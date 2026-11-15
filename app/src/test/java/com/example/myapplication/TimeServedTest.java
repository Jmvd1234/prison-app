package com.example.myapplication;

import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimeServedTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input i = new SentenceCalculator.Input();
        i.leaArrestDate = "2018-01-10"; i.commitmentDate = "2018-06-20";
        i.observationDate = "2020-02-20"; i.minYears = 3; i.maxYears = 5;
        return i;
    }
    private SentenceCalculator.Result calculate(SentenceCalculator.Input i) {
        SentenceCalculator.Result r = SentenceCalculator.calculatePreview(i);
        assertTrue(r.problem, r.available);
        return r;
    }
    @Test public void includesArrestJailAndPrisonAndUsesCalendarDurationFromArrest() {
        SentenceCalculator.Input i = input(); i.prisonTransferDate = "2019-06-20";
        SentenceCalculator.Result r = calculate(i);
        assertEquals(771, r.actualTimeServedDays);
        assertEquals(1471, r.timeServedWithAllowancesDays);
        assertEquals("2y, 1m, 10d", r.actualTimeServedText());
        assertEquals("4y, 0m, 10d", r.timeServedWithAllowancesText());
    }
    @Test public void gapsAndAllowanceDeductionsAreAppliedOnceIncludingStal() {
        SentenceCalculator.Input i = input(); i.timeGapDays = 7; i.deductionDays = 13; i.stalPercent = 20;
        SentenceCalculator.Result r = calculate(i);
        assertEquals(764, r.actualTimeServedDays);
        assertEquals(1817, r.timeServedWithAllowancesDays); // 764 + 400 GCTA + 300 TASTM + 366 STAL - 13.
        assertEquals(r.minSentenceDays - r.minRemainingDays, r.timeServedWithAllowancesDays);
        assertEquals(r.maxSentenceDays - r.maxRemainingDays, r.timeServedWithAllowancesDays);
    }
    @Test public void monthlyWithholdingAndDisqualificationChangeOnlyCreditedTotal() {
        SentenceCalculator.Input i = input();
        i.deductionMonths = java.util.Collections.singletonList(new DeductionMonth(2019, 12, true, true));
        SentenceCalculator.Result r = calculate(i);
        assertEquals(771, r.actualTimeServedDays); assertEquals(1436, r.timeServedWithAllowancesDays);
        i.deductionMonths = null; i.disqualifiedForCPI = true;
        r = calculate(i);
        assertEquals(771, r.actualTimeServedDays); assertEquals(771, r.timeServedWithAllowancesDays);
    }
    @Test public void previewsStartAtZeroAndDoNotBorrowFutureJailCredits() {
        SentenceCalculator.Input i = input(); i.observationDate = i.leaArrestDate;
        SentenceCalculator.Result r = calculate(i);
        assertEquals("0y, 0m, 0d", r.actualTimeServedText()); assertEquals(0, r.timeServedWithAllowancesDays);
        i.observationDate = "2018-02-10"; r = calculate(i);
        assertEquals(31, r.actualTimeServedDays); assertEquals(31, r.timeServedWithAllowancesDays);
        assertEquals("0y, 1m, 0d", r.actualTimeServedText());
    }
    @Test public void leapYearAndMissingArrestUseTheRecordedStart() {
        SentenceCalculator.Input i = input(); i.leaArrestDate = null; i.commitmentDate = "2024-02-29";
        i.observationDate = "2025-03-01"; i.disqualifiedForCPI = true;
        SentenceCalculator.Result r = calculate(i);
        assertEquals(366, r.actualTimeServedDays); assertEquals("1y, 0m, 1d", r.actualTimeServedText());
    }
    @Test public void newChargeAndOverdueDatesDoNotResetOrCapCumulativeTotals() {
        Profile p = new Profile(); p.leaArrestDate = p.commitmentDate = "2024-01-01";
        for (int length : new int[]{40, 20}) {
            ProfileCharge c = new ProfileCharge(); c.minDays = c.maxDays = length; p.charges.add(c);
        }
        SequentialSentencePlan first = new SequentialSentencePlan(p);
        p.serviceChoices = first.appendChoice(true, true);
        SequentialSentencePlan second = new SequentialSentencePlan(p);
        SentenceCalculator.Result r = second.calculate(second.active().start);
        assertEquals(31, r.actualTimeServedDays); assertEquals(66, r.timeServedWithAllowancesDays);
        r = second.calculate(LocalDate.of(2024, 4, 1));
        assertEquals(91, r.actualTimeServedDays); assertEquals(196, r.timeServedWithAllowancesDays);
        assertTrue(r.maxRemainingDays < 0);
    }
}
