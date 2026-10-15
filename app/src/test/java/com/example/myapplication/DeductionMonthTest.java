package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeductionMonthTest {
    private Profile profile() {
        Profile p = new Profile(); p.leaArrestDate = "2023-01-15"; p.commitmentDate = "2023-02-15";
        p.minYears = 5; p.maxYears = 10; return p;
    }
    private SentenceCalculator.Result at(Profile p, String date) {
        SentenceCalculator.Result r = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, LocalDate.parse(date)));
        assertTrue(r.problem, r.available); return r;
    }

    @Test public void matchesLiveLayaMarch2025FixtureAndIndependentCheckboxes() {
        // Live website 2026-10-12: arrest Jan 15 2023, commitment Feb 15 2023, 5/10 years.
        Profile p = profile();
        assertEquals(725, at(p,"2026-10-12").maxRemainingDays);
        p.deductionMonths = Collections.singletonList(new DeductionMonth(2025,3,true,true));
        SentenceCalculator.Result both = at(p,"2026-10-12");
        assertEquals(917, both.gctaDays); assertEquals(645, both.tastmDays);
        assertEquals(38, both.appliedDeductionDays); assertEquals(-1064,both.minRemainingDays); assertEquals(763,both.maxRemainingDays);
        assertEquals(LocalDate.of(2028,11,13),LocalDate.of(2026,10,12).plusDays(both.maxRemainingDays));
        p.deductionMonths = Collections.singletonList(new DeductionMonth(2025,3,false,true));
        assertEquals(740,at(p,"2026-10-12").maxRemainingDays);
        p.deductionMonths = Collections.singletonList(new DeductionMonth(2025,3,true,false));
        assertEquals(748,at(p,"2026-10-12").maxRemainingDays);
        p.deductionMonths = Collections.singletonList(new DeductionMonth(2025,3,false,false));
        assertEquals(725,at(p,"2026-10-12").maxRemainingDays);
    }

    @Test public void activatesAtEarnedAnniversaryNeverBeforeIncludingLeapMonthEnds() {
        Profile p = profile(); p.leaArrestDate = "2024-01-01"; p.commitmentDate = "2024-01-31";
        p.deductionMonths = Arrays.asList(new DeductionMonth(2024,1,true,true),new DeductionMonth(2024,2,true,true));
        assertEquals(0, at(p,"2024-01-01").appliedDeductionDays);
        assertEquals(0, at(p,"2024-02-28").appliedDeductionDays);
        assertEquals(35, at(p,"2024-02-29").appliedDeductionDays);
        assertEquals(35, at(p,"2024-03-30").appliedDeductionDays);
        assertEquals(70, at(p,"2024-03-31").appliedDeductionDays);
        assertEquals(0, at(p,"2024-02-28").appliedDeductionDays); // Rewinding has no stored side effects.
    }

    @Test public void allTierBoundariesUseTheOriginalAllowanceClock() {
        Profile p = profile(); LocalDate start = LocalDate.parse(p.commitmentDate);
        int[] months = {1,24,25,60,61,120,121}; int[] rates = {20,20,23,23,25,25,30};
        for (int i=0;i<months.length;i++) {
            YearMonth label = YearMonth.from(start).plusMonths(months[i]-1);
            DeductionMonth row = new DeductionMonth(label.getYear(),label.getMonthValue(),true,true);
            p.deductionMonths = Collections.singletonList(row);
            DeductionMonth.Details d = row.details(ProfileCalculationMapper.from(p));
            assertEquals(months[i],d.position); assertEquals(rates[i],d.gctaDays);
            assertEquals(0,at(p,start.plusMonths(months[i]).minusDays(1).toString()).appliedDeductionDays);
            assertEquals(rates[i]+15,at(p,start.plusMonths(months[i]).toString()).appliedDeductionDays);
        }
    }

    @Test public void itemizedRowsOverrideAndRestoreManualTotalWithoutChangingStal() {
        Profile p = profile(); p.stalPercent=20; p.deductionDays=70;
        long stal=at(p,"2026-10-12").stalDays;
        p.deductionMonths=Collections.singletonList(new DeductionMonth(2025,3,true,true));
        SentenceCalculator.Result r=at(p,"2026-10-12");
        assertEquals(38,r.appliedDeductionDays);assertEquals(stal,r.stalDays);
        assertEquals(r.gctaDays+r.tastmDays+r.stalDays-38,r.netAllowanceDays);
        assertEquals(70,p.deductionDays);
        p.deductionMonths=Collections.singletonList(new DeductionMonth(2025,3,false,false));
        assertEquals(0,at(p,"2026-10-12").appliedDeductionDays); // An unchecked row still overrides.
        assertEquals(0,at(p,"2023-02-15").appliedDeductionDays); // No manual fallback before earning.
        p.deductionMonths=Collections.emptyList();
        SentenceCalculator.Result restored=at(p,"2026-10-12");
        assertEquals(70,restored.appliedDeductionDays);
        assertEquals(stal,restored.stalDays);
        assertEquals(r.minRemainingDays+32,restored.minRemainingDays);
    }

    @Test public void transfersDoNotResetTierOrDuplicateDeductions() {
        Profile p=profile();p.deductionMonths=Arrays.asList(new DeductionMonth(2023,2,true,true),new DeductionMonth(2025,3,true,true));
        SentenceCalculator.Result unsplit=at(p,"2026-10-12");
        p.prisonTransferDate="2024-04-21";
        SentenceCalculator.Result split=at(p,"2026-10-12");
        assertEquals(73,split.appliedDeductionDays);assertEquals(unsplit.minRemainingDays,split.minRemainingDays);
        assertEquals(split.gctaDays,split.custodyBreakdown.jail.gctaDays+split.custodyBreakdown.prison.gctaDays);
    }

    @Test public void disqualificationsNeverRemoveUncreditedAllowances() {
        Profile p=profile();p.deductionMonths=Collections.singletonList(new DeductionMonth(2025,3,true,true));
        p.disqualifiedForCPI=true;assertEquals(0,at(p,"2026-10-12").appliedDeductionDays);
        p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=true;
        assertEquals(0,at(p,"2026-10-12").appliedDeductionDays);
        p.prisonTransferDate="2025-03-20";
        assertEquals(0,at(p,"2025-04-19").appliedDeductionDays);
        assertEquals(35,at(p,"2025-04-20").appliedDeductionDays);
        p.prisonTransferDate="2025-04-20"; // A later edit retains the row but cannot deduct unearned credits.
        assertEquals(0,at(p,"2026-10-12").appliedDeductionDays);
    }

    @Test public void duplicateRowsFailInsteadOfDoubleCounting() {
        Profile p=profile();DeductionMonth row=new DeductionMonth(2025,3,true,true);
        p.deductionMonths=Arrays.asList(row,row);
        SentenceCalculator.Result result=SentenceCalculator.calculate(ProfileCalculationMapper.from(p));
        assertFalse(result.available);assertTrue(result.problem.contains("Duplicate"));
    }

    @Test public void zeroMarkersFindFirstZeroWithWithheldMonthsAndAllMonthsWithheld() {
        Profile p=profile();p.minYears=1;p.maxYears=2;
        p.deductionMonths=new ArrayList<>();
        for(int i=0;i<30;i++) {
            YearMonth month=YearMonth.of(2023,2).plusMonths(i);
            p.deductionMonths.add(new DeductionMonth(month.getYear(),month.getMonthValue(),true,true));
        }
        SentenceProjection.Range range=SentenceProjection.create(p,LocalDate.of(2023,3,15));
        assertEquals(LocalDate.of(2024,1,15),range.minimumCompletion);
        assertEquals(LocalDate.of(2025,1,15),range.maximumCompletion);
        assertTrue(at(p,range.minimumCompletion.minusDays(1).toString()).minRemainingDays>0);
        assertEquals(0,at(p,range.minimumCompletion.toString()).minRemainingDays);
        assertTrue(at(p,range.maximumCompletion.minusDays(1).toString()).maxRemainingDays>0);
        assertEquals(0,at(p,range.maximumCompletion.toString()).maxRemainingDays);
    }
}
