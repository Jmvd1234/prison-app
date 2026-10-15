package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.junit.Assert.*;

/** Expected numbers are captured website responses, not recomputed expectations. */
final class LayaSwitchFixture {
    final String[] f;
    LayaSwitchFixture(String[] f) { this.f = f; }
    String mode() { return f[1]; }
    LocalDate observation() { return LocalDate.parse(f[2]); }
    static List<LayaSwitchFixture> all() throws IOException {
        InputStream stream = LayaSwitchFixture.class.getResourceAsStream("/laya-switches-2026-10-14.tsv");
        if (stream == null) throw new FileNotFoundException("Missing live LAYA switch fixtures");
        List<LayaSwitchFixture> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("#") && !line.isEmpty()) rows.add(new LayaSwitchFixture(line.split("\t", -1)));
            }
        }
        assertEquals(35, rows.size());
        return rows;
    }
    SentenceCalculator.Input input() {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.observationDate=f[2]; in.leaArrestDate=f[3]; in.commitmentDate=f[4]; in.convictionDate=f[5];
        in.minYears=n(6); in.minMonths=n(7); in.minDays=n(8);
        in.maxYears=n(9); in.maxMonths=n(10); in.maxDays=n(11);
        in.stalPercent=n(12); in.deductionDays=n(13); in.timeGapDays=n(14);
        in.notCommittedToJail=mode().equals("not-jail");
        in.disqualifiedForCPI=mode().equals("cpi");
        in.disqualifiedForTimeAllowances=mode().startsWith("ta-");
        return in;
    }
    Profile profile() {
        SentenceCalculator.Input in = input(); Profile p = new Profile();
        p.separateCustodyDates=true; p.leaArrestDate=in.leaArrestDate; p.commitmentDate=in.commitmentDate;
        p.notCommittedToJail=in.notCommittedToJail; p.disqualifiedForCPI=in.disqualifiedForCPI;
        p.disqualifiedForTimeAllowances=in.disqualifiedForTimeAllowances || in.disqualifiedForCPI;
        if (in.notCommittedToJail || in.disqualifiedForCPI) {
            p.prisonCommitmentDate=p.prisonTransferDate=in.commitmentDate;
        } else {
            p.jailCommitmentDate=in.commitmentDate;
            if (!in.convictionDate.isEmpty()) p.prisonCommitmentDate=p.prisonTransferDate=in.convictionDate;
        }
        p.minYears=in.minYears; p.minMonths=in.minMonths; p.minDays=in.minDays;
        p.maxYears=in.maxYears; p.maxMonths=in.maxMonths; p.maxDays=in.maxDays;
        p.stalPercent=in.stalPercent; p.deductionDays=Math.toIntExact(in.deductionDays); p.timeGapDays=Math.toIntExact(in.timeGapDays);
        return p;
    }
    void assertMatches(SentenceCalculator.Result r) {
        assertTrue(toString()+": "+r.problem, r.available);
        assertEquals("GCTA",n(15),r.gctaDays); assertEquals("TASTM",n(16),r.tastmDays);
        assertEquals("STAL",n(17),r.stalDays);
        assertEquals("minimum balance",n(18),r.minRemainingDays);
        assertEquals("maximum balance",n(19),r.maxRemainingDays);
        assertEquals(f[20],observation().plusDays(r.minRemainingDays).toString());
        assertEquals(f[21],observation().plusDays(r.maxRemainingDays).toString());
    }
    private int n(int i) { return Integer.parseInt(f[i]); }
    @Override public String toString() { return f[0]; }
}
