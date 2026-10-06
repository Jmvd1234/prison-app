package com.example.myapplication;

import com.example.myapplication.db.Profile;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class SentenceProjectionTest {
    private Profile profile() {
        Profile p = new Profile();
        p.observationDate = "2020-02-20";
        p.leaArrestDate = "2018-01-10";
        p.commitmentDate = "2018-06-20";
        p.minYears = 1;
        p.maxYears = 3;
        return p;
    }

    private SentenceCalculator.Result result(Profile p, LocalDate date) {
        SentenceCalculator.Result r = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, date));
        assertTrue(r.problem, r.available);
        return r;
    }

    @Test public void endpointMatchesAnIndependentDayByDaySearchInEveryTriggerMode() {
        LocalDate start = LocalDate.of(2018, 1, 10);
        for (int flags = 0; flags < 8; flags++) {
            Profile p = profile();
            p.notCommittedToJail = (flags & 1) != 0;
            p.disqualifiedForCPI = (flags & 2) != 0;
            p.disqualifiedForTimeAllowances = (flags & 4) != 0;
            p.convictionDate = "2018-08-20";
            p.stalPercent = 20;
            p.deductionDays = 13;
            p.timeGapDays = 7;
            LocalDate expected = start, expectedMinimum = null;
            while (result(p, expected).maxRemainingDays > 0) {
                if (expectedMinimum == null && result(p, expected).minRemainingDays <= 0) expectedMinimum = expected;
                expected = expected.plusDays(1);
                assertTrue("Reference search must be bounded", expected.isBefore(start.plusYears(4)));
            }
            if (expectedMinimum == null) expectedMinimum = expected;
            SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2018, 7, 20));
            assertEquals(expected, range.maximumCompletion);
            assertEquals(expectedMinimum, range.minimumCompletion);
            assertTrue(range.dates.contains(range.minimumCompletion));
            assertTrue(result(p, range.maximumCompletion).maxRemainingDays <= 0);
            if (range.maximumCompletion.isAfter(start)) assertTrue(result(p, range.maximumCompletion.minusDays(1)).maxRemainingDays > 0);
        }
    }

    @Test public void monthStepsRetainExactTodayAndCompletionDatesWithoutDuplicates() {
        Profile p = profile();
        LocalDate today = LocalDate.of(2018, 7, 9);
        SentenceProjection.Range r = SentenceProjection.create(p, today);
        assertTrue(r.dates.contains(today));
        assertTrue(r.dates.contains(r.maximumCompletion));
        assertTrue(r.dates.contains(r.minimumCompletion));
        assertTrue(r.dates.contains(LocalDate.of(2018, 6, 20)));
        assertTrue(r.dates.contains(LocalDate.of(2018, 7, 20)));
        for (int i = 1; i < r.dates.size(); i++) assertTrue(r.dates.get(i).isAfter(r.dates.get(i - 1)));
        assertEquals(r.start, r.dates.get(0));
        assertEquals(r.end, r.dates.get(r.dates.size() - 1));
        assertEquals(r.maximumCompletion, r.end);
    }

    @Test public void completedSentenceExtendsTheRangeToToday() {
        LocalDate today = LocalDate.of(2026, 10, 6);
        SentenceProjection.Range r = SentenceProjection.create(profile(), today);
        assertTrue(r.maximumCompletion.isBefore(today));
        assertEquals(today, r.end);
        assertEquals(today, r.dates.get(r.dates.size() - 1));
    }

    @Test public void monthEndsStayAnchoredInsteadOfDriftingAfterFebruary() {
        Profile p = profile();
        p.leaArrestDate = p.commitmentDate = "2020-01-31";
        SentenceProjection.Range r = SentenceProjection.create(p, LocalDate.of(2020, 2, 10));
        assertTrue(r.dates.contains(LocalDate.of(2020, 2, 29)));
        assertTrue(r.dates.contains(LocalDate.of(2020, 3, 31)));
        assertFalse(r.dates.contains(LocalDate.of(2020, 3, 29)));
    }

    @Test public void taOnlyUsesConvictionAnniversariesAndKeepsManualPreviewOnRestore() {
        Profile p = profile();
        p.disqualifiedForTimeAllowances = true;
        p.convictionDate = "2018-08-05";
        LocalDate preview = LocalDate.of(2018, 7, 9);
        SentenceProjection.Range r = SentenceProjection.create(p, LocalDate.of(2018, 7, 10), preview);
        assertTrue(r.dates.contains(LocalDate.of(2018, 9, 5)));
        assertTrue(r.dates.contains(preview));
        assertEquals("2020-02-20", p.observationDate);
        assertEquals("2018-08-05", p.convictionDate);
    }

    @Test public void mappingIgnoresLegacyDateAndNeverWritesToProfile() {
        Profile p = profile();
        p.observationDate = "invalid legacy date";
        assertEquals(LocalDate.now().toString(), ProfileCalculationMapper.from(p).observationDate);
        assertEquals("2018-07-09", ProfileCalculationMapper.from(p, LocalDate.of(2018, 7, 9)).observationDate);
        assertEquals("invalid legacy date", p.observationDate);
    }

    @Test public void alreadyZeroAtStartAndUnsupportedDurationsStayBounded() {
        Profile p = profile();
        p.leaArrestDate = p.commitmentDate;
        p.minDays = 1;
        p.minYears = 0;
        p.maxDays = 1;
        p.maxYears = 0;
        p.stalPercent = 40; // Existing rounding gives one STAL day for a one-day penalty.
        LocalDate start = LocalDate.of(2018, 6, 20);
        SentenceProjection.Range r = SentenceProjection.create(p, start);
        assertEquals(start, r.maximumCompletion);
        assertEquals(1, r.dates.size());
        p = profile();
        p.maxYears = 10000;
        try {
            SentenceProjection.create(p, start);
            fail("A projection outside the calculator domain must not be invented.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("supported projection range"));
        }
    }

    @Test public void leaIsPreviewableAndOnlyTheRecordedTransferGetsAMarker() {
        Profile p = profile();
        // Retired date values must never silently manufacture a second transfer.
        p.jailCommitmentDate = "2018-02-01";
        p.prisonCommitmentDate = "2019-01-01";
        SentenceProjection.Range r = SentenceProjection.create(p, LocalDate.of(2020, 1, 1));
        assertEquals(LocalDate.of(2018, 1, 10), r.start);
        assertTrue(r.dates.contains(LocalDate.of(2018, 2, 10)));
        assertEquals(4, r.milestones.size());
        assertEquals(SentenceProjection.Kind.LEA_ARREST, r.milestones.get(0).kind);
        assertEquals(SentenceProjection.Kind.JAIL_COMMITMENT, r.milestones.get(1).kind);
        assertEquals(r.commitment, r.milestones.get(1).date);
        p.disqualifiedForCPI = true;
        r = SentenceProjection.create(p, LocalDate.of(2020, 1, 1));
        assertEquals(SentenceProjection.Kind.PRISON_COMMITMENT, r.milestones.get(1).kind);
    }

    @Test public void absentArrestStartsAtCommitmentWithoutInventingAnArrestMarker() {
        Profile p = profile();
        p.leaArrestDate = "";
        SentenceProjection.Range r = SentenceProjection.create(p, LocalDate.of(2020, 1, 1));
        assertEquals(r.commitment, r.start);
        assertEquals(3, r.milestones.size());
        assertFalse(r.milestones.stream().anyMatch(m -> m.kind == SentenceProjection.Kind.LEA_ARREST));
    }

    @Test public void completionCanFallInsideLeaAndCoincidentMinimumMaximumShareAStop() {
        Profile p = profile();
        p.minYears = p.maxYears = 0;
        p.minDays = p.maxDays = 10;
        SentenceProjection.Range r = SentenceProjection.create(p, LocalDate.of(2020, 1, 1));
        assertEquals(LocalDate.of(2018, 1, 20), r.minimumCompletion);
        assertEquals(r.minimumCompletion, r.maximumCompletion);
        assertEquals(1L, r.dates.stream().filter(d -> d.equals(r.minimumCompletion)).count());
        assertTrue(r.milestones.stream().anyMatch(m -> m.kind == SentenceProjection.Kind.MINIMUM_ZERO));
        assertTrue(r.milestones.stream().anyMatch(m -> m.kind == SentenceProjection.Kind.MAXIMUM_ZERO));
    }

    @Test public void futureConvictionDiscontinuityDoesNotHideAnEarlierLeaZero() {
        Profile p = profile();
        p.minYears = p.maxYears = 0;
        p.minDays = 10;
        p.maxDays = 20;
        p.disqualifiedForTimeAllowances = true;
        p.convictionDate = "2020-01-01";
        SentenceProjection.Range r = SentenceProjection.create(p, LocalDate.of(2020, 1, 1));
        assertEquals(LocalDate.of(2018, 1, 20), r.minimumCompletion);
        assertEquals(LocalDate.of(2018, 1, 30), r.maximumCompletion);
        assertTrue(result(p, r.commitment).maxRemainingDays > 0);
    }
}
