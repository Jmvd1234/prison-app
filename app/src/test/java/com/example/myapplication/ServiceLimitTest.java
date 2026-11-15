package com.example.myapplication;

import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServiceLimitTest {
    private Profile profile(int... maxima){
        Profile p=new Profile();p.commitmentDate=p.leaArrestDate="2020-01-01";
        for(int max:maxima){ProfileCharge c=new ProfileCharge();c.minDays=c.maxDays=max;p.charges.add(c);}
        return p;
    }
    @Test public void defaultCountsNetAllowancesAcrossChargesAndStopsAtFirstCrossing(){
        Profile p=profile(100,100,100,100);p.serviceChoices="22";
        assertTrue(p.considerAllowancesInServiceLimit);
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(300,plan.capDays);assertEquals(LocalDate.of(2020,6,1),plan.capDate);
        assertEquals(3,plan.active().number);assertEquals(plan.capDate,plan.active().maximum);
        assertTrue(plan.calculate(plan.capDate.minusDays(1)).timeServedWithAllowancesDays<300);
        assertEquals(327,plan.calculate(plan.capDate).timeServedWithAllowancesDays);
        assertTrue(plan.calculate(plan.capDate).maxRemainingDays<=0);
        assertFalse(plan.hasNext());assertFalse(plan.finalCharge());assertTrue(plan.limitRelevant(plan.capDate));
        assertEquals("Threefold rule time reached",plan.limitReachedText());
        assertEquals(plan.capDate,plan.overdueStart(plan.capDate.plusDays(1)));
        try{plan.appendChoice(false,true);fail();}catch(IllegalArgumentException expected){assertEquals(plan.limitReachedText(),expected.getMessage());}
    }
    @Test public void disablingRestoresActualCustodyCapWithoutTurningOffChargeAllowances(){
        Profile p=profile(100,100,100,100);p.serviceChoices="22";
        SequentialSentencePlan credited=new SequentialSentencePlan(p);
        p.considerAllowancesInServiceLimit=false;SequentialSentencePlan actual=new SequentialSentencePlan(p);
        assertEquals(actual.start.plusDays(300),actual.capDate);assertTrue(actual.hasNext());
        assertEquals(credited.calculate(credited.capDate).gctaDays,actual.calculate(credited.capDate).gctaDays);
        p.serviceChoices=actual.appendChoice(false,true);
        assertEquals(4,new SequentialSentencePlan(p).active().number);
        p.considerAllowancesInServiceLimit=true;
        assertEquals(3,new SequentialSentencePlan(p).active().number);
        assertEquals("222",p.serviceChoices);
        p.considerAllowancesInServiceLimit=false;
        assertEquals(4,new SequentialSentencePlan(p).active().number);
    }
    @Test public void fortyYearThresholdAndLabelUseTheLongestMaximum(){
        Profile p=profile(9000,8000,7000);p.serviceChoices="2";
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertTrue(plan.fortyYearLimit);assertEquals(14610,plan.capDays);
        assertEquals("40 year maximum time reached",plan.limitReachedText());
        assertTrue(plan.capDate.isBefore(LocalDate.of(2060,1,1)));
        assertEquals(plan.capDate,plan.active().maximum);assertFalse(plan.hasNext());
        assertTrue(plan.calculate(plan.capDate).timeServedWithAllowancesDays>=plan.capDays);
        assertTrue(plan.calculate(plan.capDate.minusDays(1)).timeServedWithAllowancesDays<plan.capDays);
    }
    @Test public void gapsAndDeductionsMatchTheDisplayedCreditedTotal(){
        Profile p=profile(100,100,100,100);p.serviceChoices="22";p.timeGapDays=7;p.deductionDays=15;
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertTrue(plan.calculate(plan.capDate.minusDays(1)).timeServedWithAllowancesDays<300);
        assertTrue(plan.calculate(plan.capDate).timeServedWithAllowancesDays>=300);
        SentenceCalculator.Result current=plan.calculate(plan.capDate.minusDays(1));
        assertEquals(current.actualTimeServedDays+current.netAllowanceDays,current.timeServedWithAllowancesDays);
        assertEquals(300-current.timeServedWithAllowancesDays,current.maxRemainingDays);
    }
    @Test public void disqualifiedAllowancesKeepTheSameCutoffInBothModes(){
        Profile p=profile(100,100);p.disqualifiedForCPI=true;
        SequentialSentencePlan credited=new SequentialSentencePlan(p);
        p.considerAllowancesInServiceLimit=false;
        assertEquals(credited.capDate,new SequentialSentencePlan(p).capDate);
    }
    @Test public void savedLateCustomRouteRemainsAvailableWhenSwitchingBack(){
        Profile p=profile(100,100,100);p.considerAllowancesInServiceLimit=false;
        SequentialSentencePlan first=new SequentialSentencePlan(p);
        p.serviceChoices=first.appendCustomDate(first.start.plusDays(250),first.active().maximum);
        assertEquals(2,new SequentialSentencePlan(p).active().number);
        p.considerAllowancesInServiceLimit=true;
        assertEquals(1,new SequentialSentencePlan(p).active().number);
        assertEquals("v2:@2020-09-07",p.serviceChoices);
        p.considerAllowancesInServiceLimit=false;
        assertEquals(LocalDate.of(2020,9,7),new SequentialSentencePlan(p).active().start);
    }
    @Test public void stalAndMonthlyDeductionsAreIncludedUsingTheCurrentChargeTotals(){
        Profile p=profile(1000,900,800,700);p.stalPercent=20;p.serviceChoices="22";
        p.deductionMonths.add(new DeductionMonth(2020,2,true,true));
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertTrue(plan.calculate(plan.capDate).timeServedWithAllowancesDays>=plan.capDays);
        assertTrue(plan.calculate(plan.capDate.minusDays(1)).timeServedWithAllowancesDays<plan.capDays);
        assertTrue(plan.calculate(plan.active().start).completedImprisonmentMonths>0);
    }
}
