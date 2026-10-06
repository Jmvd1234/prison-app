package com.example.myapplication;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Mirrors the observed calculations at https://chr.layacalculator.org/.
 * Website compatibility is intentional: CPI disables GCTA/TASTM, but the website
 * still subtracts arrest-to-commitment days from its commitment-based dates.
 * Expected values are captured separately in the website regression fixtures.
 */
public final class SentenceCalculator {
    private SentenceCalculator() { }

    public static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US)
                    .withResolverStyle(ResolverStyle.STRICT);

    public static final class Input {
        public String observationDate, leaArrestDate, commitmentDate, convictionDate;
        public int minYears, minMonths, minDays;
        public int maxYears, maxMonths, maxDays;
        public boolean notCommittedToJail, disqualifiedForCPI, disqualifiedForTimeAllowances;
        public int stalPercent;
        public long timeGapDays, deductionDays;
        public List<Penalty> charges;
    }

    public static final class Penalty {
        public final int minYears, minMonths, minDays, maxYears, maxMonths, maxDays;

        public Penalty(int minYears, int minMonths, int minDays, int maxYears, int maxMonths, int maxDays) {
            this.minYears = minYears;
            this.minMonths = minMonths;
            this.minDays = minDays;
            this.maxYears = maxYears;
            this.maxMonths = maxMonths;
            this.maxDays = maxDays;
        }
    }

    /** Gross days and allowances for one recorded stage, before global adjustments. */
    public static final class StageCredits {
        public final boolean recorded;
        public final long days, gctaDays, tastmDays;

        private StageCredits(boolean recorded, long days, long gctaDays, long tastmDays) {
            this.recorded = recorded;
            this.days = days;
            this.gctaDays = gctaDays;
            this.tastmDays = tastmDays;
        }
    }

    /**
     * The form records one commitment date, not a jail-to-prison transfer.
     * Attribute its two intervals to LEA and the currently selected commitment type.
     * An unrecorded stage is displayed as a dash rather than implying a known zero.
     */
    public static final class CustodyBreakdown {
        public final StageCredits lea, jail, prison, total;

        private CustodyBreakdown(State s) {
            lea = new StageCredits(true, s.beforeCommitment, 0, 0);
            StageCredits committed = new StageCredits(true, s.afterCommitment, s.gcta, s.tastm);
            StageCredits unrecorded = new StageCredits(false, 0, 0, 0);
            jail = s.prisonCommitment ? unrecorded : committed;
            prison = s.prisonCommitment ? committed : unrecorded;
            total = new StageCredits(true, s.beforeCommitment + s.afterCommitment, s.gcta, s.tastm);
        }
    }

    /** Immutable result; numeric values are only valid when available is true. */
    public static final class Result {
        public final boolean available;
        public final String problem;
        public final boolean effectiveTADisqualification;
        public final LocalDate sentenceAnchor;
        // These are the website's before/after commitment intervals, not a finality determination.
        public final long actualPreventiveDays, actualServiceDays, creditedPreventiveFifths;
        public final long completedImprisonmentMonths, eligibleMonths;
        public final long gctaDays, tastmDays, stalDays;
        public final int stalPercent;
        public final long appliedDeductionDays, netAllowanceDays;
        public final long minSentenceDays, maxSentenceDays;
        public final int chargeCount;
        public final long minRemainingFifths, maxRemainingFifths, minRemainingDays, maxRemainingDays;
        public final CustodyBreakdown custodyBreakdown;
        public final List<String> notes = Collections.emptyList();

        private Result(State s) {
            available = s.available;
            problem = s.problem;
            effectiveTADisqualification = s.excluded;
            sentenceAnchor = s.anchor;
            actualPreventiveDays = s.beforeCommitment;
            actualServiceDays = s.afterCommitment;
            creditedPreventiveFifths = s.beforeCommitment * 5L;
            completedImprisonmentMonths = s.months;
            eligibleMonths = s.eligible;
            gctaDays = s.gcta;
            tastmDays = s.tastm;
            stalDays = s.stal;
            stalPercent = s.stalPercent;
            appliedDeductionDays = s.deductions;
            netAllowanceDays = s.netAllowance;
            minSentenceDays = s.minSentence;
            maxSentenceDays = s.maxSentence;
            chargeCount = s.chargeCount;
            minRemainingDays = s.minRemaining;
            maxRemainingDays = s.maxRemaining;
            // Retain the existing dashboard's exact-day interface.
            minRemainingFifths = s.minRemaining * 5L;
            maxRemainingFifths = s.maxRemaining * 5L;
            custodyBreakdown = s.available ? new CustodyBreakdown(s) : null;
        }

        public String minimumText(LocalDate observation) {
            return available ? displayBalance(observation, minRemainingDays) : "N/A";
        }

        public String maximumText(LocalDate observation) {
            return available ? displayBalance(observation, maxRemainingDays) : "N/A";
        }

        public String actionText() {
            if (!available) return "Check inputs: " + problem;
            if (maxRemainingDays <= 0) return "Start Release Process";
            if (minRemainingDays <= 0) return "Start ATI Process";
            return "";
        }
    }

    private static final class State {
        boolean available, excluded, prisonCommitment;
        String problem = "";
        LocalDate anchor;
        int chargeCount, stalPercent;
        long beforeCommitment, afterCommitment, months, eligible, gcta, tastm, stal;
        long deductions, netAllowance, minSentence, maxSentence, minRemaining, maxRemaining;
    }

    public static Result calculate(Input in) {
        return calculate(in, false);
    }

    /** Extends the timeline back to arrest without crediting future custody or allowances. */
    public static Result calculatePreview(Input in) {
        return calculate(in, true);
    }

    private static Result calculate(Input in, boolean preview) {
        State s = new State();
        if (in == null) {
            s.problem = "No calculation inputs supplied.";
            return new Result(s);
        }
        s.excluded = in.disqualifiedForCPI || in.disqualifiedForTimeAllowances;
        s.prisonCommitment = usesPrisonCommitment(in.notCommittedToJail, in.disqualifiedForCPI);
        try {
            LocalDate observation = requiredDate(in.observationDate, "Observation date");
            LocalDate commitment = requiredDate(in.commitmentDate, "Commitment date");
            LocalDate arrest = optionalDate(in.leaArrestDate, "LEA arrest date");
            if (arrest == null) arrest = commitment; // The website permits a blank LEA date.
            LocalDate conviction = usesConvictionDate(in.disqualifiedForCPI,
                    in.disqualifiedForTimeAllowances)
                    ? optionalDate(in.convictionDate, "Date of conviction") : null;
            require(!commitment.isBefore(arrest), "Commitment cannot precede arrest.");
            require(!observation.isBefore(preview ? arrest : commitment), preview
                    ? "Preview cannot precede arrest." : "Observation cannot precede commitment.");
            require(in.timeGapDays >= 0 && in.deductionDays >= 0,
                    "Gap and deduction days must be non-negative.");
            require(in.stalPercent == 0 || in.stalPercent == 20 || in.stalPercent == 40,
                    "Select None, 20%, or 40% STAL.");

            s.anchor = commitment;
            boolean beforeCommitment = observation.isBefore(commitment);
            s.beforeCommitment = ChronoUnit.DAYS.between(arrest, beforeCommitment ? observation : commitment);
            s.afterCommitment = beforeCommitment ? 0 : ChronoUnit.DAYS.between(commitment, observation);
            s.months = wholeMonthsElapsed(commitment, observation);
            require(s.months <= 2400, "Check the custody dates: more than 200 years entered.");
            List<Penalty> charges = in.charges == null ? Collections.singletonList(new Penalty(
                    in.minYears, in.minMonths, in.minDays, in.maxYears, in.maxMonths, in.maxDays)) : in.charges;
            require(!charges.isEmpty(), "Enter at least one charge.");
            s.chargeCount = charges.size();
            for (int index = 0; index < charges.size(); index++) {
                Penalty charge = charges.get(index);
                require(charge != null, "Charge " + (index + 1) + " is missing.");
                try {
                    long minimum = sentenceDays(commitment, charge.minYears, charge.minMonths, charge.minDays);
                    long maximum = sentenceDays(commitment, charge.maxYears, charge.maxMonths, charge.maxDays);
                    require(minimum <= maximum, "Minimum penalty exceeds maximum penalty.");
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("Charge " + (index + 1) + ": " + error.getMessage());
                }
            }
            s.minSentence = combinedSentenceDays(commitment, charges, true);
            s.maxSentence = combinedSentenceDays(commitment, charges, false);

            if (beforeCommitment || in.disqualifiedForCPI) {
                s.eligible = 0;
            } else if (in.disqualifiedForTimeAllowances) {
                // The website restarts both the month count and the rate tiers at conviction.
                s.eligible = conviction == null ? 0 : signedWholeMonths(conviction, observation);
            } else {
                s.eligible = s.months;
            }
            require(Math.abs(s.eligible) <= 2400, "Check the conviction date.");
            s.gcta = gctaForMonths(s.eligible);
            s.tastm = Math.multiplyExact(s.eligible, 15L);
            s.stalPercent = in.stalPercent;
            s.stal = loyaltyCreditDays(s.maxSentence, s.stalPercent);
            s.deductions = in.deductionDays;
            long grossAllowance = Math.addExact(Math.addExact(s.gcta, s.tastm), s.stal);
            s.netAllowance = Math.subtractExact(grossAllowance, s.deductions);
            long creditedDays = Math.addExact(Math.addExact(s.beforeCommitment,
                    s.afterCommitment), s.netAllowance);
            // Both website adjustments extend the balance; neither is clamped to credits.
            creditedDays = Math.subtractExact(creditedDays, in.timeGapDays);
            s.minRemaining = Math.subtractExact(s.minSentence, creditedDays);
            s.maxRemaining = Math.subtractExact(s.maxSentence, creditedDays);
            // Reject overflow or dates the dashboard cannot represent.
            Math.multiplyExact(s.minRemaining, 5L);
            Math.multiplyExact(s.maxRemaining, 5L);
            observation.plusDays(s.minRemaining);
            observation.plusDays(s.maxRemaining);
            s.available = true;
        } catch (IllegalArgumentException | DateTimeException | ArithmeticException error) {
            s.problem = error.getMessage() == null ? "Check calculation inputs." : error.getMessage();
        }
        return new Result(s);
    }

    private static long gctaForMonths(long months) {
        // Signed values intentionally reproduce the site's handling of a future conviction.
        if (months <= 24) return Math.multiplyExact(months, 20L);
        long total = 24 * 20L + Math.min(months - 24, 36) * 23L;
        if (months > 60) total += Math.min(months - 60, 60) * 25L;
        if (months > 120) total += (months - 120) * 30L;
        return total;
    }

    /**
     * Article 98 (RA 10592, section 4) bases STAL on the sentence period, not the
     * remaining balance after custody/GCTA/TASTM. LAYA uses the full maximum term,
     * rounds up to a whole day, and subtracts that same fixed credit from both
     * balances. For multiple charges, the maximum is already capped by Article 70.
     * The selected award stays fixed during date previews; no award date is stored.
     * Source: https://lawphil.net/statutes/repacts/ra2013/ra_10592_2013.html
     */
    private static long loyaltyCreditDays(long maximumSentenceDays, int percent) {
        return Math.floorDiv(Math.addExact(Math.multiplyExact(maximumSentenceDays,
                (long) percent), 99L), 100L);
    }

    public static int gctaRate(long month) {
        require(month > 0, "Imprisonment months are numbered from 1.");
        return month <= 24 ? 20 : month <= 60 ? 23 : month <= 120 ? 25 : 30;
    }

    public static boolean usesPrisonCommitment(boolean notCommittedToJail, boolean disqualifiedForCPI) {
        return notCommittedToJail || disqualifiedForCPI;
    }

    public static boolean usesConvictionDate(boolean disqualifiedForCPI, boolean disqualifiedForTimeAllowances) {
        return disqualifiedForTimeAllowances && !disqualifiedForCPI;
    }

    public static LocalDate requiredDate(String value, String label) {
        LocalDate date = optionalDate(value, label);
        if (date == null) throw new IllegalArgumentException(label + " is required.");
        return date;
    }

    public static LocalDate optionalDate(String value, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value.trim(), DISPLAY_DATE);
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException(label + " must be a valid date.");
            }
        }
    }

    public static long wholeMonthsElapsed(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) return 0;
        long months = (end.getYear() - start.getYear()) * 12L
                + end.getMonthValue() - start.getMonthValue();
        if (start.plusMonths(months).isAfter(end)) months--;
        return Math.max(0, months);
    }

    private static long signedWholeMonths(LocalDate start, LocalDate end) {
        return end.isBefore(start) ? -wholeMonthsElapsed(end, start) : wholeMonthsElapsed(start, end);
    }

    private static long sentenceDays(LocalDate anchor, int years, int months, int days) {
        require(years >= 0 && months >= 0 && days >= 0, "Penalty components cannot be negative.");
        require(years != 0 || months != 0 || days != 0, "Enter a non-zero penalty period.");
        // Add years and months together, matching the website's calendar-date arithmetic.
        return ChronoUnit.DAYS.between(anchor,
                anchor.plusMonths(Math.addExact(Math.multiplyExact((long) years, 12L), months)).plusDays(days));
    }

    /**
     * Article 70 cap for successive numeric imprisonment terms. Each term uses the
     * existing calendar-day conversion at the commitment anchor; sums and caps use days.
     * Minimum/maximum are range estimates, not a determination of parole eligibility.
     * Life/perpetual penalties and concurrent service are not represented by this form.
     * Source: https://lawphil.net/judjuris/juri2022/aug2022/gr_241986_2022.html
     */
    private static long combinedSentenceDays(LocalDate anchor, List<Penalty> charges, boolean minimum) {
        long total = 0, longest = 0;
        for (Penalty charge : charges) {
            long length = minimum
                    ? sentenceDays(anchor, charge.minYears, charge.minMonths, charge.minDays)
                    : sentenceDays(anchor, charge.maxYears, charge.maxMonths, charge.maxDays);
            total = Math.addExact(total, length);
            longest = Math.max(longest, length);
        }
        if (charges.size() == 1) return total;
        long fortyYears = ChronoUnit.DAYS.between(anchor, anchor.plusYears(40));
        return Math.min(total, Math.min(Math.multiplyExact(longest, 3L), fortyYears));
    }
    public static long ceilFifths(long fifths) {
        long quotient = Math.floorDiv(fifths, 5L);
        return Math.floorMod(fifths, 5L) == 0 ? quotient : quotient + 1;
    }

    public static String exactDays(long fifths) {
        return java.math.BigDecimal.valueOf(fifths)
                .divide(java.math.BigDecimal.valueOf(5L)).stripTrailingZeros().toPlainString();
    }

    private static String displayBalance(LocalDate observation, long days) {
        LocalDate equivalent = observation.plusDays(days);
        Period period = days < 0 ? Period.between(equivalent, observation) : Period.between(observation, equivalent);
        return (days < 0 ? "-" : "") + period.getYears() + "y, "
                + period.getMonths() + "m, " + period.getDays() + "d";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
