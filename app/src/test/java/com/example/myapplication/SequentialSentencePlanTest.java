package com.example.myapplication;

import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class SequentialSentencePlanTest {
    private Profile profile(int... lengths){
        Profile p=new Profile();p.considerAllowancesInServiceLimit=false;p.commitmentDate="2020-01-01";p.leaArrestDate="2020-01-01";
        p.disqualifiedForCPI=true;p.disqualifiedForTimeAllowances=true;
        for(int i=0;i<lengths.length;i+=2){ProfileCharge c=new ProfileCharge();c.minDays=lengths[i];c.maxDays=lengths[i+1];c.position=i/2;p.charges.add(c);}
        return p;
    }
    @Test public void severityUsesMaximumThenMinimumWithoutChangingEntryOrder(){
        Profile p=profile(10,20,30,100,40,100,1,2);
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(40,plan.active().charge.minDays);assertEquals(30,plan.ordered.get(1).minDays);
        assertEquals(10,p.charges.get(0).minDays);assertEquals(300,plan.capDays);
    }
    @Test public void bothHandoffChoicesRetainTheNextChargesFullRange(){
        for(boolean previousMax:new boolean[]{false,true})for(boolean nextMax:new boolean[]{false,true}){
            Profile p=profile(20,100,10,40);SequentialSentencePlan first=new SequentialSentencePlan(p);
            p.serviceChoices=first.appendChoice(nextMax,previousMax);
            SequentialSentencePlan next=new SequentialSentencePlan(p);
            assertEquals(LocalDate.of(2020,1,1).plusDays(previousMax?100:20),next.active().start);
            assertEquals(40,next.calculate(next.active().start).maxRemainingDays);
            assertEquals(10,next.calculate(next.active().start).minRemainingDays);
            assertTrue(next.active().minimum.isBefore(next.active().maximum));
            assertEquals(2,next.active().number);assertTrue(next.finalCharge());assertFalse(next.hasNext());
            assertEquals(100,first.active().charge.maxDays);assertEquals(300,next.capDays);
        }
    }
    @Test public void earnedMonthlyCreditsAreNotReusedAndAllowanceClockDoesNotRestart(){
        Profile p=profile(300,600,200,400);p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=false;
        SequentialSentencePlan first=new SequentialSentencePlan(p);p.serviceChoices=first.appendChoice(false,true);
        SequentialSentencePlan next=new SequentialSentencePlan(p);
        long initial=400+first.calculate(next.active().start).maxRemainingDays;
        assertEquals(initial,next.calculate(next.active().start).maxRemainingDays);
        LocalDate later=next.active().start.plusMonths(1);
        SentenceCalculator.Result atStart=next.calculate(next.active().start),atLater=next.calculate(later);
        long elapsed=java.time.temporal.ChronoUnit.DAYS.between(next.active().start,later);
        assertEquals(initial-elapsed-(atLater.netAllowanceDays-atStart.netAllowanceDays),atLater.maxRemainingDays);
        assertTrue(atStart.completedImprisonmentMonths>0);
    }
    @Test public void unusedAnniversaryCreditCarriesAcrossTheBoundary(){
        Profile p=profile(40,40,20,20);p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=false;
        SequentialSentencePlan first=new SequentialSentencePlan(p);
        assertEquals(LocalDate.of(2020,2,1),first.active().maximum);
        long unused=-first.calculate(first.active().maximum).maxRemainingDays;
        assertTrue(unused>0);
        p.serviceChoices=first.appendChoice(true,true);
        SequentialSentencePlan next=new SequentialSentencePlan(p);
        assertEquals(20-unused,next.calculate(next.active().start).maxRemainingDays);
    }
    @Test public void actualCustodyCapDoesNotShrinkWithCreditsOrMinimumChoices(){
        Profile p=profile(100,1000,100,900,100,800,100,700);
        p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=false;
        SequentialSentencePlan first=new SequentialSentencePlan(p);assertEquals(3000,first.capDays);
        p.serviceChoices=first.appendChoice(false,false);SequentialSentencePlan second=new SequentialSentencePlan(p);
        assertEquals(first.capDate,second.capDate);assertEquals(3000,second.capDays);
        assertTrue(second.active().maximum.isBefore(second.capDate));
    }
    @Test public void fortyYearLimitWinsAndIsBasedOnLongestMaximum(){
        Profile p=profile(1,9000,1,8000);SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(LocalDate.of(2060,1,1),plan.capDate);
    }
    @Test public void capClipsLastChargeAndPreventsAnotherCharge(){
        Profile p=profile(90,100,90,100,90,100,90,100);
        p.serviceChoices="333";SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(3,plan.active().number);assertEquals(plan.capDate,plan.active().maximum);
        assertFalse(plan.hasNext());assertEquals(-1,plan.calculate(plan.capDate.plusDays(1)).maxRemainingDays);
    }
    @Test public void detailRangeStartsAtTransitionOverviewRetainsOriginalStart(){
        Profile p=profile(20,100,10,40);p.serviceChoices="2";SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(plan.start,plan.range(LocalDate.of(2021,1,1),false).start);
        assertEquals(LocalDate.of(2020,1,1),plan.range(LocalDate.of(2021,1,1),true).start);
    }
    @Test public void invalidPendingChargeIsNotHiddenByFirstCharge(){
        try{new SequentialSentencePlan(profile(20,100,40,10));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void calendarTermsUseTheNewChargeStart(){
        Profile p=profile(366,366,1,2);p.charges.get(1).minDays=0;p.charges.get(1).minMonths=1;p.charges.get(1).maxDays=0;p.charges.get(1).maxMonths=2;
        p.serviceChoices="2";SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(LocalDate.of(2021,1,1),plan.active().start);assertEquals(LocalDate.of(2021,2,1),plan.active().minimum);
    }
    @Test public void custodyGapsExtendActualCustodyCap(){
        Profile p=profile(20,100,10,40);p.timeGapDays=7;SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertEquals(plan.start.plusDays(307),plan.capDate);
    }
    @Test public void rangeKeepsEarlierExtentAfterMinimumHandoff(){
        Profile p=profile(20,100,10,40);SequentialSentencePlan first=new SequentialSentencePlan(p);
        LocalDate today=LocalDate.of(2020,1,1);SentenceProjection.Range original=first.range(today,false);
        p.serviceChoices="0";SequentialSentencePlan next=new SequentialSentencePlan(p);
        assertEquals(original.start,next.range(today,false).start);
        assertFalse(next.range(today,false).end.isBefore(original.end));
    }
    @Test public void capVisibilityUsesCreditsAtTheCurrentObservation(){
        Profile p=profile(10000,18000,1000,2000);p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=false;
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertTrue(plan.limitRelevant(plan.start));assertFalse(plan.hasNext(plan.start));
        assertFalse(plan.limitRelevant(plan.start.plusDays(5000)));assertTrue(plan.hasNext(plan.start.plusDays(5000)));
        assertTrue(plan.limitRelevant(plan.capDate.plusDays(10)));
    }
    @Test public void aCappedMaximumBlocksEvenTheEarlierMinimumHandoff(){
        Profile p=profile(90,100,90,100,90,100,90,100);p.serviceChoices="22";
        SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertTrue(plan.active().minimum.isBefore(plan.capDate));assertEquals(plan.capDate,plan.active().maximum);
        assertFalse(plan.hasNext());assertTrue(plan.limitRelevant(plan.active().start));
        try{plan.appendChoice(false,false);fail();}catch(IllegalArgumentException expected){assertEquals("Threefold rule time reached",expected.getMessage());}
        assertEquals(plan.capDate,plan.overdueStart(plan.capDate.plusDays(10)));
    }
    @Test public void redStartsOnlyAfterFinalReleaseOrHardLimit(){
        Profile p=profile(20,100,10,40);SequentialSentencePlan first=new SequentialSentencePlan(p);
        assertNull(first.overdueStart(first.active().maximum.plusDays(1)));
        p.serviceChoices="2";SequentialSentencePlan last=new SequentialSentencePlan(p);
        assertEquals(last.active().maximum,last.overdueStart(last.active().maximum.plusDays(1)));
    }

    @Test public void customHandoffBeforeMinimumClosesOnlyEarnedCreditAndSurvivesReload(){
        Profile p=profile(100,200,30,80);SequentialSentencePlan first=new SequentialSentencePlan(p);
        LocalDate boundary=first.start.plusDays(15);
        p.serviceChoices=first.appendCustomDate(boundary,first.start);
        assertTrue(p.serviceChoices.startsWith("v2:"));
        SequentialSentencePlan next=new SequentialSentencePlan(p);
        assertEquals(boundary,next.active().start);assertTrue(next.active().previousCustom);
        assertEquals(30,next.calculate(boundary).minRemainingDays);assertEquals(80,next.calculate(boundary).maxRemainingDays);
        assertEquals(boundary.plusDays(80),next.active().maximum);
        assertEquals("",next.choicesBefore(2));
    }
    @Test public void customEndDateAllowsMaximumAndLaterButStopsBeforeCustodyCap(){
        Profile p=profile(100,200,30,80);SequentialSentencePlan plan=new SequentialSentencePlan(p);
        assertNotNull(plan.appendCustomDate(plan.active().start,plan.start));
        assertNotNull(plan.appendCustomDate(plan.active().maximum,plan.start));
        assertNotNull(plan.appendCustomDate(plan.active().maximum.plusDays(1),plan.start));
        assertNotNull(plan.appendCustomDate(plan.capDate.minusDays(1),plan.start));
        for(LocalDate invalid:new LocalDate[]{null,plan.start.minusDays(1),plan.capDate,plan.capDate.plusDays(1)}){
            try{plan.appendCustomDate(invalid,plan.start);fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void lateCustomHandoffPreservesEarlierMaximumWithoutReusingDelayCredit(){
        Profile p=profile(100,200,30,80,10,20);SequentialSentencePlan first=new SequentialSentencePlan(p);
        LocalDate boundary=first.active().maximum.plusDays(35);
        p.serviceChoices=first.appendCustomDate(boundary,first.active().maximum);
        String custom=p.serviceChoices;SequentialSentencePlan second=new SequentialSentencePlan(p);
        assertEquals(first.active().maximum,second.stages.get(0).maximum);
        assertEquals(boundary,second.active().start);
        assertEquals(30,second.calculate(boundary).minRemainingDays);
        assertEquals(80,second.calculate(boundary).maxRemainingDays);
        assertEquals(boundary.plusDays(30),second.active().minimum);
        assertEquals(boundary.plusDays(80),second.active().maximum);
        assertEquals(first.capDate,second.capDate);
        assertFalse(second.range(first.start,true).end.isBefore(second.active().maximum));
        p.serviceChoices=second.appendChoice(false,true);SequentialSentencePlan third=new SequentialSentencePlan(p);
        assertEquals(second.active().maximum,third.active().start);
        assertEquals(custom,third.choicesBefore(3));
        p.serviceChoices=third.choicesBefore(2);
        assertEquals(first.active().maximum,new SequentialSentencePlan(p).active().maximum);
    }
    @Test public void lateCustomHandoffCarriesAllowanceTierAndCumulativeCustody(){
        Profile p=profile(500,1000,100,200);p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=false;
        SequentialSentencePlan first=new SequentialSentencePlan(p);LocalDate boundary=first.start.plusMonths(24);
        assertTrue(boundary.isAfter(first.active().maximum));
        p.serviceChoices=first.appendCustomDate(boundary,first.active().maximum);
        SequentialSentencePlan next=new SequentialSentencePlan(p);
        SentenceCalculator.Result atStart=next.calculate(boundary),later=next.calculate(boundary.plusMonths(1));
        assertEquals(100,atStart.minRemainingDays);assertEquals(200,atStart.maxRemainingDays);
        assertEquals(24,atStart.completedImprisonmentMonths);assertEquals(25,later.completedImprisonmentMonths);
        assertEquals(SentenceCalculator.gctaRate(25),later.gctaDays-atStart.gctaDays);
        assertEquals(first.calculate(boundary).netAllowanceDays,atStart.netAllowanceDays);
        assertEquals(first.calculate(boundary).actualServiceDays,atStart.actualServiceDays);
        long elapsed=java.time.temporal.ChronoUnit.DAYS.between(boundary,boundary.plusMonths(1));
        assertEquals(200-elapsed-(later.netAllowanceDays-atStart.netAllowanceDays),later.maxRemainingDays);
    }
    @Test public void lateCustomHandoffClipsNextChargeAtRemainingCustodyLimit(){
        Profile p=profile(100,200,30,80,10,20);SequentialSentencePlan first=new SequentialSentencePlan(p);
        LocalDate boundary=first.capDate.minusDays(1);
        p.serviceChoices=first.appendCustomDate(boundary,first.active().maximum);
        SequentialSentencePlan next=new SequentialSentencePlan(p);
        assertEquals(first.capDate,next.active().minimum);assertEquals(first.capDate,next.active().maximum);
        assertEquals(1,next.calculate(boundary).maxRemainingDays);assertFalse(next.hasNext());
        assertEquals(-5,next.calculate(next.capDate.plusDays(5)).maxRemainingDays);
        assertEquals(next.capDate,next.overdueStart(next.capDate.plusDays(5)));
        try{next.appendCustomDate(boundary,boundary);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void savedCustomDatesCannotRunBackwardsOrPastCustodyLimit(){
        Profile p=profile(100,200,30,80);SequentialSentencePlan first=new SequentialSentencePlan(p);
        for(LocalDate invalid:new LocalDate[]{first.start.minusDays(1),first.capDate,first.capDate.plusDays(1)}){
            p.serviceChoices="v2:@"+invalid;
            try{new SequentialSentencePlan(p);fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void customAndNormalChoicesCanBeMixedAndUndone(){
        Profile p=profile(100,200,30,80,10,20);SequentialSentencePlan first=new SequentialSentencePlan(p);
        p.serviceChoices=first.appendCustomDate(first.start.plusDays(10),first.start);
        String firstRoute=p.serviceChoices;SequentialSentencePlan second=new SequentialSentencePlan(p);
        p.serviceChoices=second.appendChoice(false,true);SequentialSentencePlan third=new SequentialSentencePlan(p);
        assertEquals(3,third.active().number);assertEquals(second.active().maximum,third.active().start);
        assertEquals(firstRoute,third.choicesBefore(3));
    }
    @Test public void gctaTierBoundariesCarryAcrossCustomHandoffs(){
        for(int months:new int[]{24,60,120}){
            Profile p=profile(6000,12000,4000,10000);p.disqualifiedForCPI=false;p.disqualifiedForTimeAllowances=false;
            SequentialSentencePlan first=new SequentialSentencePlan(p);LocalDate date=first.commitment.plusMonths(months);
            p.serviceChoices=first.appendCustomDate(date,date);
            SequentialSentencePlan next=new SequentialSentencePlan(p);
            SentenceCalculator.Result before=next.calculate(date),after=next.calculate(date.plusMonths(1));
            assertEquals(months,before.completedImprisonmentMonths);
            assertEquals(months+1,after.completedImprisonmentMonths);
            assertEquals(SentenceCalculator.gctaRate(months+1),after.gctaDays-before.gctaDays);
            assertEquals(first.calculate(date).gctaDays,before.gctaDays);
            assertEquals(10000,before.maxRemainingDays);
        }
    }
    @Test public void pendingChargeRangeRetainsContextBeyondMaximum(){
        Profile p=profile(100,200,30,80);SequentialSentencePlan first=new SequentialSentencePlan(p);
        assertTrue(first.range(LocalDate.of(2030,1,1),false).end.isAfter(first.active().maximum));
        p.serviceChoices=first.appendChoice(false,true);SequentialSentencePlan last=new SequentialSentencePlan(p);
        assertTrue(last.range(LocalDate.of(2030,1,1),false).end.isAfter(last.active().maximum));
    }
}
