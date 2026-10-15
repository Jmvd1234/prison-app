package com.example.myapplication;

import com.example.myapplication.db.Profile;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class PrisonTransferTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2018-01-10";
        in.commitmentDate = "2018-06-20";
        in.prisonTransferDate = "2019-03-10";
        in.observationDate = "2020-02-20";
        in.minYears = 5; in.maxYears = 10;
        return in;
    }
    private SentenceCalculator.Result result(SentenceCalculator.Input in, boolean preview) {
        SentenceCalculator.Result r = preview ? SentenceCalculator.calculatePreview(in) : SentenceCalculator.calculate(in);
        assertTrue(r.problem, r.available);
        SentenceCalculator.CustodyBreakdown b = r.custodyBreakdown;
        assertEquals(b.total.days, b.lea.days + b.jail.days + b.prison.days);
        assertEquals(b.total.gctaDays, b.lea.gctaDays + b.jail.gctaDays + b.prison.gctaDays);
        assertEquals(b.total.tastmDays, b.lea.tastmDays + b.jail.tastmDays + b.prison.tastmDays);
        return r;
    }

    @Test public void splitsDaysAndCreditsWithoutResettingPartialMonthsOrChangingRelease() {
        SentenceCalculator.Input in = input();
        SentenceCalculator.Result split = result(in, false);
        assertEquals(161, split.custodyBreakdown.lea.days);
        assertEquals(263, split.custodyBreakdown.jail.days);
        assertEquals(347, split.custodyBreakdown.prison.days);
        assertEquals(160, split.custodyBreakdown.jail.gctaDays);
        assertEquals(120, split.custodyBreakdown.jail.tastmDays);
        assertEquals(240, split.custodyBreakdown.prison.gctaDays);
        assertEquals(180, split.custodyBreakdown.prison.tastmDays);
        in.prisonTransferDate = null;
        SentenceCalculator.Result ordinary = result(in, false);
        assertEquals(ordinary.minRemainingDays, split.minRemainingDays);
        assertEquals(ordinary.maxRemainingDays, split.maxRemainingDays);
    }

    @Test public void transferOnAnAllowanceAnniversaryBelongsToTheCompletedJailInterval() {
        SentenceCalculator.Input in = input(); in.prisonTransferDate = "2019-03-20";
        in.observationDate = in.prisonTransferDate;
        SentenceCalculator.Result at = result(in, false);
        assertEquals(180, at.custodyBreakdown.jail.gctaDays);
        assertEquals(0, at.custodyBreakdown.prison.days);
        assertEquals(0, at.custodyBreakdown.prison.gctaDays);
        in.observationDate = "2019-04-20";
        SentenceCalculator.Result later = result(in, false);
        assertEquals(180, later.custodyBreakdown.jail.gctaDays);
        assertEquals(20, later.custodyBreakdown.prison.gctaDays);
        assertEquals(15, later.custodyBreakdown.prison.tastmDays);
    }

    @Test public void tierProgressAndMonthEndsCarryAcrossTransfer() {
        SentenceCalculator.Input in = input();
        in.commitmentDate = "2018-01-31"; in.prisonTransferDate = "2020-01-31";
        in.observationDate = "2023-02-28";
        SentenceCalculator.Result r = result(in, false);
        assertEquals(480, r.custodyBreakdown.jail.gctaDays);
        assertEquals(36 * 23 + 25, r.custodyBreakdown.prison.gctaDays);
        assertEquals(37 * 15, r.custodyBreakdown.prison.tastmDays);
    }

    @Test public void historicalPreviewsNeverCountFuturePrisonDaysOrCredits() {
        SentenceCalculator.Input in = input();
        for (String date : new String[]{"2018-01-10", "2018-02-10", "2018-06-20", "2019-03-09"}) {
            in.observationDate = date;
            SentenceCalculator.Result r = result(in, true);
            assertTrue(r.custodyBreakdown.prison.recorded);
            assertEquals(0, r.custodyBreakdown.prison.days);
            assertEquals(0, r.custodyBreakdown.prison.gctaDays);
            assertEquals(0, r.custodyBreakdown.prison.tastmDays);
        }
    }

    @Test public void convictionRemainsIndependentAndDoesNotProduceNegativeEarnedJailCredits() {
        SentenceCalculator.Input in = input(); in.disqualifiedForTimeAllowances = true;
        in.convictionDate = "2019-10-20";
        SentenceCalculator.Result r = result(in, false);
        assertEquals(0, r.custodyBreakdown.jail.gctaDays);
        assertEquals(0, r.custodyBreakdown.jail.tastmDays);
        assertEquals(80, r.custodyBreakdown.prison.gctaDays);
        assertEquals(60, r.custodyBreakdown.prison.tastmDays);
        in.convictionDate = null;
        assertEquals(0, result(in, false).gctaDays);
    }

    @Test public void globalAdjustmentsAndAllTriggerModesKeepTheirOriginalTotals() {
        for(int bits=0;bits<8;bits++) {
            SentenceCalculator.Input in = input();
            in.notCommittedToJail=(bits&1)!=0; in.disqualifiedForCPI=(bits&2)!=0;
            in.disqualifiedForTimeAllowances=(bits&4)!=0;
            in.convictionDate="2018-09-20";in.stalPercent=40;in.timeGapDays=7;in.deductionDays=13;
            SentenceCalculator.Result with = result(in,false);
            in.prisonTransferDate=null;
            SentenceCalculator.Result without=result(in,false);
            assertEquals(without.minRemainingDays,with.minRemainingDays);
            assertEquals(without.maxRemainingDays,with.maxRemainingDays);
            assertEquals(without.stalDays,with.stalDays);
        }
    }

    @Test public void activeTransfersRejectMalformedSameDayEarlierAndFutureDatesButAcceptBlank() {
        for(String invalid:new String[]{"bad","2019-02-30","2018-06-19","2018-06-20","2020-02-21"}) {
            SentenceCalculator.Input in=input();in.prisonTransferDate=invalid;
            SentenceCalculator.Result r=SentenceCalculator.calculate(in);
            assertFalse(invalid,r.available);assertNull(r.custodyBreakdown);
        }
        SentenceCalculator.Input in=input();in.prisonTransferDate="  ";
        assertFalse(result(in,false).custodyBreakdown.prison.recorded);
        in.prisonTransferDate=in.observationDate;
        assertEquals(0,result(in,false).custodyBreakdown.prison.days);
    }

    @Test public void sliderAddsExactPrisonStopAndKeepsAbsoluteReleaseDates() {
        Profile p=new Profile();p.leaArrestDate="2018-01-10";p.commitmentDate="2018-06-20";
        p.minYears=5;p.maxYears=10;
        LocalDate today=LocalDate.of(2020,2,20), transfer=LocalDate.of(2019,3,10);
        SentenceProjection.Range original=SentenceProjection.create(p,today);
        p.prisonTransferDate=transfer.toString();
        SentenceProjection.Range split=SentenceProjection.create(p,today);
        assertEquals(original.minimumCompletion,split.minimumCompletion);
        assertEquals(original.maximumCompletion,split.maximumCompletion);
        assertTrue(split.dates.contains(transfer));
        assertEquals(6,split.milestones.size());
        assertTrue(split.milestones.stream().anyMatch(m->m.kind==SentenceProjection.Kind.PRISON_COMMITMENT&&m.date.equals(transfer)));
        assertEquals(transfer.toString(),p.prisonTransferDate);
        p.disqualifiedForCPI=true;
        assertNull(ProfileCalculationMapper.from(p,today).prisonTransferDate);
        assertEquals(5,SentenceProjection.create(p,today).milestones.size());
    }

    @Test public void legacyPrisonAliasDoesNotSilentlyBecomeARecordedTransfer() {
        Profile p=new Profile();p.leaArrestDate="2018-01-10";p.commitmentDate="2018-06-20";
        p.prisonCommitmentDate="2019-03-10";p.minYears=5;p.maxYears=10;
        SentenceCalculator.Result r=result(ProfileCalculationMapper.from(p,LocalDate.of(2020,2,20)),false);
        assertFalse(r.custodyBreakdown.prison.recorded);
    }
}
