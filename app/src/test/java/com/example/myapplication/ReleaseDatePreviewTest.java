package com.example.myapplication;

import com.example.myapplication.db.Profile;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class ReleaseDatePreviewTest {
    private Profile profile() {
        Profile p = new Profile();
        p.leaArrestDate = p.commitmentDate = "2024-01-01";
        p.minYears = 1;
        p.maxYears = 3;
        return p;
    }

    private SentenceCalculator.Result result(Profile p, LocalDate date) {
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, date));
        assertTrue(result.problem, result.available);
        return result;
    }

    private SentenceProjection.ReleaseDates dates(Profile p, SentenceProjection.Range range, LocalDate date) {
        return SentenceProjection.releaseDates(range, date, result(p, date));
    }

    @Test public void datesMoveWithEarnedCreditsRatherThanForecastingFutureCredits() {
        Profile p = profile();
        LocalDate start = LocalDate.of(2024, 1, 1);
        SentenceProjection.Range range = SentenceProjection.create(p, start);
        SentenceProjection.ReleaseDates initial = dates(p, range, start);
        assertEquals(LocalDate.of(2025, 1, 1), initial.minimum);
        assertEquals(LocalDate.of(2027, 1, 1), initial.maximum);
        SentenceProjection.ReleaseDates beforeCredit = dates(p, range, start.plusDays(30));
        assertEquals(initial.minimum, beforeCredit.minimum);
        assertEquals(initial.maximum, beforeCredit.maximum);
        SentenceProjection.ReleaseDates afterCredit = dates(p, range, start.plusMonths(1));
        assertEquals(initial.minimum.minusDays(35), afterCredit.minimum); // 20 GCTA + 15 TASTM.
        assertEquals(initial.maximum.minusDays(35), afterCredit.maximum);
        assertTrue(afterCredit.minimum.isAfter(range.minimumCompletion));
        assertFalse(afterCredit.minimumReached);
        assertFalse(afterCredit.maximumReached);
    }

    @Test public void monthlyCreditJumpPinsTheFirstZeroWithoutBackdatingIt() {
        Profile p = profile();
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2024, 1, 1));
        LocalDate zero = range.minimumCompletion;
        assertTrue(result(p, zero.minusDays(1)).minRemainingDays > 0);
        assertTrue("Fixture must exercise a credit jump past zero", result(p, zero).minRemainingDays < 0);
        assertFalse(dates(p, range, zero.minusDays(1)).minimumReached);
        SentenceProjection.ReleaseDates atZero = dates(p, range, zero);
        assertEquals(zero, atZero.minimum);
        assertTrue(atZero.minimumReached);
        assertFalse(atZero.maximumReached);
        SentenceProjection.ReleaseDates later = dates(p, range, zero.plusMonths(1));
        assertEquals(zero, later.minimum);
        assertTrue(later.maximum.isBefore(atZero.maximum));
    }

    @Test public void bothDatesStayFixedAfterCompletionAndRewindRestoresEstimates() {
        Profile p = profile();
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2026, 10, 10));
        SentenceProjection.ReleaseDates later = dates(p, range, range.maximumCompletion.plusYears(1));
        assertEquals(range.minimumCompletion, later.minimum);
        assertEquals(range.maximumCompletion, later.maximum);
        assertTrue(later.minimumReached && later.maximumReached);
        SentenceProjection.ReleaseDates rewound = dates(p, range, range.start);
        assertFalse(rewound.minimumReached || rewound.maximumReached);
        assertEquals(LocalDate.of(2025, 1, 1), rewound.minimum);
    }

    @Test public void excludedAllowancesKeepDatesStableUntilZero() {
        Profile p = profile();
        p.disqualifiedForCPI = true;
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2024, 1, 1));
        SentenceProjection.ReleaseDates initial = dates(p, range, range.start);
        SentenceProjection.ReleaseDates later = dates(p, range, range.start.plusMonths(3));
        assertEquals(initial.minimum, later.minimum);
        assertEquals(initial.maximum, later.maximum);
        assertEquals(range.minimumCompletion, initial.minimum);
        assertTrue(dates(p, range, range.minimumCompletion).minimumReached);
    }

    @Test public void leaPreviewDoesNotBorrowFutureJailCredits() {
        Profile p = profile();
        p.leaArrestDate = "2023-11-01";
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2024, 1, 1));
        SentenceProjection.ReleaseDates arrest = dates(p, range, range.start);
        SentenceProjection.ReleaseDates transfer = dates(p, range, range.commitment);
        assertEquals(arrest.minimum, transfer.minimum);
        assertEquals(arrest.maximum, transfer.maximum);
        assertEquals(0, result(p, range.start.plusMonths(1)).gctaDays);
    }

    @Test public void coincidentZeroAndStalAlreadyCoveringMinimumAreIndependent() {
        Profile p = profile();
        p.minYears = p.maxYears;
        SentenceProjection.Range equal = SentenceProjection.create(p, LocalDate.of(2024, 1, 1));
        SentenceProjection.ReleaseDates atZero = dates(p, equal, equal.minimumCompletion);
        assertEquals(atZero.minimum, atZero.maximum);
        assertTrue(atZero.minimumReached && atZero.maximumReached);
        p.minYears = 1;
        p.stalPercent = 40;
        SentenceProjection.Range stal = SentenceProjection.create(p, LocalDate.of(2024, 1, 1));
        SentenceProjection.ReleaseDates initial = dates(p, stal, stal.start);
        assertEquals(stal.start, initial.minimum);
        assertTrue(initial.minimumReached);
        assertFalse(initial.maximumReached);
    }

    @Test public void missingProjectionStillShowsObservationBasedDates() {
        Profile p = profile();
        SentenceProjection.ReleaseDates preview = dates(p, null, LocalDate.of(2024, 2, 1));
        assertEquals(LocalDate.of(2025, 1, 1).minusDays(35), preview.minimum);
        assertFalse(preview.minimumReached || preview.maximumReached);
    }
}
