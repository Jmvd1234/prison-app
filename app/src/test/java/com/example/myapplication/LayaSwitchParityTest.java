package com.example.myapplication;

import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LayaSwitchParityTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() throws Exception {
        List<Object[]> rows=new ArrayList<>();
        for (LayaSwitchFixture f:LayaSwitchFixture.all()) rows.add(new Object[]{f});
        return rows;
    }
    private final LayaSwitchFixture fixture;
    public LayaSwitchParityTest(LayaSwitchFixture fixture) { this.fixture=fixture; }
    @Test public void individualModeMatchesLiveLayaThroughCalculatorAndSavedProfile() {
        fixture.assertMatches(SentenceCalculator.calculate(fixture.input()));
        fixture.assertMatches(SentenceCalculator.calculate(ProfileCalculationMapper.from(fixture.profile(),fixture.observation())));
    }
}
