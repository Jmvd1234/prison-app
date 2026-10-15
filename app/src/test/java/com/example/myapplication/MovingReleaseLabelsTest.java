package com.example.myapplication;

import com.example.myapplication.db.Profile;
import org.junit.Test;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import static org.junit.Assert.*;

public class MovingReleaseLabelsTest {
    private Profile profile() {
        Profile p = new Profile();
        p.leaArrestDate = "2024-01-01";
        p.commitmentDate = "2024-02-01";
        p.minYears = 1;
        p.maxYears = 3;
        return p;
    }

    private SentenceProjection.ReleaseDates preview(Profile p, SentenceProjection.Range range, LocalDate date) {
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, date));
        assertTrue(result.problem, result.available);
        return SentenceProjection.releaseDates(range, date, result);
    }

    @Test public void initialEstimatesFitOnTheAxisAndMoveTowardFixedZeros() {
        Profile p = profile();
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2024, 2, 1));
        SentenceProjection.ReleaseDates first = preview(p, range, range.start);
        assertEquals(first.maximum, range.end);
        assertTrue(range.maximumCompletion.isBefore(range.end));
        float fixedMinimum = range.positionOf(range.minimumCompletion);
        float fixedMaximum = range.positionOf(range.maximumCompletion);
        SentenceProjection.ReleaseDates later = preview(p, range, range.commitment.plusMonths(1));
        assertTrue(range.positionOf(later.minimum) < range.positionOf(first.minimum));
        assertTrue(range.positionOf(later.maximum) < range.positionOf(first.maximum));
        assertEquals(fixedMinimum, range.positionOf(range.minimumCompletion), 0);
        assertEquals(fixedMaximum, range.positionOf(range.maximumCompletion), 0);
        assertEquals(fixedMinimum, range.positionOf(preview(p, range, range.minimumCompletion).minimum), 0);
        assertEquals(fixedMaximum, range.positionOf(preview(p, range, range.maximumCompletion).maximum), 0);
    }

    @Test public void intermediateDatesInterpolateWithoutChangingMonthlyStops() {
        SentenceProjection.Range range = SentenceProjection.create(profile(), LocalDate.of(2024, 2, 1));
        int size = range.dates.size();
        for (int i = 0; i < size; i++) assertEquals((float) i / (size - 1), range.positionOf(range.dates.get(i)), 0.000001);
        for (int i = 0; i < size - 1; i++) {
            LocalDate left = range.dates.get(i), right = range.dates.get(i + 1);
            long days = ChronoUnit.DAYS.between(left, right);
            if (days < 2) continue;
            LocalDate middle = left.plusDays(days / 2);
            assertTrue(range.positionOf(middle) > range.positionOf(left));
            assertTrue(range.positionOf(middle) < range.positionOf(right));
        }
        assertEquals(size, range.dates.size());
        assertEquals(0, range.positionOf(range.start.minusDays(1)), 0);
        assertEquals(1, range.positionOf(range.end.plusDays(1)), 0);
    }

    @Test public void retiredConvictionCannotCreateNegativeCreditsOrMoveTheAxis() {
        Profile p = profile();
        p.disqualifiedForTimeAllowances = true;
        p.convictionDate = "2025-02-01";
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2024, 2, 1));
        LocalDate atArrest = preview(p, range, range.start).maximum;
        LocalDate atCommitment = preview(p, range, range.commitment).maximum;
        assertEquals(atArrest, atCommitment);
        assertEquals(atCommitment, range.end);
    }

    @Test public void twoMovingEstimatesCanConvergeOnOneCreditDay() {
        Profile p = profile();
        p.leaArrestDate = p.commitmentDate = "2024-01-01";
        p.minYears = p.maxYears = 0;
        p.minDays = 40;
        p.maxDays = 50;
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.of(2024, 1, 1));
        assertEquals(range.minimumCompletion, range.maximumCompletion);
        SentenceProjection.ReleaseDates first = preview(p, range, range.start);
        assertNotEquals(first.minimum, first.maximum);
        assertTrue(range.positionOf(first.minimum) < range.positionOf(first.maximum));
        SentenceProjection.ReleaseDates done = preview(p, range, range.maximumCompletion);
        assertEquals(done.minimum, done.maximum);
    }

    @Test public void todayRemainsTheEndWhenLaterThanEveryEstimate() {
        LocalDate today = LocalDate.of(2030, 1, 1);
        SentenceProjection.Range range = SentenceProjection.create(profile(), today);
        assertEquals(today, range.end);
        assertEquals(1, range.positionOf(today), 0);
    }
}
