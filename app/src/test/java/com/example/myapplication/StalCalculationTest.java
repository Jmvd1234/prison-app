package com.example.myapplication;

import org.junit.Test;
import java.time.LocalDate;
import java.util.Arrays;
import com.example.myapplication.db.Profile;
import static org.junit.Assert.*;

public class StalCalculationTest {
    private SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2024-12-01";
        in.commitmentDate = "2025-01-01";
        in.observationDate = "2026-01-01";
        in.minYears = 3;
        in.maxYears = 5;
        return in;
    }

    @Test public void matchesNineLiveLayaResults() {
        // Captured from https://layacalculator.org/calculator/ on 2026-10-06.
        // Observation, STAL %, deductions, gap, CPI, GCTA, TASTM, STAL days,
        // minimum/maximum remaining days and release dates. All other inputs above.
        String[][] cases = {
            {"2026-01-01","0","0","0","false","240","180","0","279","1010","2026-10-07","2028-10-07"},
            {"2026-01-01","20","0","0","false","240","180","366","-87","644","2025-10-06","2027-10-07"},
            {"2026-01-01","40","0","0","false","240","180","731","-452","279","2024-10-06","2026-10-07"},
            {"2026-01-01","40","50","17","false","240","180","731","-385","346","2024-12-12","2026-12-13"},
            {"2026-01-01","20","50","17","false","240","180","366","-20","711","2025-12-12","2027-12-13"},
            {"2026-02-01","20","50","17","false","260","195","366","-86","645","2025-11-07","2027-11-08"},
            {"2026-02-01","20","50","17","true","0","0","366","369","1100","2027-02-05","2029-02-05"},
            {"2026-02-01","40","50","17","true","0","0","731","4","735","2026-02-05","2028-02-06"},
            {"2026-02-01","0","50","17","true","0","0","0","735","1466","2028-02-06","2030-02-06"}
        };
        for (String[] row : cases) {
            SentenceCalculator.Input in = input();
            in.observationDate = row[0]; in.stalPercent = Integer.parseInt(row[1]);
            in.deductionDays = Long.parseLong(row[2]); in.timeGapDays = Long.parseLong(row[3]);
            in.disqualifiedForCPI = Boolean.parseBoolean(row[4]);
            SentenceCalculator.Result r = SentenceCalculator.calculate(in);
            assertTrue(Arrays.toString(row) + ": " + r.problem, r.available);
            assertEquals(1826, r.maxSentenceDays);
            assertEquals(in.stalPercent, r.stalPercent);
            assertArrayEquals(Arrays.toString(row), new long[]{Long.parseLong(row[5]),
                    Long.parseLong(row[6]), Long.parseLong(row[7]), Long.parseLong(row[8]), Long.parseLong(row[9])},
                    new long[]{r.gctaDays,r.tastmDays,r.stalDays,r.minRemainingDays,r.maxRemainingDays});
            LocalDate date = LocalDate.parse(in.observationDate);
            assertEquals(row[10], date.plusDays(r.minRemainingDays).toString());
            assertEquals(row[11], date.plusDays(r.maxRemainingDays).toString());
        }
    }

    @Test public void oneFixedCreditReducesBothBalancesInEveryTriggerCombination() {
        for (int flags = 0; flags < 8; flags++) {
            SentenceCalculator.Input in = input();
            in.notCommittedToJail = (flags & 1) != 0;
            in.disqualifiedForCPI = (flags & 2) != 0;
            in.disqualifiedForTimeAllowances = (flags & 4) != 0;
            in.convictionDate = "2025-07-01";
            in.deductionDays = 50; in.timeGapDays = 17;
            SentenceCalculator.Result none = SentenceCalculator.calculate(in);
            for (int percent : new int[]{20,40}) {
                in.stalPercent = percent;
                SentenceCalculator.Result r = SentenceCalculator.calculate(in);
                assertTrue(r.problem, r.available);
                assertEquals(percent == 20 ? 366 : 731, r.stalDays);
                assertEquals(none.minRemainingDays - r.stalDays, r.minRemainingDays);
                assertEquals(none.maxRemainingDays - r.stalDays, r.maxRemainingDays);
                assertEquals(none.gctaDays, r.gctaDays);
                assertEquals(none.tastmDays, r.tastmDays);
            }
        }
    }

    @Test public void article70CapsAreAppliedBeforeTheStalPercentage() {
        SentenceCalculator.Input in = input();
        SentenceCalculator.Penalty small = new SentenceCalculator.Penalty(0,0,100,0,0,200);
        in.charges = Arrays.asList(small,small,small,small);
        in.stalPercent = 40;
        SentenceCalculator.Result r = SentenceCalculator.calculate(in);
        assertTrue(r.problem, r.available);
        assertEquals(600, r.maxSentenceDays);
        assertEquals(240, r.stalDays); // Not 40% of the uncapped total of 800.
        SentenceCalculator.Penalty large = new SentenceCalculator.Penalty(10,0,0,30,0,0);
        in.charges = Arrays.asList(large,large);
        r = SentenceCalculator.calculate(in);
        assertTrue(r.problem, r.available);
        assertEquals(14610, r.maxSentenceDays);
        assertEquals(5844, r.stalDays);
    }

    @Test public void creditStaysFixedAcrossCustodyAndDatePreviews() {
        SentenceCalculator.Input in = input();
        in.stalPercent = 20;
        for (String date : new String[]{"2024-12-01","2024-12-20","2025-01-01","2026-01-01","2035-01-01"}) {
            in.observationDate = date;
            SentenceCalculator.Result r = SentenceCalculator.calculatePreview(in);
            assertTrue(r.problem, r.available);
            assertEquals(366, r.stalDays);
        }
    }

    @Test public void bothTimelineCompletionDatesIncludeTheAwardOnce() {
        Profile p = new Profile();
        p.leaArrestDate = "2024-12-01"; p.commitmentDate = "2025-01-01";
        p.minYears = 3; p.maxYears = 5; p.stalPercent = 40;
        LocalDate today = LocalDate.of(2026,1,1);
        SentenceProjection.Range range = SentenceProjection.create(p, today);
        assertNotNull(range);
        // Compare the timeline's search to an independent daily scan of its calculator.
        LocalDate minimum = null, maximum = null;
        for (LocalDate date = LocalDate.parse(p.leaArrestDate); !date.isAfter(today.plusYears(5)); date = date.plusDays(1)) {
            SentenceCalculator.Result r = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, date));
            assertEquals(731, r.stalDays);
            if (minimum == null && r.minRemainingDays <= 0) minimum = date;
            if (r.maxRemainingDays <= 0) { maximum = date; break; }
        }
        assertEquals(minimum, range.minimumCompletion);
        assertEquals(maximum, range.maximumCompletion);
    }

    @Test public void unsupportedPercentagesAreRejected() {
        for (int percent : new int[]{-20,1,30,100}) {
            SentenceCalculator.Input in = input(); in.stalPercent = percent;
            assertFalse(SentenceCalculator.calculate(in).available);
        }
    }
}
