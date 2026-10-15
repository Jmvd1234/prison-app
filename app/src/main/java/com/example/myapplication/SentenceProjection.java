package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.RandomAccess;

/** Recorded custody dates and first non-positive balances, using the shared calculator. */
public final class SentenceProjection {
    private SentenceProjection() { }

    public enum Kind { LEA_ARREST, JAIL_COMMITMENT, PRISON_COMMITMENT, MINIMUM_ZERO, MAXIMUM_ZERO, TODAY }

    /** Dates based on credits earned at observation, pinned once their first zero is reached. */
    public static final class ReleaseDates {
        public final LocalDate minimum, maximum;
        public final boolean minimumReached, maximumReached;

        private ReleaseDates(Range range, LocalDate observation, SentenceCalculator.Result result) {
            minimumReached = range != null && !observation.isBefore(range.minimumCompletion);
            maximumReached = range != null && !observation.isBefore(range.maximumCompletion);
            // A monthly credit can jump past zero. Do not backdate that first zero by
            // adding a negative balance to observation, or keep moving it afterward.
            minimum = minimumReached ? range.minimumCompletion : observation.plusDays(result.minRemainingDays);
            maximum = maximumReached ? range.maximumCompletion : observation.plusDays(result.maxRemainingDays);
        }
    }

    public static ReleaseDates releaseDates(Range range, LocalDate observation, SentenceCalculator.Result result) {
        if (!result.available) throw new IllegalArgumentException(result.problem);
        return new ReleaseDates(range, observation, result);
    }

    public static final class Milestone {
        public final Kind kind;
        public final LocalDate date;

        private Milestone(Kind kind, LocalDate date) {
            this.kind = kind;
            this.date = date;
        }
    }

    public static final class Range {
        public final LocalDate start, commitment, minimumCompletion, maximumCompletion, end;
        public final List<LocalDate> dates;
        public final List<Milestone> milestones;

        private Range(LocalDate start, LocalDate commitment, LocalDate minimum, LocalDate maximum,
                LocalDate end, List<Milestone> milestones) {
            this.start = start;
            this.commitment = commitment;
            this.minimumCompletion = minimum;
            this.maximumCompletion = maximum;
            this.end = end;
            this.dates = new DailyDates(start, end);
            milestones.sort(Comparator.comparing(point -> point.date));
            this.milestones = Collections.unmodifiableList(new ArrayList<>(milestones));
        }

        /** Each slider step and each equal distance on the track represents one day. */
        public float positionOf(LocalDate date) {
            if (dates.size() <= 1 || !date.isAfter(start)) return 0;
            if (!date.isBefore(end)) return 1;
            return (float) ChronoUnit.DAYS.between(start, date) / (dates.size() - 1);
        }
    }

    /** Immutable, constant-space date range: avoid allocating a date for every day of a long sentence. */
    private static final class DailyDates extends AbstractList<LocalDate> implements RandomAccess {
        private final LocalDate start;
        private final int size;

        DailyDates(LocalDate start, LocalDate end) {
            this.start = start;
            this.size = Math.toIntExact(ChronoUnit.DAYS.between(start, end) + 1);
        }

        @Override public int size() { return size; }

        @Override public LocalDate get(int index) {
            if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Day index: " + index);
            return start.plusDays(index);
        }

        @Override public int indexOf(Object value) {
            if (!(value instanceof LocalDate)) return -1;
            long index = ChronoUnit.DAYS.between(start, (LocalDate) value);
            return index >= 0 && index < size ? (int) index : -1;
        }

        @Override public int lastIndexOf(Object value) { return indexOf(value); }
        @Override public boolean contains(Object value) { return indexOf(value) >= 0; }
    }

