package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.myapplication.db.Profile;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class TriggerFormControllerTest {
    private EditText date(newEntryActivity a, int id) { return a.findViewById(id); }

    @Test public void bothLayoutsLoadAndSaveAllTriggerCombinations() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(a -> {
                for (int layout : new int[]{R.layout.activity_new_entry, R.layout.activity_edit_profile}) {
                    for (int bits = 0; bits < 8; bits++) {
                        a.setContentView(layout);
                        Profile p = new Profile(); p.separateCustodyDates = true;
                        p.notCommittedToJail = (bits & 1) != 0; p.disqualifiedForCPI = (bits & 2) != 0;
                        p.disqualifiedForTimeAllowances = (bits & 4) != 0;
                        p.jailCommitmentDate = "Jun 20, 2018"; p.prisonTransferDate = "Mar 10, 2019";
                        boolean prison = p.notCommittedToJail || p.disqualifiedForCPI;
                        p.commitmentDate = prison ? p.prisonTransferDate : p.jailCommitmentDate;
                        LinearLayout details = a.findViewById(R.id.triggerDetailsContainer);
                        TriggerFormController controller = new TriggerFormController(a, details); controller.load(p, null);
                        assertEquals(!p.notCommittedToJail, date(a,R.id.inputCommitmentDate).isEnabled());
                        assertTrue(date(a,R.id.inputPrisonTransferDate).isEnabled());
                        assertTrue(a.findViewById(R.id.clearPrisonTransferDate).isEnabled());
                        assertEquals(p.disqualifiedForCPI && !p.notCommittedToJail ? "Jail Commitment Date, if any" : "Jail Commitment Date",
                                ((TextView)a.findViewById(R.id.commitmentDateLabel)).getText().toString());
                        assertEquals(prison ? "Prison Commitment Date" : "Prison Commitment Date, if any",
                                ((TextView)a.findViewById(R.id.prisonTransferLabel)).getText().toString());
                        assertEquals(View.GONE, details.getVisibility()); assertEquals(0, details.getChildCount());
                        MaterialSwitch ta = a.findViewById(R.id.disqualifiedTASwitch);
                        assertEquals(p.disqualifiedForCPI || p.disqualifiedForTimeAllowances, ta.isChecked());
                        assertEquals(!p.disqualifiedForCPI, ta.isEnabled());
                        Profile saved = new Profile(); controller.writeToProfile(saved);
                        assertTrue(saved.separateCustodyDates); assertNull(saved.convictionDate);
                        assertEquals(p.commitmentDate, saved.commitmentDate);
                        assertEquals(p.jailCommitmentDate, saved.jailCommitmentDate);
                        assertEquals(p.prisonTransferDate, saved.prisonTransferDate);
                        assertEquals(ta.isChecked() && !p.notCommittedToJail && !p.disqualifiedForCPI ? p.prisonTransferDate : null,
                                ProfileCalculationMapper.from(saved).convictionDate);
                    }
                }
            });
        }
    }
    @Test public void combinedSwitchesCalculateIdenticallyInEitherToggleOrderAndAfterRestore() {
        for (int layout : new int[]{R.layout.activity_new_entry, R.layout.activity_edit_profile}) {
            for (int disqualification : new int[]{R.id.disqualifiedCPISwitch, R.id.disqualifiedTASwitch}) {
                for (boolean directFirst : new boolean[]{false,true}) {
                    try (ActivityScenario<newEntryActivity> s=ActivityScenario.launch(newEntryActivity.class)) {
                        final TriggerFormController[] controller=new TriggerFormController[1];
                        s.onActivity(a -> {
                            a.setContentView(layout);
                            controller[0]=new TriggerFormController(a,a.findViewById(R.id.triggerDetailsContainer));
                            controller[0].load(null,null);
                            date(a,R.id.inputLEADate).setText("Jan 10, 2018");
                            date(a,R.id.inputCommitmentDate).setText("Feb 20, 2018");
                            date(a,R.id.inputPrisonTransferDate).setText("Jun 20, 2018");
                        });
                        // Wait for the replaced form's window before opening its modal.
                        onView(withId(R.id.main)).check(androidx.test.espresso.assertion.ViewAssertions.matches(
                                androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
                        s.onActivity(a -> {
                            if(directFirst) a.findViewById(R.id.committedSwitch).performClick();
                            a.findViewById(disqualification).performClick();
                        });
                        onView(withText(R.string.trigger_warning_confirm))
                                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
                        s.onActivity(a -> {
                            if(!directFirst) a.findViewById(R.id.committedSwitch).performClick();
                            Profile saved=new Profile(); controller[0].writeToProfile(saved);
                            saved.leaArrestDate="Jan 10, 2018"; saved.minYears=5; saved.maxYears=10;
                            assertTrue(saved.notCommittedToJail);
                            assertEquals(disqualification==R.id.disqualifiedCPISwitch,saved.disqualifiedForCPI);
                            assertTrue(saved.disqualifiedForTimeAllowances);
                            SentenceCalculator.Result r=SentenceCalculator.calculate(ProfileCalculationMapper.from(saved,LocalDate.of(2020,2,20)));
                            assertTrue(r.problem,r.available);
                            // Independent ordinary-not-jail live LAYA fixture (2026-10-14).
                            assertEquals(400,r.gctaDays); assertEquals(300,r.tastmDays);
                            assertEquals(355,r.minRemainingDays); assertEquals(2182,r.maxRemainingDays);
                            Bundle state=new Bundle(); controller[0].saveInstanceState(state);
                            a.setContentView(layout);
                            TriggerFormController restored=new TriggerFormController(a,a.findViewById(R.id.triggerDetailsContainer));
                            restored.load(saved,state);
                            assertFalse(date(a,R.id.inputCommitmentDate).isEnabled());
                            assertTrue(((MaterialSwitch)a.findViewById(disqualification)).isChecked());
                            assertEquals("Jun 20, 2018",date(a,R.id.inputPrisonTransferDate).getText().toString());
                            Profile reopened=new Profile(); restored.writeToProfile(reopened);
                            reopened.leaArrestDate=saved.leaArrestDate; reopened.minYears=5; reopened.maxYears=10;
                            assertEquals(r.maxRemainingDays,SentenceCalculator.calculate(ProfileCalculationMapper.from(reopened,LocalDate.of(2020,2,20))).maxRemainingDays);
                        });
                    }
                }
            }
        }
    }
    @Test public void cancellingCpiLeavesBothDatesAndTriggersUnchanged() {
        try (ActivityScenario<newEntryActivity> s = ActivityScenario.launch(newEntryActivity.class)) {
            s.onActivity(a -> { date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018"); a.findViewById(R.id.disqualifiedCPISwitch).performClick(); });
            onView(withText(R.string.trigger_warning_cancel)).perform(click());
            s.onActivity(a -> {
                assertFalse(((MaterialSwitch)a.findViewById(R.id.disqualifiedCPISwitch)).isChecked());
                assertEquals("Jun 20, 2018",date(a,R.id.inputCommitmentDate).getText().toString());
                assertEquals("",date(a,R.id.inputPrisonTransferDate).getText().toString());
            });
        }
    }
    @Test public void cpiKeepsOptionalJailAndBothDatesAcrossRotation() {
        try (ActivityScenario<newEntryActivity> s = ActivityScenario.launch(newEntryActivity.class)) {
            s.onActivity(a -> { date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018");
                date(a,R.id.inputPrisonTransferDate).setText("Mar 10, 2019"); a.findViewById(R.id.disqualifiedCPISwitch).performClick(); });
            onView(withText(R.string.trigger_warning_confirm)).perform(click()); s.recreate();
            s.onActivity(a -> {
                assertTrue(date(a,R.id.inputCommitmentDate).isEnabled());
                assertEquals("Jun 20, 2018",date(a,R.id.inputCommitmentDate).getText().toString());
                assertEquals("Mar 10, 2019",date(a,R.id.inputPrisonTransferDate).getText().toString());
                assertFalse(a.findViewById(R.id.disqualifiedTASwitch).isEnabled());
                a.findViewById(R.id.disqualifiedCPISwitch).performClick();
                assertTrue(a.findViewById(R.id.disqualifiedTASwitch).isEnabled());
                assertEquals(View.GONE,a.findViewById(R.id.triggerDetailsContainer).getVisibility());
                assertTrue(date(a,R.id.inputCommitmentDate).isEnabled());
            });
        }
    }
    @Test public void taOnlyKeepsNormalFieldsAndNeverAddsConvictionInput() {
        try (ActivityScenario<newEntryActivity> s = ActivityScenario.launch(newEntryActivity.class)) {
            s.onActivity(a -> a.findViewById(R.id.disqualifiedTASwitch).performClick());
            onView(withText(R.string.trigger_warning_confirm)).perform(click()); s.recreate();
            s.onActivity(a -> {
                assertTrue(date(a,R.id.inputCommitmentDate).isEnabled());
                assertTrue(date(a,R.id.inputPrisonTransferDate).isEnabled());
                assertEquals(View.GONE,a.findViewById(R.id.triggerDetailsContainer).getVisibility());
                assertEquals(0,((LinearLayout)a.findViewById(R.id.triggerDetailsContainer)).getChildCount());
            });
        }
    }
    @Test public void notJailDisablesOnlyJailAndKeepsIndependentDrafts() {
        try (ActivityScenario<newEntryActivity> s = ActivityScenario.launch(newEntryActivity.class)) {
            s.onActivity(a -> { date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018");
                date(a,R.id.inputPrisonTransferDate).setText("Mar 10, 2019"); a.findViewById(R.id.committedSwitch).performClick(); });
            s.recreate();
            s.onActivity(a -> {
                assertFalse(date(a,R.id.inputCommitmentDate).isEnabled());
                assertTrue(date(a,R.id.inputPrisonTransferDate).isEnabled());
                assertEquals("Jun 20, 2018",date(a,R.id.inputCommitmentDate).getText().toString());
                assertEquals("Mar 10, 2019",date(a,R.id.inputPrisonTransferDate).getText().toString());
                a.findViewById(R.id.committedSwitch).performClick();
                assertTrue(date(a,R.id.inputCommitmentDate).isEnabled());
            });
        }
    }
    @Test public void openJailAndPrisonPickersReconnectAfterRecreation() {
        for (int id : new int[]{R.id.inputCommitmentDate,R.id.inputPrisonTransferDate}) {
            try (ActivityScenario<newEntryActivity> s = ActivityScenario.launch(newEntryActivity.class)) {
                s.onActivity(a -> a.findViewById(id).performClick()); s.recreate();
                onView(withId(com.google.android.material.R.id.confirm_button)).perform(click());
                s.onActivity(a -> assertFalse(date(a,id).getText().toString().isEmpty()));
            }
        }
    }
}
