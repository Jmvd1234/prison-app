package com.example.myapplication;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import com.google.android.material.slider.Slider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StalFlowTest {
    private Context context;
    private AppDatabase db;
    private SharedPreferences session;
    private User user;
    private long oldUser;
    private boolean hadUser;

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = AppDatabase.getInstance(context);
        session = context.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        hadUser = session.contains("currentUserID"); oldUser = session.getLong("currentUserID", -1);
        user = new User(); user.username = "stal-test-" + UUID.randomUUID(); user.password = "synthetic-only";
        user.userID = (int) db.UserDao().insertUser(user);
        assertTrue(session.edit().putLong("currentUserID", user.userID).commit());
    }

    @After public void cleanup() {
        SharedPreferences.Editor editor = session.edit();
        if (hadUser) editor.putLong("currentUserID", oldUser); else editor.remove("currentUserID");
        assertTrue(editor.commit());
        db.UserDao().deleteUser(user);
    }

    @Test public void selectionPersistsAndIsShownInPopupDashboardAndTimeline() throws Exception {
        int profileID;
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            home.onActivity(activity -> activity.make_new_entry(null));
            await(newEntryActivity.class, activity -> {
                activity.inputFirstName.setText("STAL"); activity.inputLastName.setText("Example");
                activity.inputMinYears.setText("3"); activity.inputMaxYears.setText("5");
                activity.inputLEADate.setText("Dec 1, 2024");
                ((EditText) activity.findViewById(R.id.inputCommitmentDate)).setText("Jan 1, 2025");
                activity.inputSTAL.setSelection(2);
                activity.saveEntryButton.performClick();
                return true;
            });
            await(HomeActivity.class, activity -> {
                ProfileBreakdownDialogFragment fragment = (ProfileBreakdownDialogFragment) activity
                        .getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
                return fragment != null && fragment.getDialog() != null && fragment.requireDialog().isShowing();
            });
            Profile saved = db.ProfileDao().getProfilesByUserID(user.userID).get(0);
            profileID = saved.profileID;
            assertEquals(40, saved.stalPercent);
            home.onActivity(activity -> {
                ProfileBreakdownDialogFragment fragment = (ProfileBreakdownDialogFragment) activity
                        .getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
                Dialog dialog = fragment.requireDialog();
                assertEquals("STAL (40%)", ((TextView) dialog.findViewById(R.id.stalCreditLabel)).getText().toString());
                assertEquals("731 days", ((TextView) dialog.findViewById(R.id.stalCreditValue)).getText().toString());
            });
            screenshot("stal-save-popup.png");
        }
        verifyOutputs(profileID, 40, "731 days", true);
        for (int selection : new int[]{1,0}) {
            Intent editIntent = new Intent(context, editProfileActivity.class).putExtra("profileID", profileID);
            try (ActivityScenario<editProfileActivity> edit = ActivityScenario.launch(editIntent)) {
                edit.onActivity(activity -> {
                    assertEquals(selection + 1, activity.inputSTAL.getSelectedItemPosition());
                    activity.inputSTAL.setSelection(selection);
                });
                edit.recreate();
                edit.onActivity(activity -> {
                    assertEquals(selection, activity.inputSTAL.getSelectedItemPosition());
                    activity.saveEntryButton.performClick();
                });
                long end = SystemClock.uptimeMillis() + 10000;
                while (edit.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < end) SystemClock.sleep(50);
                assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, edit.getState());
            }
            verifyOutputs(profileID, selection * 20, selection == 0 ? "0 days" : "366 days", false);
        }
    }

    private void verifyOutputs(int id, int percent, String credit, boolean capture) throws Exception {
        Profile saved = db.ProfileDao().getProfileByProfileID(id);
        assertEquals(percent, saved.stalPercent);
        SentenceCalculator.Result today = SentenceCalculator.calculate(ProfileCalculationMapper.from(saved));
        try (ActivityScenario<DashboardActivity> dashboard = ActivityScenario.launch(DashboardActivity.class)) {
            await(DashboardActivity.class, activity -> {
                if (activity.profileModels.size() != 1) return false;
                assertEquals(SentenceCalculator.exactDays(today.maxRemainingFifths), activity.profileModels.get(0).getMaximumExactDays());
                return true;
            });
        }
        Intent intent = new Intent(context, BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID, id);
        try (ActivityScenario<BreakdownActivity> screen = ActivityScenario.launch(intent)) {
            await(BreakdownActivity.class, activity -> activity.findViewById(R.id.breakdownCard).getVisibility() == View.VISIBLE);
            SentenceProjection.Range range = SentenceProjection.create(saved, LocalDate.now());
            screen.onActivity(activity -> {
                assertText(activity, R.id.stalCreditLabel, percent == 0 ? "STAL (None)" : "STAL (" + percent + "%)");
                assertText(activity, R.id.stalCreditValue, credit);
                for (LocalDate date : new LocalDate[]{range.start,range.minimumCompletion,range.maximumCompletion}) {
                    ((Slider) activity.findViewById(R.id.breakdownDateSlider)).setValue(range.dates.indexOf(date));
                    assertText(activity, R.id.stalCreditValue, credit);
                    SentenceCalculator.Result r = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(saved,date));
                    assertText(activity, R.id.maximumSentenceValue, context.getString(R.string.breakdown_balance_value,
                            r.maximumText(date), r.maxRemainingDays));
                }
                View card = activity.findViewById(R.id.breakdownCard);
                card.requestRectangleOnScreen(new Rect(0,0,card.getWidth(),card.getHeight()), true);
            });
            if (capture) screenshot("stal-full-breakdown.png");
        }
        assertEquals(percent, db.ProfileDao().getProfileByProfileID(id).stalPercent);
    }

    private void assertText(Activity activity, int id, String expected) {
        assertEquals(expected, ((TextView) activity.findViewById(id)).getText().toString());
    }
    private interface Check<T> { boolean ready(T activity); }
    private <T extends Activity> void await(Class<T> type, Check<T> check) {
        long end = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean ready = new AtomicBoolean();
        while (!ready.get() && SystemClock.uptimeMillis() < end) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(activity)) ready.set(check.ready(type.cast(activity)));
                }
            });
            if (!ready.get()) SystemClock.sleep(50);
        }
        assertTrue("Timed out waiting for " + type.getSimpleName(), ready.get());
    }
    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500,5000);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = context.getExternalFilesDir("test-screenshots");
        try (FileOutputStream stream = new FileOutputStream(new File(directory,name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));
        } finally { image.recycle(); }
    }
}
