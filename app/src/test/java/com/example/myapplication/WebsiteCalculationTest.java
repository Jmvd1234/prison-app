package com.example.myapplication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Expected results come from the website responses/screenshots, not this implementation. */
@RunWith(Parameterized.class)
public class WebsiteCalculationTest {
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> cases() throws Exception {
        List<Object[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                WebsiteCalculationTest.class.getResourceAsStream("/website-calculator-cases.tsv"),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("name\t") || line.isEmpty()) continue;
                String[] cells = line.split("\t", -1);
                rows.add(new Object[]{cells[0], cells});
            }
        }
        return rows;
    }

    private final String[] row;
    public WebsiteCalculationTest(String name, String[] row) { this.row = row; }

    @Test public void matchesCapturedWebsiteResult() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.observationDate = row[1]; in.leaArrestDate = row[2];
        in.commitmentDate = row[3]; in.convictionDate = row[4];
        in.minYears = number(5); in.minMonths = number(6); in.minDays = number(7);
        in.maxYears = number(8); in.maxMonths = number(9); in.maxDays = number(10);
        in.notCommittedToJail = checked(11); in.disqualifiedForCPI = checked(12);
        in.disqualifiedForTimeAllowances = checked(13); in.stalPercent = number(14);
        in.deductionDays = number(15); in.timeGapDays = number(16);
        SentenceCalculator.Result r = SentenceCalculator.calculate(in);
        assertTrue(r.problem, r.available);
        assertEquals(number(17), r.completedImprisonmentMonths);
        assertEquals(number(18), r.gctaDays);
        assertEquals(number(19), r.tastmDays);
        assertEquals(number(20), r.stalDays);
        SentenceCalculator.CustodyBreakdown b = r.custodyBreakdown;
        assertEquals(number(18), b.total.gctaDays);
        assertEquals(number(19), b.total.tastmDays);
        assertEquals(r.actualPreventiveDays + r.actualServiceDays, b.total.days);
        assertEquals(b.lea.days + b.jail.days + b.prison.days, b.total.days);
        assertEquals(b.lea.gctaDays + b.jail.gctaDays + b.prison.gctaDays, b.total.gctaDays);
        assertEquals(b.lea.tastmDays + b.jail.tastmDays + b.prison.tastmDays, b.total.tastmDays);
        assertEquals(number(21), r.minRemainingDays);
        assertEquals(number(22), r.maxRemainingDays);
        LocalDate observation = LocalDate.parse(in.observationDate);
        assertEquals(row[23], observation.plusDays(r.minRemainingDays).toString());
        assertEquals(row[24], observation.plusDays(r.maxRemainingDays).toString());
    }

    private int number(int column) { return Integer.parseInt(row[column]); }
    private boolean checked(int column) { return "on".equals(row[column]); }
}
