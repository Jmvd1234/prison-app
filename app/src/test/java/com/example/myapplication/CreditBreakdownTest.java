package com.example.myapplication;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreditBreakdownTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2023-01-15";
        in.commitmentDate = "2023-02-15";
        in.observationDate = "2025-04-15";
        in.minYears = 5; in.maxYears = 10;
        return in;
    }

    private SentenceCalculator.Result at(SentenceCalculator.Input in, String observation) {
        in.observationDate = observation;
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(in);
        assertTrue(result.problem, result.available);
        SentenceCalculator.CustodyBreakdown b = result.custodyBreakdown;
        assertEquals(b.total.netGctaDays, b.lea.netGctaDays + b.jail.netGctaDays + b.prison.netGctaDays);
        assertEquals(b.total.netTastmDays, b.lea.netTastmDays + b.jail.netTastmDays + b.prison.netTastmDays);
        assertEquals(result.gctaDays - result.monthlyGctaDeducted, b.total.netGctaDays);
        assertEquals(result.tastmDays - result.monthlyTastmDeducted, b.total.netTastmDays);
        return result;
    }

    private void credits(SentenceCalculator.StageCredits stage, long netGcta, long grossGcta,
            long netTastm, long grossTastm) {
        assertEquals(netGcta, stage.netGctaDays); assertEquals(grossGcta, stage.gctaDays);
        assertEquals(netTastm, stage.netTastmDays); assertEquals(grossTastm, stage.tastmDays);
    }

    @Test public void independentCheckboxesKeepPossibleCreditsAndBalancesConsistent() {
        SentenceCalculator.Input in = input();
        SentenceCalculator.Result original = at(in, "2023-04-15");
        in.deductionMonths = Arrays.asList(new DeductionMonth(2023,2,true,false),
                new DeductionMonth(2023,3,false,true));
        SentenceCalculator.Result withheld = at(in, "2023-04-15");
        credits(withheld.custodyBreakdown.jail,20,40,15,30);
        credits(withheld.custodyBreakdown.lea,0,0,0,0);
        assertFalse(withheld.custodyBreakdown.prison.recorded);
        assertEquals(original.minRemainingDays + 35, withheld.minRemainingDays);
        assertEquals(original.maxRemainingDays + 35, withheld.maxRemainingDays);
        in.deductionMonths = Collections.singletonList(new DeductionMonth(2023,2,false,false));
        credits(at(in,"2023-04-15").custodyBreakdown.total,40,40,30,30);
    }

    @Test public void transferAnniversaryUsesJailAndNextTierUsesPrison() {
        SentenceCalculator.Input in = input(); in.prisonTransferDate = "2025-02-15";
        in.deductionMonths = Arrays.asList(new DeductionMonth(2025,1,true,true),
                new DeductionMonth(2025,2,true,false), new DeductionMonth(2025,3,false,true));
        SentenceCalculator.CustodyBreakdown b = at(in,"2025-04-15").custodyBreakdown;
        credits(b.jail,460,480,345,360);
        credits(b.prison,23,46,15,30);
        credits(b.total,483,526,360,390);
        b = at(in,"2025-02-15").custodyBreakdown;
        credits(b.jail,460,480,345,360); credits(b.prison,0,0,0,0);
        b = at(in,"2025-02-14").custodyBreakdown;
        credits(b.total,460,460,345,345); // Rewinding restores all not-yet-earned deductions.
    }

    @Test public void periodEndingAfterTransferBelongsToPrisonIncludingLeapMonthEnd() {
        SentenceCalculator.Input in = input(); in.leaArrestDate = "2024-01-01";
        in.commitmentDate = "2024-01-31"; in.prisonTransferDate = "2024-02-20";
        in.deductionMonths = Collections.singletonList(new DeductionMonth(2024,1,true,true));
        credits(at(in,"2024-01-15").custodyBreakdown.total,0,0,0,0);
        credits(at(in,"2024-02-28").custodyBreakdown.total,0,0,0,0);
        SentenceCalculator.CustodyBreakdown b = at(in,"2024-02-29").custodyBreakdown;
        credits(b.jail,0,0,0,0); credits(b.prison,0,20,0,15);
    }

    @Test public void directPrisonAndConvictionClocksUseTheSameCreditAllocation() {
        SentenceCalculator.Input in = input(); in.notCommittedToJail = true;
        in.prisonTransferDate = "not used";
        in.deductionMonths = Collections.singletonList(new DeductionMonth(2023,2,true,true));
        SentenceCalculator.CustodyBreakdown b = at(in,"2023-03-15").custodyBreakdown;
        assertFalse(b.jail.recorded); credits(b.prison,0,20,0,15);
        in.disqualifiedForCPI = true;
        credits(at(in,"2025-04-15").custodyBreakdown.prison,506,526,375,390);
        in.notCommittedToJail = false; in.disqualifiedForCPI = false;
        in.disqualifiedForTimeAllowances = true; in.convictionDate = "2024-05-20";
        in.prisonTransferDate = "2024-06-20";
        in.deductionMonths = Arrays.asList(new DeductionMonth(2024,5,true,true),new DeductionMonth(2024,6,true,false));
        b = at(in,"2024-07-20").custodyBreakdown;
        credits(b.jail,0,20,0,15); credits(b.prison,0,20,15,15);
        in.convictionDate = null;
        credits(at(in,"2024-07-20").custodyBreakdown.total,0,0,0,0);
    }

    @Test public void unallocatedManualDeductionsAndStalStaySeparateFromMonthlyRatios() {
        SentenceCalculator.Input in = input(); in.deductionDays = 500; in.stalPercent = 20;
        SentenceCalculator.Result manual = at(in,"2023-04-15");
        credits(manual.custodyBreakdown.total,40,40,30,30);
        assertEquals(500,manual.appliedDeductionDays);
        in.deductionMonths = Collections.singletonList(new DeductionMonth(2023,2,true,false));
        SentenceCalculator.Result monthly = at(in,"2023-04-15");
        credits(monthly.custodyBreakdown.total,20,40,30,30);
        assertEquals(20,monthly.appliedDeductionDays); assertEquals(manual.stalDays,monthly.stalDays);
        assertEquals(monthly.custodyBreakdown.total.netGctaDays
                + monthly.custodyBreakdown.total.netTastmDays + monthly.stalDays, monthly.netAllowanceDays);
    }
}
