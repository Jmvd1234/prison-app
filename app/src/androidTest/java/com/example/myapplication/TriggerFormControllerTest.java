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
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

@RunWith(AndroidJUnit4.class)
public class TriggerFormControllerTest {
    private EditText conviction(newEntryActivity activity) {
        LinearLayout details = activity.findViewById(R.id.triggerDetailsContainer);
        return (EditText) ((LinearLayout) details.getChildAt(1)).getChildAt(0);
    }

    @Test public void bothLayoutsLoadAndSaveAllTriggerCombinations() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> {
                for (int layout : new int[]{R.layout.activity_new_entry, R.layout.activity_edit_profile}) {
                    for (int bits = 0; bits < 8; bits++) {
                        activity.setContentView(layout);
                        Profile p = new Profile();
                        p.notCommittedToJail = (bits & 1) != 0;
                        p.disqualifiedForCPI = (bits & 2) != 0;
                        p.disqualifiedForTimeAllowances = (bits & 4) != 0;
                        p.commitmentDate = "Nov 13, 2025";
                        p.convictionDate = "May 13, 2026";
                        LinearLayout details = activity.findViewById(R.id.triggerDetailsContainer);
                        TriggerFormController controller = new TriggerFormController(activity, details);
                        controller.load(p, null);
                        boolean prison = p.notCommittedToJail || p.disqualifiedForCPI;
                        assertEquals(prison ? "Prison Commitment Date" : "Jail Commitment Date",
                                ((TextView) activity.findViewById(R.id.commitmentDateLabel)).getText().toString());
                        assertEquals(p.disqualifiedForTimeAllowances && !p.disqualifiedForCPI
                                ? View.VISIBLE : View.GONE, details.getVisibility());
                        assertEquals(2, details.getChildCount()); // Label and one date row only.
                        assertEquals("Date of conviction, if any", ((TextView) details.getChildAt(0)).getText());
                        MaterialSwitch ta = activity.findViewById(R.id.disqualifiedTASwitch);
                        assertEquals(p.disqualifiedForCPI || p.disqualifiedForTimeAllowances, ta.isChecked());
                        assertEquals(!p.disqualifiedForCPI, ta.isEnabled());
                        Profile saved = new Profile();
                        controller.writeToProfile(saved);
                        assertEquals(p.commitmentDate, saved.commitmentDate);
                        assertEquals(p.convictionDate, saved.convictionDate);
                        assertEquals(p.disqualifiedForCPI || p.disqualifiedForTimeAllowances,
                                saved.disqualifiedForTimeAllowances);
                        assertEquals(p.disqualifiedForTimeAllowances && !p.disqualifiedForCPI
                                        ? p.convictionDate : null,
                                ProfileCalculationMapper.from(saved).convictionDate);
                    }
                }
            });
        }
    }

    @Test public void cancellingCpiLeavesCommitmentAndTriggersUnchanged() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.inputCommitmentDate)).setText("Nov 13, 2025");
                activity.findViewById(R.id.disqualifiedCPISwitch).performClick();
            });
            onView(withText("Cancel")).perform(click());
            scenario.onActivity(activity -> {
                assertFalse(((MaterialSwitch) activity.findViewById(R.id.disqualifiedCPISwitch)).isChecked());
                assertFalse(((MaterialSwitch) activity.findViewById(R.id.disqualifiedTASwitch)).isChecked());
                assertEquals("Nov 13, 2025", ((EditText) activity.findViewById(R.id.inputCommitmentDate)).getText().toString());
                assertEquals(View.GONE, activity.findViewById(R.id.triggerDetailsContainer).getVisibility());
            });
        }
    }

    @Test public void confirmedCpiAndDateSurviveRecreationAndUnlockTaWhenCleared() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.inputCommitmentDate)).setText("Nov 13, 2025");
                activity.findViewById(R.id.disqualifiedCPISwitch).performClick();
            });
            onView(withText("Confirmed")).perform(click());
            scenario.recreate();
            scenario.onActivity(activity -> {
                MaterialSwitch cpi = activity.findViewById(R.id.disqualifiedCPISwitch);
                MaterialSwitch ta = activity.findViewById(R.id.disqualifiedTASwitch);
                assertTrue(cpi.isChecked());
                assertTrue(ta.isChecked());
                assertFalse(ta.isEnabled());
                assertEquals(View.GONE, activity.findViewById(R.id.triggerDetailsContainer).getVisibility());
                assertEquals("Nov 13, 2025", ((EditText) activity.findViewById(R.id.inputCommitmentDate)).getText().toString());
                cpi.performClick();
                assertTrue(ta.isChecked());
                assertTrue(ta.isEnabled());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.triggerDetailsContainer).getVisibility());
                ta.performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.triggerDetailsContainer).getVisibility());
            });
        }
    }

    @Test public void taConvictionDraftSurvivesHidingAndRecreation() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.disqualifiedTASwitch).performClick());
            onView(withText("Confirmed")).perform(click());
            scenario.onActivity(activity -> {
                conviction(activity).setText("May 13, 2026");
                activity.findViewById(R.id.disqualifiedTASwitch).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.triggerDetailsContainer).getVisibility());
            });
            scenario.recreate();
            scenario.onActivity(activity -> activity.findViewById(R.id.disqualifiedTASwitch).performClick());
            onView(withText("Confirmed")).perform(click());
            scenario.onActivity(activity -> {
                assertEquals("May 13, 2026", conviction(activity).getText().toString());
                LinearLayout details = activity.findViewById(R.id.triggerDetailsContainer);
                ((LinearLayout) details.getChildAt(1)).getChildAt(1).performClick();
                assertEquals("", conviction(activity).getText().toString());
            });
        }
    }

    @Test public void notJailOnlyRelabelsTheEnteredDate() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> {
                EditText date = activity.findViewById(R.id.inputCommitmentDate);
                date.setText("Nov 13, 2025");
                activity.findViewById(R.id.committedSwitch).performClick();
                assertEquals("Prison Commitment Date", ((TextView) activity.findViewById(R.id.commitmentDateLabel)).getText());
                assertEquals(View.GONE, activity.findViewById(R.id.triggerDetailsContainer).getVisibility());
                assertEquals("Nov 13, 2025", date.getText().toString());
                activity.findViewById(R.id.committedSwitch).performClick();
                assertEquals("Jail Commitment Date", ((TextView) activity.findViewById(R.id.commitmentDateLabel)).getText());
                assertEquals("Nov 13, 2025", date.getText().toString());
            });
        }
    }

    @Test public void openCommitmentPickerReconnectsAfterRecreation() {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.inputCommitmentDate).performClick());
            scenario.recreate();
            onView(withId(com.google.android.material.R.id.confirm_button)).perform(click());
            scenario.onActivity(activity -> assertFalse(
                    ((EditText) activity.findViewById(R.id.inputCommitmentDate)).getText().toString().isEmpty()));
        }
    }
}
