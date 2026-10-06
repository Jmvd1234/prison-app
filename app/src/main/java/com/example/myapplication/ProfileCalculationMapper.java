package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.util.ArrayList;
import com.example.myapplication.db.ProfileCharge;

/** Maps exactly the website-style inputs; retired metadata cannot gate calculations. */
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
        input.convictionDate = SentenceCalculator.usesConvictionDate(
                p.disqualifiedForCPI, p.disqualifiedForTimeAllowances) ? p.convictionDate : null;
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
}
