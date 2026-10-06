package com.example.myapplication;

import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import org.junit.Test;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class MultipleChargesCalculationTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.commitmentDate = "2020-01-01";
        in.leaArrestDate = "2019-12-01";
        in.observationDate = "2021-01-01";
        return in;
    }

    private SentenceCalculator.Penalty days(int minimum, int maximum) {
        return new SentenceCalculator.Penalty(0, 0, minimum, 0, 0, maximum);
    }

    private SentenceCalculator.Result calculate(SentenceCalculator.Input in) {
        SentenceCalculator.Result result = SentenceCalculator.calculate(in);
        assertTrue(result.problem, result.available);
        return result;
    }

    @Test public void totalsWinWhenBelowBothCaps() {
        SentenceCalculator.Input in = input();
        in.charges = Arrays.asList(days(100, 200), days(50, 70));
        SentenceCalculator.Result result = calculate(in);
        assertEquals(150, result.minSentenceDays);
        assertEquals(270, result.maxSentenceDays);
        assertEquals(2, result.chargeCount);
    }

    @Test public void threefoldWinsIndependentlyForEachRangeEndpoint() {
        SentenceCalculator.Input in = input();
        in.charges = Arrays.asList(days(100, 200), days(100, 200), days(100, 200), days(100, 200));
        SentenceCalculator.Result result = calculate(in);
        assertEquals(300, result.minSentenceDays);
        assertEquals(600, result.maxSentenceDays);
    }

    @Test public void fortyYearLimitWinsWhenBothTotalsAndThreefoldExceedIt() {
        SentenceCalculator.Input in = input();
        in.charges = Arrays.asList(days(8000, 9000), days(8000, 9000));
        SentenceCalculator.Result result = calculate(in);
        long cap = ChronoUnit.DAYS.between(LocalDate.of(2020, 1, 1), LocalDate.of(2060, 1, 1));
        assertEquals(cap, result.minSentenceDays);
        assertEquals(cap, result.maxSentenceDays);
    }

    @Test public void longestChargeIsNotAssumedToBeFirstAndOrderDoesNotMatter() {
        SentenceCalculator.Input in = input();
        in.charges = new ArrayList<>(Arrays.asList(days(100, 110), days(300, 500), days(200, 400),
                days(250, 450), days(270, 400)));
        SentenceCalculator.Result first = calculate(in);
        Collections.reverse(in.charges);
        SentenceCalculator.Result reversed = calculate(in);
        assertEquals(900, first.minSentenceDays);
        assertEquals(1500, first.maxSentenceDays);
        assertEquals(first.minSentenceDays, reversed.minSentenceDays);
        assertEquals(first.maxSentenceDays, reversed.maxSentenceDays);
    }

    @Test public void calendarTermsUseExistingPerChargeConversionIncludingLeapDays() {
        SentenceCalculator.Input in = input();
        SentenceCalculator.Penalty year = new SentenceCalculator.Penalty(1, 0, 0, 1, 0, 0);
        in.charges = Arrays.asList(year, year, year, year);
        assertEquals(366 * 3, calculate(in).maxSentenceDays);
        in.minYears = in.maxYears = 1;
        in.charges = Collections.singletonList(year);
        SentenceCalculator.Result single = calculate(in);
        in.charges = null;
        SentenceCalculator.Result legacy = calculate(in);
        assertEquals(legacy.minRemainingDays, single.minRemainingDays);
        assertEquals(legacy.maxRemainingDays, single.maxRemainingDays);
    }

    @Test public void creditsAndAdjustmentsAreAppliedOnceAfterTheCapInEveryTriggerMode() {
        for (int flags = 0; flags < 8; flags++) {
            SentenceCalculator.Input in = input();
            in.notCommittedToJail = (flags & 1) != 0;
            in.disqualifiedForCPI = (flags & 2) != 0;
            in.disqualifiedForTimeAllowances = (flags & 4) != 0;
            in.convictionDate = "2020-06-01";
            in.charges = Arrays.asList(days(100, 200), days(100, 200), days(100, 200), days(100, 200));
            in.stalPercent = 20;
            in.deductionDays = 13;
            in.timeGapDays = 7;
            SentenceCalculator.Result multiple = calculate(in);
            in.charges = Collections.singletonList(days(300, 600));
            SentenceCalculator.Result equivalentSingle = calculate(in);
            assertEquals(equivalentSingle.minRemainingDays, multiple.minRemainingDays);
            assertEquals(equivalentSingle.maxRemainingDays, multiple.maxRemainingDays);
            assertEquals(equivalentSingle.gctaDays, multiple.gctaDays);
            assertEquals(equivalentSingle.tastmDays, multiple.tastmDays);
            assertEquals(120, multiple.stalDays);
            assertEquals(397, multiple.custodyBreakdown.total.days);
        }
    }

    @Test public void everyChargeMustBeValidBeforeAnyCapCanHideIt() {
        SentenceCalculator.Input in = input();
        for (SentenceCalculator.Penalty invalid : Arrays.asList(days(0, 10), days(20, 10), days(-1, 10))) {
            in.charges = Arrays.asList(days(100, 200), invalid);
            SentenceCalculator.Result result = SentenceCalculator.calculate(in);
            assertFalse(result.available);
            assertTrue(result.problem.contains("Charge 2"));
        }
        in.charges = Collections.emptyList();
        assertFalse(SentenceCalculator.calculate(in).available);
    }

    @Test public void projectionAndMapperUseAllChargesAndRetainBothZeroStops() {
        Profile profile = new Profile();
        profile.commitmentDate = "2020-01-01";
        profile.leaArrestDate = "2019-12-01";
        for (int i = 0; i < 4; i++) {
            ProfileCharge charge = new ProfileCharge();
            charge.minDays = 100;
            charge.maxDays = 200;
            profile.charges.add(charge);
        }
        LocalDate today = LocalDate.of(2020, 1, 1);
        SentenceProjection.Range range = SentenceProjection.create(profile, today);
        SentenceCalculator.Result minimum = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, range.minimumCompletion));
        SentenceCalculator.Result maximum = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, range.maximumCompletion));
        assertEquals(300, minimum.minSentenceDays);
        assertEquals(600, maximum.maxSentenceDays);
        assertTrue(minimum.minRemainingDays <= 0);
        assertTrue(maximum.maxRemainingDays <= 0);
        assertTrue(SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile,
                range.minimumCompletion.minusDays(1))).minRemainingDays > 0);
        assertTrue(SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile,
                range.maximumCompletion.minusDays(1))).maxRemainingDays > 0);
        assertEquals(4, profile.charges.size());
    }
}