    public static Range create(Profile profile, LocalDate today) {
        SentenceCalculator.Input input = ProfileCalculationMapper.from(profile, today);
        SentenceCalculator.Result current = SentenceCalculator.calculate(input);
        if (!current.available) throw new IllegalArgumentException(current.problem);
        LocalDate commitment = SentenceCalculator.requiredDate(
                ProfileCalculationMapper.commitmentDate(profile), "Commitment date");
        LocalDate transfer = SentenceCalculator.optionalDate(
                ProfileCalculationMapper.prisonTransferDate(profile), "Prison commitment date");
        LocalDate arrest = SentenceCalculator.optionalDate(profile.leaArrestDate, "LEA arrest date");
        LocalDate earlierJail = SentenceCalculator.optionalDate(input.jailBeforePrisonDate, "Jail commitment date");
        LocalDate start = arrest == null ? (earlierJail == null ? commitment : earlierJail) : arrest;
        if (ChronoUnit.MONTHS.between(start, commitment) > 2400) {
            throw new IllegalArgumentException("LEA custody exceeds the supported 200-year preview range.");
        }
        LocalDate allowanceStart = commitment;
        if (SentenceCalculator.usesConvictionDate(profile.notCommittedToJail, profile.disqualifiedForCPI,
                profile.disqualifiedForTimeAllowances)) {
            LocalDate conviction = SentenceCalculator.optionalDate(input.convictionDate, "Prison commitment date");
            if (conviction != null) allowanceStart = conviction;
        }

        LocalDate limit = commitment.plusYears(200);
        if (allowanceStart.isBefore(commitment)) limit = allowanceStart.plusYears(200);
        LocalDate minimum = completion(profile, start, commitment, limit, true);
        LocalDate maximum = completion(profile, start, commitment, limit, false);
        LocalDate end = maximum.isAfter(today) ? maximum : today;
        // Keep the recorded transfer visible even if the balance reaches zero while in LEA.
        if (commitment.isAfter(end)) end = commitment;
        // Earned-credit estimates start later than the eventual zero date. Reserve their
        // space once, rather than rescaling the timeline as the observation thumb moves.
        // Check both sides of commitment because TA-only conviction credits can jump there.
        LocalDate arrestEstimate = start.plusDays(checked(profile, start).maxRemainingDays);
        LocalDate commitmentEstimate = commitment.plusDays(checked(profile, commitment).maxRemainingDays);
        LocalDate latestEstimate = arrestEstimate.isAfter(commitmentEstimate) ? arrestEstimate : commitmentEstimate;
        if (latestEstimate.isAfter(limit)) latestEstimate = limit;
        if (latestEstimate.isAfter(end)) end = latestEstimate;

        List<Milestone> milestones = new ArrayList<>();
        if (arrest != null) milestones.add(new Milestone(Kind.LEA_ARREST, arrest));
        if (earlierJail != null) milestones.add(new Milestone(Kind.JAIL_COMMITMENT, earlierJail));
        milestones.add(new Milestone(SentenceCalculator.usesPrisonCommitment(
                profile.notCommittedToJail, profile.disqualifiedForCPI)
                ? Kind.PRISON_COMMITMENT : Kind.JAIL_COMMITMENT, commitment));
        if (transfer != null) milestones.add(new Milestone(Kind.PRISON_COMMITMENT, transfer));
        milestones.add(new Milestone(Kind.MINIMUM_ZERO, minimum));
        milestones.add(new Milestone(Kind.MAXIMUM_ZERO, maximum));
        milestones.add(new Milestone(Kind.TODAY, today));
        return new Range(start, commitment, minimum, maximum, end, milestones);
    }

    private static LocalDate completion(Profile profile, LocalDate start, LocalDate commitment,
            LocalDate limit, boolean minimum) {
        long initial = balance(checked(profile, start), minimum);
        if (initial <= 0) return start;
        // LEA earns calendar days only. Check it separately: the website's signed conviction
        // credits can cause a discontinuity at commitment, so do not binary-search across it.
        if (initial < ChronoUnit.DAYS.between(start, commitment)) return start.plusDays(initial);
        long committedBalance = balance(checked(profile, commitment), minimum);
        if (committedBalance <= 0) return commitment;
        long low = 0;
        long high = Math.min(committedBalance, ChronoUnit.DAYS.between(commitment, limit));
        if (high <= 0 || balance(checked(profile, commitment.plusDays(high)), minimum) > 0) {
            throw new IllegalArgumentException("Completion is beyond the supported projection range.");
        }
        while (high - low > 1) {
            long middle = low + (high - low) / 2;
            if (balance(checked(profile, commitment.plusDays(middle)), minimum) <= 0) high = middle;
            else low = middle;
        }
        return commitment.plusDays(high);
    }

    private static long balance(SentenceCalculator.Result result, boolean minimum) {
        return minimum ? result.minRemainingDays : result.maxRemainingDays;
    }

    private static SentenceCalculator.Result checked(Profile profile, LocalDate date) {
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, date));
        if (!result.available) throw new IllegalArgumentException(result.problem);
        return result;
    }
}
