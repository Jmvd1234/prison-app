package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Recorded custody dates and first non-positive balances, using the shared calculator. */
public final class SentenceProjection {
    private SentenceProjection() { }

    public enum Kind { LEA_ARREST, JAIL_COMMITMENT, PRISON_COMMITMENT, MINIMUM_ZERO, MAXIMUM_ZERO }

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
                LocalDate end, TreeSet<LocalDate> dates, List<Milestone> milestones) {
            this.start = start;
            this.commitment = commitment;
            this.minimumCompletion = minimum;
            this.maximumCompletion = maximum;
            this.end = end;
            this.dates = Collections.unmodifiableList(new ArrayList<>(dates));
            milestones.sort(Comparator.comparing(point -> point.date));
            this.milestones = Collections.unmodifiableList(new ArrayList<>(milestones));
        }
    }

    public static Range create(Profile profile, LocalDate today) {
        return create(profile, today, null);
    }

    public static Range create(Profile profile, LocalDate today, LocalDate restoredPreview) {
        checked(profile, today);
        LocalDate commitment = SentenceCalculator.requiredDate(
                ProfileCalculationMapper.commitmentDate(profile), "Commitment date");
        LocalDate arrest = SentenceCalculator.optionalDate(profile.leaArrestDate, "LEA arrest date");
        LocalDate start = arrest == null ? commitment : arrest;
        if (ChronoUnit.MONTHS.between(start, commitment) > 2400) {
            throw new IllegalArgumentException("LEA custody exceeds the supported 200-year preview range.");
        }
        LocalDate allowanceStart = commitment;
        if (SentenceCalculator.usesConvictionDate(profile.disqualifiedForCPI,
                profile.disqualifiedForTimeAllowances)) {
            LocalDate conviction = SentenceCalculator.optionalDate(profile.convictionDate, "Date of conviction");
            if (conviction != null) allowanceStart = conviction;
        }

        LocalDate limit = commitment.plusYears(200);
        if (allowanceStart.isBefore(commitment)) limit = allowanceStart.plusYears(200);
        LocalDate minimum = completion(profile, start, commitment, limit, true);
        LocalDate maximum = completion(profile, start, commitment, limit, false);
        LocalDate end = maximum.isAfter(today) ? maximum : today;
        // Keep the recorded transfer visible even if the balance reaches zero while in LEA.
        if (commitment.isAfter(end)) end = commitment;
        TreeSet<LocalDate> dates = new TreeSet<>();
        dates.add(start);
        dates.add(commitment);
        dates.add(today);
        dates.add(minimum);
        dates.add(maximum);
        dates.add(end);
        if (restoredPreview != null && !restoredPreview.isBefore(start) && !restoredPreview.isAfter(end)) {
            dates.add(restoredPreview);
        }

        // Monthly LEA previews, then the existing allowance clock after commitment.
        for (long month = 1; start.plusMonths(month).isBefore(commitment); month++) {
            dates.add(start.plusMonths(month));
        }
        long month = (commitment.getYear() - allowanceStart.getYear()) * 12L
                + commitment.getMonthValue() - allowanceStart.getMonthValue();
        LocalDate point = allowanceStart.plusMonths(month);
        if (point.isBefore(commitment)) point = allowanceStart.plusMonths(++month);
        while (!point.isAfter(end)) {
            dates.add(point);
            point = allowanceStart.plusMonths(++month);
        }

        List<Milestone> milestones = new ArrayList<>();
        if (arrest != null) milestones.add(new Milestone(Kind.LEA_ARREST, arrest));
        milestones.add(new Milestone(SentenceCalculator.usesPrisonCommitment(
                profile.notCommittedToJail, profile.disqualifiedForCPI)
                ? Kind.PRISON_COMMITMENT : Kind.JAIL_COMMITMENT, commitment));
        milestones.add(new Milestone(Kind.MINIMUM_ZERO, minimum));
        milestones.add(new Milestone(Kind.MAXIMUM_ZERO, maximum));
        return new Range(start, commitment, minimum, maximum, end, dates, milestones);
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
