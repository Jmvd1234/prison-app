package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class CustodyPreviewTest {
    private SentenceCalculator.Input input(String date) {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2018-01-10";
        in.commitmentDate = "2018-06-20";
        in.observationDate = date;
        in.minYears = 1;
        in.maxYears = 3;
        return in;
    }

    @Test public void arrestStartsWithZeroDaysAndNoFutureCredit() {
        SentenceCalculator.Result r = SentenceCalculator.calculatePreview(input("2018-01-10"));
        assertTrue(r.available);
        assertEquals(0, r.custodyBreakdown.total.days);
        assertEquals(0, r.gctaDays);
        assertEquals(0, r.tastmDays);
        assertEquals(r.minSentenceDays, r.minRemainingDays);
        assertEquals(r.maxSentenceDays, r.maxRemainingDays);
    }

    @Test public void preCommitmentCountsOnlyElapsedLeaAcrossAllTriggerModes() {
        for (int flags = 0; flags < 8; flags++) {
            SentenceCalculator.Input in = input("2018-02-10");
            in.notCommittedToJail = (flags & 1) != 0;
            in.disqualifiedForCPI = (flags & 2) != 0;
            in.disqualifiedForTimeAllowances = (flags & 4) != 0;
            in.convictionDate = "2017-01-01";
            in.stalPercent = 20;
            in.deductionDays = 13;
            in.timeGapDays = 7;
            SentenceCalculator.Result r = SentenceCalculator.calculatePreview(in);
            assertTrue(r.problem, r.available);
            assertEquals(31, r.custodyBreakdown.lea.days);
            assertEquals(0, r.custodyBreakdown.jail.days);
            assertEquals(0, r.custodyBreakdown.prison.days);
            assertEquals(31, r.custodyBreakdown.total.days);
            assertEquals(0, r.gctaDays);
            assertEquals(0, r.tastmDays);
            assertEquals(r.minSentenceDays - 31 - r.stalDays + 20, r.minRemainingDays);
        }
    }

    @Test public void commitmentAndLaterMatchTheUnchangedStandardCalculation() {
        for (String date : new String[]{"2018-06-20", "2018-07-20", "2020-02-20"}) {
            for (int flags = 0; flags < 8; flags++) {
                SentenceCalculator.Input in = input(date);
                in.notCommittedToJail = (flags & 1) != 0;
                in.disqualifiedForCPI = (flags & 2) != 0;
                in.disqualifiedForTimeAllowances = (flags & 4) != 0;
                in.convictionDate = "2019-10-20";
                SentenceCalculator.Result normal = SentenceCalculator.calculate(in);
                SentenceCalculator.Result preview = SentenceCalculator.calculatePreview(in);
                assertTrue(normal.available);
                assertTrue(preview.available);
                assertEquals(normal.minRemainingDays, preview.minRemainingDays);
                assertEquals(normal.maxRemainingDays, preview.maxRemainingDays);
                assertEquals(normal.gctaDays, preview.gctaDays);
                assertEquals(normal.tastmDays, preview.tastmDays);
                assertEquals(normal.custodyBreakdown.total.days, preview.custodyBreakdown.total.days);
            }
        }
    }

    @Test public void previewDoesNotWeakenNormalInputValidation() {
        assertFalse(SentenceCalculator.calculate(input("2018-02-10")).available);
        assertFalse(SentenceCalculator.calculatePreview(input("2018-01-09")).available);
        SentenceCalculator.Input in = input("2018-02-10");
        in.leaArrestDate = "";
        assertFalse(SentenceCalculator.calculatePreview(in).available);
        in.leaArrestDate = "2018-07-01";
        assertFalse(SentenceCalculator.calculatePreview(in).available);
    }
}
