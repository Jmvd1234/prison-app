package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** LAYA clears both disqualifications in direct-prison mode. Retained app flags
 * must produce that supported mode's numbers, including deductions and previews. */
@RunWith(Parameterized.class)
public class DirectPrisonOverlapTest {
    @Parameterized.Parameters(name="{0}, disqualification bits={1}") public static Collection<Object[]> cases() throws Exception {
        List<Object[]> rows=new ArrayList<>();
        for(LayaSwitchFixture f:LayaSwitchFixture.all()) if(f.mode().equals("not-jail"))
            for(int flags=1;flags<=3;flags++) rows.add(new Object[]{f,flags});
        return rows;
    }
    private final LayaSwitchFixture fixture;
    private final int flags;
    public DirectPrisonOverlapTest(LayaSwitchFixture fixture,int flags) { this.fixture=fixture; this.flags=flags; }
    private Profile combined() {
        Profile p=fixture.profile(); p.disqualifiedForCPI=(flags&1)!=0;
        p.disqualifiedForTimeAllowances=(flags&2)!=0;
        p.jailCommitmentDate="ignored disabled jail draft";
        p.convictionDate="ignored retired conviction";
        return p;
    }
    @Test public void combinedFlagsMatchLiveDirectPrisonNumbers() {
        SentenceCalculator.Input raw=fixture.input(); raw.disqualifiedForCPI=(flags&1)!=0;
        raw.disqualifiedForTimeAllowances=(flags&2)!=0;
        raw.convictionDate="ignored hidden date";
        raw.jailBeforePrisonDate="ignored disabled jail date";
        fixture.assertMatches(SentenceCalculator.calculate(raw));
        Profile p=combined();
        SentenceCalculator.Result r=SentenceCalculator.calculate(ProfileCalculationMapper.from(p,fixture.observation()));
        fixture.assertMatches(r);
        assertFalse(r.effectiveTADisqualification);
        assertFalse(r.custodyBreakdown.jail.recorded);
        assertEquals(r.gctaDays,r.custodyBreakdown.prison.gctaDays);
        assertEquals(r.tastmDays,r.custodyBreakdown.prison.tastmDays);
        assertEquals((flags&1)!=0,p.disqualifiedForCPI); // Calculating must not rewrite stored choices.
        assertEquals((flags&2)!=0,p.disqualifiedForTimeAllowances);
    }
    @Test public void monthlyDeductionsActivateOnlyOnThePrisonCreditAnniversary() {
        Profile p=combined(); LocalDate start=LocalDate.parse(p.commitmentDate);
        p.deductionMonths=Collections.singletonList(new DeductionMonth(start.getYear(),start.getMonthValue(),true,true));
        LocalDate earned=start.plusMonths(1);
        DeductionMonth.Details d=p.deductionMonths.get(0).details(ProfileCalculationMapper.from(p));
        assertEquals(earned,d.earnedOn); assertEquals(20,d.gctaDays); assertEquals(15,d.tastmDays);
        SentenceCalculator.Result before=at(p,earned.minusDays(1)), after=at(p,earned);
        assertEquals(0,before.appliedDeductionDays); assertEquals(0,before.gctaDays);
        assertEquals(35,after.appliedDeductionDays); assertEquals(20,after.gctaDays); assertEquals(15,after.tastmDays);
        assertEquals(0,after.custodyBreakdown.prison.netGctaDays);
        assertEquals(0,after.custodyBreakdown.prison.netTastmDays);
        assertEquals(0,at(p,earned.minusDays(1)).appliedDeductionDays);
    }
    @Test public void sliderReleaseMarkersAndToggleBackUseTheSameClock() {
        Profile p=combined(), ordinary=fixture.profile();
        SentenceProjection.Range a=SentenceProjection.create(p,fixture.observation());
        SentenceProjection.Range b=SentenceProjection.create(ordinary,fixture.observation());
        assertEquals(b.minimumCompletion,a.minimumCompletion); assertEquals(b.maximumCompletion,a.maximumCompletion);
        assertTrue(at(p,a.minimumCompletion.minusDays(1)).minRemainingDays>0);
        assertTrue(at(p,a.minimumCompletion).minRemainingDays<=0);
        assertTrue(at(p,a.maximumCompletion.minusDays(1)).maxRemainingDays>0);
        assertTrue(at(p,a.maximumCompletion).maxRemainingDays<=0);
        for(LocalDate date:Arrays.asList(a.start,a.commitment.minusDays(1),a.commitment,fixture.observation())) {
            if(date.isBefore(a.start)) continue;
            assertEquals(at(ordinary,date).minRemainingDays,at(p,date).minRemainingDays);
        }
        p.notCommittedToJail=false; p.jailCommitmentDate=null;
        p.prisonTransferDate=null; p.prisonCommitmentDate=null;
        assertEquals(0,at(p,fixture.observation()).gctaDays); // CPI or TA without a prison start applies again.
    }
    private SentenceCalculator.Result at(Profile p,LocalDate day) {
        SentenceCalculator.Result r=SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p,day));
        assertTrue(r.problem,r.available); return r;
    }
}
