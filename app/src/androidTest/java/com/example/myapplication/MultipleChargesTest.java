package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import com.example.myapplication.db.User;
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
public class MultipleChargesTest {
    private Context context;
    private AppDatabase db;
    private SharedPreferences session;
    private User user;
    private long oldUser;
    private boolean hadUser;

    @Before public void seedSyntheticUser() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = AppDatabase.getInstance(context);
        session = context.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        hadUser = session.contains("currentUserID");
        oldUser = session.getLong("currentUserID", -1L);
        user = new User();
        user.username = "multi-charge-" + UUID.randomUUID();
        user.password = "synthetic-only";
        user.userID = (int) db.UserDao().insertUser(user);
        assertTrue(session.edit().putLong("currentUserID", user.userID).commit());
    }

    @After public void cleanup() {
        SharedPreferences.Editor editor = session.edit();
        if (hadUser) editor.putLong("currentUserID", oldUser); else editor.remove("currentUserID");
        assertTrue(editor.commit());
        db.UserDao().deleteUser(user);
    }

    @Test public void addEntrySavesEveryChargeAndAllOutputsUseTheCombinedSentence() {
        try (ActivityScenario<HomeActivity> home = ActivityScenario.launch(HomeActivity.class)) {
            home.onActivity(activity -> activity.make_new_entry(null));
            await(newEntryActivity.class, activity -> {
                activity.inputFirstName.setText("Multiple");
                activity.inputLastName.setText("Charges");
                ((EditText) activity.findViewById(R.id.inputCommitmentDate)).setText("Jan 1, 2020");
                activity.inputMinDays.setText("100");
                activity.inputMaxDays.setText("200");
                LinearLayout rows = activity.findViewById(R.id.additionalChargesContainer);
                for (int i = 0; i < 3; i++) {
                    activity.findViewById(R.id.addChargeButton).performClick();
                    ((EditText) rows.getChildAt(i).findViewById(R.id.chargeMinDays)).setText("100");
                    ((EditText) rows.getChildAt(i).findViewById(R.id.chargeMaxDays)).setText("200");
                }
                activity.saveEntryButton.performClick();
                return true;
            });
            await(HomeActivity.class, activity -> activity.getSupportFragmentManager()
                    .findFragmentByTag(ProfileBreakdownDialogFragment.TAG) != null);
            Profile saved = db.ProfileDao().getProfilesByUserID(user.userID).get(0);
            assertEquals(4, saved.charges.size());
            SentenceCalculator.Result result = SentenceCalculator.calculate(ProfileCalculationMapper.from(saved));
            assertEquals(300, result.minSentenceDays);
            assertEquals(600, result.maxSentenceDays);
            home.onActivity(activity -> {
                ProfileBreakdownDialogFragment dialog = (ProfileBreakdownDialogFragment) activity
                        .getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
                assertEquals(balance(result, true), ((TextView) dialog.requireDialog()
                        .findViewById(R.id.minimumSentenceValue)).getText().toString());
                dialog.dismiss();
            });
            try (ActivityScenario<DashboardActivity> dashboard = ActivityScenario.launch(DashboardActivity.class)) {
                await(DashboardActivity.class, activity -> {
                    if (activity.profileModels.size() != 1) return false;
                    assertEquals(SentenceCalculator.exactDays(result.maxRemainingFifths),
                            activity.profileModels.get(0).getMaximumExactDays());
                    return true;
                });
            }
            try (ActivityScenario<BreakdownActivity> screen = ActivityScenario.launch(new Intent(context,
                    BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID, saved.profileID))) {
                await(BreakdownActivity.class, activity -> {
                    if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE) return false;
                    assertEquals(balance(result, false), ((TextView) activity.findViewById(R.id.maximumSentenceValue)).getText().toString());
                    return true;
                });
            }
        }
    }

    @Test public void editRestoresChargesAndRotatedRemovalsStayRemovedWhenSaved() throws Exception {
        Profile p = new Profile();
        p.userID = user.userID;
        p.firstName = "Edit"; p.lastName = "Charges"; p.commitmentDate = "2020-01-01";
        for (int years = 2; years <= 4; years++) {
            ProfileCharge charge = new ProfileCharge();
            charge.minYears = years; charge.maxYears = years * 2;
            p.charges.add(charge);
        }
        int id = (int) db.ProfileDao().insertProfile(p);
        Intent intent = new Intent(context, editProfileActivity.class).putExtra("profileID", id);
        try (ActivityScenario<editProfileActivity> edit = ActivityScenario.launch(intent)) {
            edit.onActivity(activity -> {
                assertEquals("2", activity.inputMinYears.getText().toString());
                LinearLayout rows = activity.findViewById(R.id.additionalChargesContainer);
                assertEquals(2, rows.getChildCount());
                rows.getChildAt(0).findViewById(R.id.removeChargeButton).performClick();
                activity.inputMinYears.setText("1");
                activity.findViewById(R.id.addChargeButton).performClick();
                ((EditText) rows.getChildAt(1).findViewById(R.id.chargeMinYears)).setText("5");
                ((EditText) rows.getChildAt(1).findViewById(R.id.chargeMaxYears)).setText("10");
            });
            edit.recreate();
            edit.onActivity(activity -> {
                LinearLayout rows = activity.findViewById(R.id.additionalChargesContainer);
                assertEquals("1", activity.inputMinYears.getText().toString());
                assertEquals("4", ((EditText) rows.getChildAt(0).findViewById(R.id.chargeMinYears)).getText().toString());
                assertEquals("5", ((EditText) rows.getChildAt(1).findViewById(R.id.chargeMinYears)).getText().toString());
            });
            screenshot("edit-multiple-charges.png");
            edit.onActivity(activity -> activity.saveEntryButton.performClick());
            awaitClosed(edit);
        }
        Profile updated = db.ProfileDao().getProfileByProfileID(id);
        assertEquals(3, updated.charges.size());
        assertEquals(1, updated.charges.get(0).minYears);
        assertEquals(4, updated.charges.get(1).minYears);
        assertEquals(5, updated.charges.get(2).minYears);
        try (ActivityScenario<editProfileActivity> edit = ActivityScenario.launch(intent)) {
            edit.onActivity(activity -> {
                LinearLayout rows = activity.findViewById(R.id.additionalChargesContainer);
                while (rows.getChildCount() > 0) rows.getChildAt(0).findViewById(R.id.removeChargeButton).performClick();
            });
            edit.recreate();
            edit.onActivity(activity -> {
                assertEquals(0, ((LinearLayout) activity.findViewById(R.id.additionalChargesContainer)).getChildCount());
                activity.saveEntryButton.performClick();
            });
            awaitClosed(edit);
        }
        assertEquals(1, db.ProfileDao().getProfileByProfileID(id).charges.size());
    }

    private String balance(SentenceCalculator.Result result, boolean minimum) {
        LocalDate today = LocalDate.now();
        return context.getString(R.string.breakdown_balance_value,
                minimum ? result.minimumText(today) : result.maximumText(today),
                minimum ? result.minRemainingDays : result.maxRemainingDays);
    }

    private void awaitClosed(ActivityScenario<?> scenario) {
        long end = SystemClock.uptimeMillis() + 10000;
        while (scenario.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED && SystemClock.uptimeMillis() < end) SystemClock.sleep(50);
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.getState());
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
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5000);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = context.getExternalFilesDir("test-screenshots");
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { image.recycle(); }
    }
}
