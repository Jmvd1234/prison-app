package com.example.myapplication;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A selected allowance period. Amounts are derived, never saved as stale day totals. */
public final class DeductionMonth {
    public final int year, month;
    public final boolean gcta, tastm;

    public DeductionMonth(int year, int month, boolean gcta, boolean tastm) {
        if (year < 1 || year > 9999) throw new IllegalArgumentException("Enter a year from 1 to 9999.");
        YearMonth.of(year, month);
        this.year = year; this.month = month; this.gcta = gcta; this.tastm = tastm;
    }

    public YearMonth date() { return YearMonth.of(year, month); }

    /** The same clock that earns the calculator's GCTA/TASTM; a transfer never resets it. */
    public static LocalDate allowanceStart(SentenceCalculator.Input input) {
        if (input.notCommittedToJail) return SentenceCalculator.requiredDate(input.commitmentDate, "Prison commitment date");
        if (input.disqualifiedForCPI) return null;
        return SentenceCalculator.usesConvictionDate(input.notCommittedToJail, input.disqualifiedForCPI, input.disqualifiedForTimeAllowances)
                ? SentenceCalculator.optionalDate(input.convictionDate, "Date of conviction")
                : SentenceCalculator.requiredDate(input.commitmentDate, "Commitment date");
    }

    public static final class Details {
        public final long position;
        public final int tier, gctaDays, tastmDays;
        public final LocalDate earnedOn;
        Details(long position, LocalDate earnedOn) {
            this.position = position;
            this.earnedOn = earnedOn;
            tier = earnedOn == null ? 0 : position <= 24 ? 1 : position <= 60 ? 2 : position <= 120 ? 3 : 4;
            gctaDays = earnedOn == null ? 0 : SentenceCalculator.gctaRate(position);
            tastmDays = earnedOn == null ? 0 : 15;
        }
        public long selected(DeductionMonth row) { return (row.gcta ? gctaDays : 0) + (row.tastm ? tastmDays : 0); }
    }

    public Details details(SentenceCalculator.Input input) {
        LocalDate start = allowanceStart(input);
        if (start == null) return new Details(0, null);
        long position = ChronoUnit.MONTHS.between(YearMonth.from(start), date()) + 1;
        if (position < 1 || position > 2400) return new Details(position, null);
        // LAYA labels the commitment month as Month 1. Its first full month's credit
        // arrives on the next anniversary, not on the first day of the labelled month.
        return new Details(position, start.plusMonths(position));
    }

    public static final class Totals {
        public long selected, appliedGcta, appliedTastm;
        public long appliedJailGcta, appliedJailTastm;
        public long applied() { return appliedGcta + appliedTastm; }
    }

    public static Totals totals(SentenceCalculator.Input input, LocalDate observation) {
        Totals out = new Totals();
        List<DeductionMonth> rows = input.deductionMonths;
        if (rows == null) return out;
        if (rows.size() > 2400) throw new IllegalArgumentException("At most 2400 deduction months are supported.");
        LocalDate commitment = SentenceCalculator.requiredDate(input.commitmentDate, "Commitment date");
        boolean directPrison = SentenceCalculator.usesPrisonCommitment(
                input.notCommittedToJail, input.disqualifiedForCPI);
        LocalDate transfer = directPrison ? null
                : SentenceCalculator.optionalDate(input.prisonTransferDate, "Prison commitment date");
        Set<YearMonth> seen = new HashSet<>();
        for (DeductionMonth row : rows) {
            if (row == null || !seen.add(row.date())) throw new IllegalArgumentException("Duplicate deduction months are not allowed.");
            Details details = row.details(input);
            out.selected += details.selected(row);
            if (details.earnedOn == null || observation.isBefore(details.earnedOn) || observation.isBefore(commitment)) continue;
            if (row.gcta) out.appliedGcta += details.gctaDays;
            if (row.tastm) out.appliedTastm += details.tastmDays;
            // Match the gross custody split: credit earned ON the transfer date belongs to Jail.
            if (!directPrison && (transfer == null || !details.earnedOn.isAfter(transfer))) {
                if (row.gcta) out.appliedJailGcta += details.gctaDays;
                if (row.tastm) out.appliedJailTastm += details.tastmDays;
            }
        }
        return out;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof DeductionMonth)) return false;
        DeductionMonth row = (DeductionMonth) other;
        return year == row.year && month == row.month && gcta == row.gcta && tastm == row.tastm;
    }
    @Override public int hashCode() { return Objects.hash(year, month, gcta, tastm); }
}
