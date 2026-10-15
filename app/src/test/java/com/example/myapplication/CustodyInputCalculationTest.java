package com.example.myapplication;

import com.example.myapplication.db.Profile;
import org.junit.Test;
import java.time.LocalDate;
import java.util.Collections;
import static org.junit.Assert.*;

public class CustodyInputCalculationTest {
    private Profile profile(boolean cpi) {
        Profile p = new Profile(); p.separateCustodyDates = true;
        p.leaArrestDate = "2018-01-10"; p.jailCommitmentDate = "2018-06-20";
        p.prisonCommitmentDate = p.prisonTransferDate = "2019-03-10";
        p.commitmentDate = cpi ? p.prisonCommitmentDate : p.jailCommitmentDate;
        p.disqualifiedForCPI = cpi; p.disqualifiedForTimeAllowances = true;
        p.minYears = 5; p.maxYears = 10;
        return p;
    }
    private SentenceCalculator.Result at(Profile p, String date) {
        SentenceCalculator.Result r = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, LocalDate.parse(date)));
        assertTrue(r.problem, r.available);
        SentenceCalculator.CustodyBreakdown b = r.custodyBreakdown;
        assertEquals(b.total.days, b.lea.days + b.jail.days + b.prison.days);
        return r;
    }
    @Test public void cpiSplitsOnlyCustodyAndKeepsPrisonAsTheCalculationAnchor() {
        Profile p = profile(true);
        SentenceCalculator.Result r = at(p, "2020-02-20");
        assertEquals("2019-03-10", ProfileCalculationMapper.from(p).commitmentDate);
        assertEquals(161, r.custodyBreakdown.lea.days);
        assertEquals(263, r.custodyBreakdown.jail.days);
        assertEquals(347, r.custodyBreakdown.prison.days);
        assertEquals(0, r.gctaDays); assertEquals(0, r.tastmDays);
        p.jailCommitmentDate = null;
        SentenceCalculator.Result without = at(p, "2020-02-20");
        assertFalse(without.custodyBreakdown.jail.recorded);
        assertEquals(r.minRemainingDays, without.minRemainingDays);
        assertEquals(r.maxRemainingDays, without.maxRemainingDays);
    }
    @Test public void cpiPreviewsNeverCountFutureJailOrPrisonDays() {
        Profile p = profile(true);
        assertEquals(0, at(p, "2018-06-19").custodyBreakdown.jail.days);
        assertEquals(1, at(p, "2018-06-21").custodyBreakdown.jail.days);
        assertEquals(0, at(p, "2019-03-09").custodyBreakdown.prison.days);
        assertEquals(1, at(p, "2019-03-11").custodyBreakdown.prison.days);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2020, 2, 20));
        assertTrue(range.milestones.stream().anyMatch(m -> m.kind == SentenceProjection.Kind.JAIL_COMMITMENT && m.date.equals(LocalDate.of(2018,6,20))));
        assertTrue(range.milestones.stream().anyMatch(m -> m.kind == SentenceProjection.Kind.PRISON_COMMITMENT && m.date.equals(LocalDate.of(2019,3,10))));
    }
    @Test public void cpiWithNoLeaStartsFromItsEarliestRecordedCustodyDate() {
        Profile p = profile(true); p.leaArrestDate = null;
        assertEquals(0, at(p, "2020-02-20").custodyBreakdown.lea.days);
        assertEquals(263, at(p, "2020-02-20").custodyBreakdown.jail.days);
        assertEquals(LocalDate.of(2018,6,20), SentenceProjection.create(p, LocalDate.of(2020,2,20)).start);
    }
    @Test public void noJailAndLegacyRecordsIgnoreUnrecordedJailDrafts() {
        Profile p = profile(true); p.jailCommitmentDate = "not a recorded stage";
        p.notCommittedToJail = true;
        assertFalse(at(p, "2020-02-20").custodyBreakdown.jail.recorded);
        p.notCommittedToJail = false; p.separateCustodyDates = false;
        assertFalse(at(p, "2020-02-20").custodyBreakdown.jail.recorded);
        assertEquals("", ProfileCalculationMapper.jailInputDate(p));
        assertEquals(p.commitmentDate, ProfileCalculationMapper.prisonInputDate(p));
    }
    @Test public void invalidRecordedCpiJailDatesCannotCreateOverlappingStages() {
        for (String date : new String[]{"invalid", "2018-01-09", "2019-03-10", "2019-03-11"}) {
            Profile p = profile(true); p.jailCommitmentDate = date;
            assertFalse(SentenceCalculator.calculate(ProfileCalculationMapper.from(p, LocalDate.of(2020,2,20))).available);
        }
    }
    @Test public void taStartsCreditsAtPrisonAndHasNoNegativeHistoricalCredits() {
        Profile p = profile(false); p.convictionDate = "ignored retired field";
        for (String date : new String[]{"2018-01-10", "2018-06-20", "2019-03-09", "2019-03-10", "2019-04-09"}) {
            SentenceCalculator.Result r = at(p, date);
            assertEquals(0, r.gctaDays); assertEquals(0, r.tastmDays);
        }
        SentenceCalculator.Result r = at(p, "2019-04-10");
        assertEquals(0, r.custodyBreakdown.jail.gctaDays);
        assertEquals(20, r.custodyBreakdown.prison.gctaDays);
        assertEquals(15, r.custodyBreakdown.prison.tastmDays);
        p.prisonTransferDate = null;
        assertEquals(0, at(p, "2020-02-20").gctaDays);
    }
    @Test public void taDeductionMonthsFollowThePrisonAllowanceClock() {
        Profile p = profile(false);
        p.deductionMonths = Collections.singletonList(new DeductionMonth(2019,3,true,true));
        assertEquals(0, at(p, "2019-04-09").appliedDeductionDays);
        SentenceCalculator.Result r = at(p, "2019-04-10");
        assertEquals(35, r.appliedDeductionDays);
        assertEquals(0, r.custodyBreakdown.prison.netGctaDays);
        assertEquals(0, r.custodyBreakdown.prison.netTastmDays);
    }
    @Test public void taWithNoJailUsesTheRequiredPrisonDateForBothClocks() {
        Profile p = profile(false); p.notCommittedToJail = true;
        p.commitmentDate = p.prisonTransferDate;
        assertNull(ProfileCalculationMapper.from(p).convictionDate); // Direct prison uses commitment, not a second clock.
        SentenceCalculator.Result r = at(p, "2019-04-10");
        assertFalse(r.custodyBreakdown.jail.recorded);
        assertEquals(20, r.custodyBreakdown.prison.gctaDays);
    }
}
