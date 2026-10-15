package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.util.ArrayList;
import com.example.myapplication.db.ProfileCharge;

/** Maps website-style inputs and an explicit custody transfer; ignores retired metadata. */
public final class ProfileCalculationMapper {
    private ProfileCalculationMapper() { }

    public static SentenceCalculator.Input from(Profile p) {
        return from(p, LocalDate.now());
    }

    /** A calculation date is a view choice, never a mutation of the saved profile. */
    public static SentenceCalculator.Input from(Profile p, LocalDate observation) {
        SentenceCalculator.Input input = new SentenceCalculator.Input();
        input.observationDate = observation.toString();
        input.leaArrestDate = p.leaArrestDate;
        input.commitmentDate = commitmentDate(p);
        input.prisonTransferDate = prisonTransferDate(p);
        input.jailBeforePrisonDate = jailBeforePrisonDate(p);
        input.allowanceStartIsPrison = SentenceCalculator.usesConvictionDate(
                p.notCommittedToJail, p.disqualifiedForCPI, p.disqualifiedForTimeAllowances);
        input.convictionDate = SentenceCalculator.usesConvictionDate(
                p.notCommittedToJail, p.disqualifiedForCPI, p.disqualifiedForTimeAllowances) ? prisonInputDate(p) : null;
        input.minYears = p.minYears;
        input.minMonths = p.minMonths;
        input.minDays = p.minDays;
        input.maxYears = p.maxYears;
        input.maxMonths = p.maxMonths;
        input.maxDays = p.maxDays;
        if (p.charges != null && !p.charges.isEmpty()) {
            input.charges = new ArrayList<>();
            for (ProfileCharge charge : p.charges) input.charges.add(charge == null ? null : charge.toPenalty());
        }
        input.notCommittedToJail = p.notCommittedToJail;
        input.disqualifiedForCPI = p.disqualifiedForCPI;
        input.disqualifiedForTimeAllowances = p.disqualifiedForCPI || p.disqualifiedForTimeAllowances;
        input.timeGapDays = p.timeGapDays;
        input.deductionDays = p.deductionDays;
        input.deductionMonths = p.deductionMonths == null ? null : new ArrayList<>(p.deductionMonths);
        input.stalPercent = p.stalPercent;
        // Retired CPI-rate/timeline/service fields do not exist on the website form.
        return input;
    }

    public static String commitmentDate(Profile p) {
        if (p.commitmentDate != null && !p.commitmentDate.trim().isEmpty()) return p.commitmentDate;
        String stored = SentenceCalculator.usesPrisonCommitment(p.notCommittedToJail, p.disqualifiedForCPI)
                ? p.prisonCommitmentDate : p.jailCommitmentDate;
        return stored == null ? "" : stored;
    }

    public static String jailInputDate(Profile p) {
        if (!SentenceCalculator.usesPrisonCommitment(p.notCommittedToJail, p.disqualifiedForCPI)) {
            return commitmentDate(p);
        }
        // Old switch drafts were not separate recorded stages.
        return p.separateCustodyDates && p.jailCommitmentDate != null ? p.jailCommitmentDate : "";
    }

    public static String prisonInputDate(Profile p) {
        if (SentenceCalculator.usesPrisonCommitment(p.notCommittedToJail, p.disqualifiedForCPI)) {
            return commitmentDate(p);
        }
        return p.prisonTransferDate == null ? "" : p.prisonTransferDate;
    }

    public static String jailBeforePrisonDate(Profile p) {
        return p.separateCustodyDates && p.disqualifiedForCPI && !p.notCommittedToJail
                ? p.jailCommitmentDate : null;
    }

    public static String prisonTransferDate(Profile p) {
        // A direct prison commitment already supplies the prison entry date.
        return SentenceCalculator.usesPrisonCommitment(p.notCommittedToJail, p.disqualifiedForCPI)
                ? null : p.prisonTransferDate;
    }
}
