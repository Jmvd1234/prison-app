package com.example.myapplication;

import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClemencyAssessmentTest {
    private Profile profile() {
        Profile p=new Profile();p.leaArrestDate=p.commitmentDate="2000-02-29";
        p.convicted=true;p.clemencyCategory="SINGLE_GENERAL";
        ProfileCharge c=new ProfileCharge();c.minYears=30;c.maxYears=40;p.charges.add(c);
        return p;
    }
    private SentenceCalculator.Result result(Profile p,LocalDate d) {
        return SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p,d));
    }
    @Test public void defaultAndNoneProduceNoReminderOrRangeChange() {
        Profile p=profile();p.convicted=false;p.clemencyCategory="NONE";
        ClemencyAssessment a=ClemencyAssessment.create(p,null);
        assertNull(a.reviewDate);assertFalse(a.reached(LocalDate.of(2100,1,1)));
        p.convicted=true;assertNull(ClemencyAssessment.create(p,null).reviewDate);
    }
    @Test public void allSixCategoriesUseTheirCalendarThresholdAndEqualityCounts() {
        for(ClemencyCategory category:ClemencyCategory.values())if(category.years>0){
            Profile p=profile();p.clemencyCategory=category.name();p.considerAllowancesInServiceLimit=false;
            ClemencyAssessment a=ClemencyAssessment.create(p,null);
            assertEquals(LocalDate.of(2000,2,29).plusYears(category.years),a.reviewDate);
            assertFalse(a.reached(a.reviewDate.minusDays(1)));assertTrue(a.reached(a.reviewDate));
            assertTrue(a.reached(a.reviewDate.plusDays(1)));
            assertEquals(a.requiredDays,result(p,a.reviewDate).actualTimeServedDays);
        }
    }
    @Test public void allowancesDefaultOnAndFirstCrossingMatchesDisplayedServiceCounter() {
        for(ClemencyCategory category:ClemencyCategory.values())if(category.years>0){
            Profile p=profile();p.clemencyCategory=category.name();assertTrue(p.considerAllowancesInServiceLimit);
            ClemencyAssessment a=ClemencyAssessment.create(p,null);
            assertTrue(result(p,a.reviewDate).timeServedWithAllowancesDays>=a.requiredDays);
            assertTrue(result(p,a.reviewDate.minusDays(1)).timeServedWithAllowancesDays<a.requiredDays);
            p.considerAllowancesInServiceLimit=false;
            assertTrue(a.reviewDate.isBefore(ClemencyAssessment.create(p,null).reviewDate));
        }
    }
    @Test public void gapsManualDeductionsAndRecordedPreventiveCustodyUseExistingCounter() {
        Profile p=profile();p.commitmentDate="2001-02-28";p.timeGapDays=100;p.deductionDays=500;
        ClemencyAssessment a=ClemencyAssessment.create(p,null);
        assertTrue(result(p,a.reviewDate.minusDays(1)).timeServedWithAllowancesDays<a.requiredDays);
        assertTrue(result(p,a.reviewDate).timeServedWithAllowancesDays>=a.requiredDays);
        p.considerAllowancesInServiceLimit=false;
        assertEquals(LocalDate.of(2010,2,28).plusDays(100),ClemencyAssessment.create(p,null).reviewDate);
    }
    @Test public void withheldMonthsAndDisqualificationDoNotCreatePhantomCredits() {
        Profile p=profile();ClemencyAssessment base=ClemencyAssessment.create(p,null);
        p.deductionMonths=Collections.singletonList(new DeductionMonth(2000,3,true,true));
        ClemencyAssessment withheld=ClemencyAssessment.create(p,null);
        assertFalse(withheld.reviewDate.isBefore(base.reviewDate));
        assertTrue(result(p,withheld.reviewDate.minusDays(1)).timeServedWithAllowancesDays<withheld.requiredDays);
        p.disqualifiedForCPI=true;
        ClemencyAssessment disqualified=ClemencyAssessment.create(p,null);
        assertEquals(LocalDate.of(2010,2,28),disqualified.reviewDate);
    }
    @Test public void addingAndRemovingChargesDoesNotRestartTheClemencyClock() {
        Profile p=profile();p.charges.clear();
        for(int max:new int[]{12,10,8}){ProfileCharge c=new ProfileCharge();c.minYears=2;c.maxYears=max;p.charges.add(c);}
        SequentialSentencePlan first=new SequentialSentencePlan(p);
        LocalDate date=ClemencyAssessment.create(p,first).reviewDate;
        p.serviceChoices=first.appendChoice(false,false);
        SequentialSentencePlan second=new SequentialSentencePlan(p);
        assertEquals(date,ClemencyAssessment.create(p,second).reviewDate);
        p.serviceChoices=second.appendCustomDate(second.active().start.plusDays(60),second.active().start);
        SequentialSentencePlan third=new SequentialSentencePlan(p);
        assertEquals(date,ClemencyAssessment.create(p,third).reviewDate);
        p.serviceChoices="";assertEquals(date,ClemencyAssessment.create(p,new SequentialSentencePlan(p)).reviewDate);
    }
    @Test public void markerExtendsRangeAndNeverChangesReleaseDates() {
        Profile p=profile();p.charges.get(0).minYears=1;p.charges.get(0).maxYears=2;
        SentenceProjection.Range base=SentenceProjection.create(p,LocalDate.of(2002,1,1));
        ClemencyAssessment a=ClemencyAssessment.create(p,null);SentenceProjection.Range marked=a.addTo(base);
        assertEquals(base.maximumCompletion,marked.maximumCompletion);assertEquals(base.minimumCompletion,marked.minimumCompletion);
        assertTrue(marked.dates.contains(a.reviewDate));
        assertEquals(1,marked.milestones.stream().filter(m->m.kind==SentenceProjection.Kind.CLEMENCY_REVIEW).count());
    }
    @Test public void dashboardPrioritizesFinalReleaseAndHardLimitOverClemency() {
        Profile p=profile();ClemencyAssessment a=ClemencyAssessment.create(p,null);
        assertTrue(a.recommend(a.reviewDate,result(p,a.reviewDate),null));
        LocalDate late=LocalDate.of(2080,1,1);assertFalse(a.recommend(late,result(p,late),null));
        ProfileCharge extra=new ProfileCharge();extra.minYears=20;extra.maxYears=30;p.charges.add(extra);
        SequentialSentencePlan plan=new SequentialSentencePlan(p);a=ClemencyAssessment.create(p,plan);
        assertTrue(a.recommend(a.reviewDate,plan.calculateAtDate(a.reviewDate),plan));
        assertFalse(a.recommend(plan.capDate,plan.calculateAtDate(plan.capDate),plan));
    }
    @Test public void categoryRequiresConfirmationAndRejectsUnknownValues() {
        Profile p=profile();p.convicted=false;
        try{ClemencyAssessment.create(p,null);fail();}catch(IllegalArgumentException expected){}
        p.convicted=true;p.clemencyCategory="UNKNOWN";
        try{ClemencyAssessment.create(p,null);fail();}catch(IllegalArgumentException expected){}
    }
}
