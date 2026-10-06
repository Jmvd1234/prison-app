package com.example.myapplication;

import com.example.myapplication.db.Profile;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class SentenceCalculatorTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.observationDate = "2026-09-29";
        in.leaArrestDate = "2024-06-04";
        in.commitmentDate = "2025-02-06";
        in.minYears = 1; in.maxYears = 3;
        return in;
    }

    private SentenceCalculator.Result available(SentenceCalculator.Input in) {
        SentenceCalculator.Result r = SentenceCalculator.calculate(in);
        assertTrue(r.problem, r.available);
        return r;
    }

    @Test public void forcedTaDoesNotChangeTheWebsiteCpiBranch() {
        SentenceCalculator.Input in = input();
        in.disqualifiedForCPI = true;
        SentenceCalculator.Result first = available(in);
        in.disqualifiedForTimeAllowances = true;
        SentenceCalculator.Result second = available(in);
        assertEquals(-482, second.minRemainingDays);
        assertEquals(248, second.maxRemainingDays);
        assertEquals(first.maxRemainingDays, second.maxRemainingDays);
        assertEquals(0, second.gctaDays);
        assertEquals(0, second.tastmDays);
        assertTrue(second.effectiveTADisqualification);
    }

    @Test public void hiddenConvictionDoesNotAffectOtherModes() {
        for (boolean cpi : new boolean[]{false, true}) {
            SentenceCalculator.Input in = input();
            in.disqualifiedForCPI = cpi;
            SentenceCalculator.Result before = available(in);
            in.convictionDate = "invalid hidden date";
            assertEquals(before.maxRemainingDays, available(in).maxRemainingDays);
        }
    }

    @Test public void allTriggerStatesKeepTheRequestedUiContract() {
        for (int bits = 0; bits < 8; bits++) {
            SentenceCalculator.Input in = input();
            in.notCommittedToJail = (bits & 1) != 0;
            in.disqualifiedForCPI = (bits & 2) != 0;
            in.disqualifiedForTimeAllowances = (bits & 4) != 0;
            available(in);
            assertEquals(in.notCommittedToJail || in.disqualifiedForCPI,
                    SentenceCalculator.usesPrisonCommitment(in.notCommittedToJail, in.disqualifiedForCPI));
            assertEquals(in.disqualifiedForTimeAllowances && !in.disqualifiedForCPI,
                    SentenceCalculator.usesConvictionDate(in.disqualifiedForCPI, in.disqualifiedForTimeAllowances));
        }
    }

    @Test public void legacyMetadataCannotChangeVisibleWebsiteInputs() {
        Profile p = new Profile();
        p.observationDate = "Sep 29, 2026"; p.leaArrestDate = "Jun 4, 2024";
        p.commitmentDate = "Feb 6, 2025"; p.minYears = 1; p.maxYears = 3;
        p.disqualifiedForCPI = true;
        p.convictionDate = "invalid hidden";
        p.imprisonmentStartDate = "invalid legacy";
        p.sentenceServiceStartDate = "invalid legacy";
        p.custodyTimelineConfirmed = false;
        p.cpiCreditPercent = 80;
        SentenceCalculator.Input mapped = ProfileCalculationMapper.from(p, LocalDate.of(2026, 9, 29));
        assertNull(mapped.convictionDate);
        assertTrue(mapped.disqualifiedForTimeAllowances);
        assertEquals(248, available(mapped).maxRemainingDays);
        p.disqualifiedForCPI = false;
        long withOldRate = available(ProfileCalculationMapper.from(p)).maxRemainingDays;
        p.cpiCreditPercent = 0;
        assertEquals(withOldRate, available(ProfileCalculationMapper.from(p)).maxRemainingDays);
    }

    @Test public void mapperPassesStalEvenWhenCpiIsSelected() {
        Profile p = new Profile();
        p.stalPercent = 40; p.disqualifiedForCPI = true;
        assertEquals(40, ProfileCalculationMapper.from(p).stalPercent);
    }

    @Test public void mapperUsesVisibleDateBeforeLegacyFallback() {
        Profile p = new Profile();
        p.jailCommitmentDate = "2020-01-01"; p.prisonCommitmentDate = "2020-06-01";
        assertEquals(p.jailCommitmentDate, ProfileCalculationMapper.commitmentDate(p));
        p.disqualifiedForCPI = true;
        assertEquals(p.prisonCommitmentDate, ProfileCalculationMapper.commitmentDate(p));
        p.commitmentDate = "2021-02-01";
        assertEquals(p.commitmentDate, ProfileCalculationMapper.commitmentDate(p));
    }

    @Test public void emptyArrestUsesCommitmentAndStillCalculates() {
        SentenceCalculator.Input in = input();
        in.leaArrestDate = "";
        in.disqualifiedForCPI = true;
        SentenceCalculator.Result r = available(in);
        assertEquals(0, r.actualPreventiveDays);
        assertEquals(-235, r.minRemainingDays);
        assertEquals(495, r.maxRemainingDays);
    }

    @Test public void noRepeatedDisclaimersAndShortWebsiteActions() {
        SentenceCalculator.Input in = input();
        in.disqualifiedForCPI = true;
        assertTrue(available(in).notes.isEmpty());
        assertEquals("Start ATI Process", available(in).actionText());
        in.disqualifiedForCPI = false;
        assertEquals("Start Release Process", available(in).actionText());
        in.minYears = 20; in.maxYears = 30;
        assertEquals("", available(in).actionText());
    }

    @Test public void invalidInputsFailWithoutCrashing() {
        SentenceCalculator.Input in = input(); in.commitmentDate = "";
        assertFalse(SentenceCalculator.calculate(in).available);
        in = input(); in.observationDate = "2024-01-01";
        assertFalse(SentenceCalculator.calculate(in).available);
        in = input(); in.minYears = 4;
        assertFalse(SentenceCalculator.calculate(in).available);
        in = input(); in.maxYears = Integer.MAX_VALUE;
        assertFalse(SentenceCalculator.calculate(in).available);
        in = input(); in.deductionDays = -1;
        assertFalse(SentenceCalculator.calculate(in).available);
        in = input(); in.disqualifiedForTimeAllowances = true; in.convictionDate = "2026-02-30";
        assertFalse(SentenceCalculator.calculate(in).available);
        in = input(); in.timeGapDays = Long.MAX_VALUE;
        assertFalse(SentenceCalculator.calculate(in).available);
    }

    @Test public void wholeMonthAndTierBoundariesRemainStable() {
        assertEquals(1, SentenceCalculator.wholeMonthsElapsed(
                LocalDate.of(2016, 1, 31), LocalDate.of(2016, 2, 29)));
        assertEquals(1, SentenceCalculator.wholeMonthsElapsed(
                LocalDate.of(2016, 1, 31), LocalDate.of(2016, 3, 30)));
        long[] months = {1, 24, 25, 60, 61, 120, 121};
        int[] rates = {20, 20, 23, 23, 25, 25, 30};
        for (int i = 0; i < months.length; i++) assertEquals(rates[i], SentenceCalculator.gctaRate(months[i]));
    }
}
