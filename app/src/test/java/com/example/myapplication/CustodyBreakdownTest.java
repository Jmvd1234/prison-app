package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class CustodyBreakdownTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2018-01-10";
        in.commitmentDate = "2018-06-20";
        in.observationDate = "2020-02-20";
        in.minYears = 1;
        in.maxYears = 3;
        return in;
    }

    private SentenceCalculator.Result calculate(SentenceCalculator.Input in) {
        SentenceCalculator.Result result = SentenceCalculator.calculate(in);
        assertTrue(result.problem, result.available);
        assertNotNull(result.custodyBreakdown);
        return result;
    }

    private void row(SentenceCalculator.StageCredits row, boolean recorded,
            long days, long gcta, long tastm) {
        assertEquals(recorded, row.recorded);
        assertEquals(days, row.days);
        assertEquals(gcta, row.gctaDays);
        assertEquals(tastm, row.tastmDays);
    }

    @Test public void ordinaryCaseHasLeaAndJailWithoutInventingAPrisonTransfer() {
        SentenceCalculator.CustodyBreakdown b = calculate(input()).custodyBreakdown;
        row(b.lea, true, 161, 0, 0);
        row(b.jail, true, 610, 400, 300);
        row(b.prison, false, 0, 0, 0);
        row(b.total, true, 771, 400, 300);
    }

    @Test public void notJailMovesTheSameValuesToPrisonWithoutChangingTheBalance() {
        SentenceCalculator.Input in = input();
        SentenceCalculator.Result ordinary = calculate(in);
        in.notCommittedToJail = true;
        SentenceCalculator.Result prison = calculate(in);
        row(prison.custodyBreakdown.jail, false, 0, 0, 0);
        row(prison.custodyBreakdown.prison, true, 610, 400, 300);
        assertEquals(ordinary.minRemainingDays, prison.minRemainingDays);
        assertEquals(ordinary.maxRemainingDays, prison.maxRemainingDays);
    }

    @Test public void cpiUsesPrisonAndPreservesZeroAllowances() {
        SentenceCalculator.Input in = input();
        in.disqualifiedForCPI = true;
        SentenceCalculator.CustodyBreakdown b = calculate(in).custodyBreakdown;
        row(b.jail, false, 0, 0, 0);
        row(b.prison, true, 610, 0, 0);
        row(b.total, true, 771, 0, 0);
    }

    @Test public void taConvictionChangesCreditsWithoutPretendingToBeATransferDate() {
        SentenceCalculator.Input in = input();
        in.disqualifiedForTimeAllowances = true;
        row(calculate(in).custodyBreakdown.jail, true, 610, 0, 0);
        in.convictionDate = "2019-10-20";
        SentenceCalculator.CustodyBreakdown b = calculate(in).custodyBreakdown;
        row(b.jail, true, 610, 80, 60);
        row(b.prison, false, 0, 0, 0);
        in.convictionDate = "2020-04-20";
        row(calculate(in).custodyBreakdown.jail, true, 610, -40, -30);
    }

    @Test public void globalAdjustmentsAreAppliedOnceAfterTheGrossTableTotals() {
        for (int bits = 0; bits < 8; bits++) {
            SentenceCalculator.Input in = input();
            in.notCommittedToJail = (bits & 1) != 0;
            in.disqualifiedForCPI = (bits & 2) != 0;
            in.disqualifiedForTimeAllowances = (bits & 4) != 0;
            in.convictionDate = "2019-10-20";
            in.stalPercent = 20;
            in.deductionDays = 13;
            in.timeGapDays = 7;
            SentenceCalculator.Result r = calculate(in);
            SentenceCalculator.CustodyBreakdown b = r.custodyBreakdown;
            assertEquals(b.lea.days + b.jail.days + b.prison.days, b.total.days);
            assertEquals(b.lea.gctaDays + b.jail.gctaDays + b.prison.gctaDays, b.total.gctaDays);
            assertEquals(b.lea.tastmDays + b.jail.tastmDays + b.prison.tastmDays, b.total.tastmDays);
            long credit = b.total.days + b.total.gctaDays + b.total.tastmDays
                    + r.stalDays - in.deductionDays - in.timeGapDays;
            assertEquals(r.minSentenceDays - credit, r.minRemainingDays);
            assertEquals(r.maxSentenceDays - credit, r.maxRemainingDays);
        }
    }

    @Test public void blankArrestKeepsTheExistingZeroDayLeaFallback() {
        SentenceCalculator.Input in = input();
        in.leaArrestDate = "";
        SentenceCalculator.CustodyBreakdown b = calculate(in).custodyBreakdown;
        row(b.lea, true, 0, 0, 0);
        row(b.total, true, 610, 400, 300);
    }

    @Test public void invalidInputsDoNotExposeAPartialTable() {
        SentenceCalculator.Input in = input();
        in.commitmentDate = "invalid";
        SentenceCalculator.Result result = SentenceCalculator.calculate(in);
        assertFalse(result.available);
        assertNull(result.custodyBreakdown);
    }
}
