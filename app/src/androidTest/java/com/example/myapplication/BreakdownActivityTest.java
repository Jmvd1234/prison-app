package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import static androidx.test.espresso.Espresso.pressBack;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BreakdownActivityTest {
    private Context context;
    private AppDatabase db;
    private SharedPreferences session;
    private User testUser;
    private int ordinaryID, cpiID;
    private boolean hadSession;
    private long oldUserID;

    @Before public void seedOnlySyntheticProfiles() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = AppDatabase.getInstance(context);
        session = context.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        hadSession = session.contains("currentUserID");
        oldUserID = session.getLong("currentUserID", -1L);
        testUser = new User();
        testUser.username = "breakdown-test-" + UUID.randomUUID();
        testUser.password = "synthetic-test-only";
        testUser.userID = (int) db.UserDao().insertUser(testUser);
        ordinaryID = insertProfile("Alex", "Rivera", false);
        cpiID = insertProfile("Blair", "Torres", true);
        assertTrue(session.edit().putLong("currentUserID", testUser.userID).commit());
    }

    private int insertProfile(String first, String last, boolean cpi) {
        Profile p = new Profile();
        p.userID = testUser.userID;
        p.firstName = first;
        p.lastName = last;
        p.observationDate = "2020-02-20";
        p.leaArrestDate = "2018-01-10";
        p.commitmentDate = "2018-06-20";
        p.minYears = 10;
        p.maxYears = 15;
        p.disqualifiedForCPI = cpi;
        p.disqualifiedForTimeAllowances = cpi;
        return (int) db.ProfileDao().insertProfile(p);
    }

    @After public void restoreSessionAndRemoveOnlyTestRecords() {
        if (session != null) {
            SharedPreferences.Editor editor = session.edit();
            if (hadSession) editor.putLong("currentUserID", oldUserID);
            else editor.remove("currentUserID");
            assertTrue(editor.commit());
        }
        if (testUser != null && testUser.userID > 0) db.UserDao().deleteUser(testUser);
    }

    @Test public void sortedRowButtonOpensThatProfileAndBackReturnsToDashboard() throws Exception {
        try (ActivityScenario<DashboardActivity> scenario = ActivityScenario.launch(DashboardActivity.class)) {
            await(DashboardActivity.class, activity -> activity.profileModels.size() == 2);
            scenario.onActivity(activity -> {
                activity.sortDropdown.setSelection(2); // Minimum balance.
                activity.orderDropdown.setSelection(1); // Descending: CPI profile comes first.
            });
            await(DashboardActivity.class, activity -> activity.profileModels.size() == 2
                    && activity.profileModels.get(0).getProfileID() == cpiID
                    && ((RecyclerView) activity.findViewById(R.id.profileRecyclerView))
                    .findViewHolderForAdapterPosition(0) != null);
            screenshot("dashboard-breakdown-button.png");
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.profileRecyclerView);
                View row = list.findViewHolderForAdapterPosition(0).itemView;
                View button = row.findViewById(R.id.expandBreakdownBox);
                assertTrue(button instanceof MaterialButton);
                assertTrue(button.isClickable());
                button.performClick();
            });
            await(BreakdownActivity.class, activity -> {
                if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE) return false;
                assertEquals(cpiID, activity.getIntent().getIntExtra(BreakdownActivity.EXTRA_PROFILE_ID, -1));
                assertText(activity, R.id.breakdownProfileName, "Blair Torres");
                assertText(activity, R.id.jailDaysValue, "\u2014");
                assertText(activity, R.id.prisonDaysValue, NumberFormat.getIntegerInstance().format(ChronoUnit.DAYS.between(LocalDate.of(2018, 6, 20), LocalDate.now())));
                assertText(activity, R.id.totalGctaValue, "0");
                SentenceProjection.Range range = SentenceProjection.create(
                        db.ProfileDao().getProfileByProfileID(cpiID), LocalDate.now());
                assertTrue(((TextView) activity.findViewById(R.id.breakdownTimeline)
                        .findViewWithTag(range.commitment.toString())).getText().toString().contains("Prison entry"));
                return true;
            });
            screenshot("activity-breakdown-cpi.png");
            pressBack();
            await(DashboardActivity.class, activity -> activity.profileModels.size() == 2);
            assertEquals(2, db.ProfileDao().getProfilesByUserID(testUser.userID).size());
        }
    }

    @Test public void monthlyPreviewRestoresResetsAndNeverChangesStoredProfile() throws Exception {
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            awaitTodayResult();
            screenshot("activity-slider-today.png");
            previewHistoricalMonth(scenario);
            awaitOrdinaryResult();
            screenshot("activity-slider-preview.png");
            scenario.recreate();
            awaitOrdinaryResult();
            scenario.onActivity(activity -> activity.findViewById(R.id.breakdownTodayButton).performClick());
            awaitTodayResult();
            previewHistoricalMonth(scenario);
            awaitOrdinaryResult();
        }
        try (ActivityScenario<BreakdownActivity> reopened = ActivityScenario.launch(intent)) {
            awaitTodayResult();
        }
        Profile unchanged = db.ProfileDao().getProfileByProfileID(ordinaryID);
        assertEquals("2020-02-20", unchanged.observationDate);
        assertEquals("2018-06-20", unchanged.commitmentDate);
        assertEquals(0, unchanged.deductionDays);
    }

    @Test public void releaseDatesMoveWithCreditsPinAtZeroAndRewind() throws Exception {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        LocalDate first = LocalDate.of(2019, 1, 20), next = first.plusMonths(1);
        SentenceCalculator.Result initial = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, first));
        LocalDate firstMinimum = first.plusDays(initial.minRemainingDays);
        LocalDate firstMaximum = first.plusDays(initial.maxRemainingDays);
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            awaitTodayResult();
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                slider.setValue(range.dates.indexOf(first));
                assertText(activity, R.id.breakdownMinimumReleaseDate, firstMinimum.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMaximumReleaseDate, firstMaximum.format(SentenceCalculator.DISPLAY_DATE));
                slider.setValue(range.dates.indexOf(next));
                assertText(activity, R.id.breakdownMinimumReleaseDate, firstMinimum.minusDays(35).format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMaximumReleaseDate, firstMaximum.minusDays(35).format(SentenceCalculator.DISPLAY_DATE));
            });
            screenshot("release-dates-moving.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.breakdownTimeline)
                    .findViewWithTag(range.maximumCompletion.toString()).performClick());
            scenario.recreate();
            await(BreakdownActivity.class, activity -> {
                if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE) return false;
                assertText(activity, R.id.breakdownMinimumReleaseDate, range.minimumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMaximumReleaseDate, range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.minimumSentenceValue, context.getString(R.string.breakdown_zero_balance));
                assertText(activity, R.id.maximumSentenceValue, context.getString(R.string.breakdown_zero_balance));
                return true;
            });
            screenshot("release-dates-zero.png");
            scenario.onActivity(activity -> {
                ((Slider) activity.findViewById(R.id.breakdownDateSlider)).setValue(range.dates.indexOf(first));
                assertText(activity, R.id.breakdownMinimumReleaseDate, firstMinimum.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMinimumReleaseState, context.getString(R.string.breakdown_release_estimate));
            });
        }
        assertEquals("2020-02-20", db.ProfileDao().getProfileByProfileID(ordinaryID).observationDate);
    }

    private void previewHistoricalMonth(ActivityScenario<BreakdownActivity> scenario) {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        int index = SentenceProjection.create(p, LocalDate.now()).dates.indexOf(LocalDate.of(2020, 2, 20));
        assertTrue(index >= 0);
        scenario.onActivity(activity -> ((Slider) activity.findViewById(R.id.breakdownDateSlider)).setValue(index));
    }

    @Test public void timelineLabelsJumpToLeaTransferAndBothCompletionDates() throws Exception {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            awaitTodayResult();
            scenario.onActivity(activity -> {
                ViewGroup timeline = activity.findViewById(R.id.breakdownTimeline);
                assertLabelsDoNotOverlap(timeline);
                assertTrue(((TextView) timeline.findViewWithTag(range.commitment.toString()))
                        .getText().toString().contains("Jail entry"));
                timeline.findViewWithTag(range.start.toString()).performClick();
                assertText(activity, R.id.leaDaysValue, "0");
                assertText(activity, R.id.jailDaysValue, "0");
                assertText(activity, R.id.totalDaysValue, "0");
            });
            screenshot("timeline-lea-start.png");
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                slider.setValue(range.dates.indexOf(LocalDate.of(2018, 2, 10)));
                assertText(activity, R.id.leaDaysValue, "31");
                assertText(activity, R.id.jailDaysValue, "0");
                assertText(activity, R.id.totalGctaValue, "0");
                assertText(activity, R.id.totalTastmValue, "0");
                ViewGroup timeline = activity.findViewById(R.id.breakdownTimeline);
                timeline.findViewWithTag(range.commitment.toString()).performClick();
                assertEquals(range.dates.indexOf(range.commitment), Math.round(slider.getValue()));
                assertText(activity, R.id.leaDaysValue, "161");
                assertText(activity, R.id.jailDaysValue, "0");
                timeline.findViewWithTag(range.minimumCompletion.toString()).performClick();
                assertEquals(range.dates.indexOf(range.minimumCompletion), Math.round(slider.getValue()));
                SentenceCalculator.Result minimum = SentenceCalculator.calculatePreview(
                        ProfileCalculationMapper.from(p, range.minimumCompletion));
                assertTrue(minimum.minRemainingDays <= 0);
                assertText(activity, R.id.minimumSentenceValue, context.getString(R.string.breakdown_zero_balance));
                assertText(activity, R.id.breakdownMinimumReleaseDate,
                        range.minimumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMinimumReleaseState, context.getString(R.string.breakdown_zero_reached));
            });
            screenshot("timeline-minimum-zero.png");
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.breakdownTimeline)
                        .findViewWithTag(range.maximumCompletion.toString()).performClick();
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                assertEquals(range.dates.indexOf(range.maximumCompletion), Math.round(slider.getValue()));
                SentenceCalculator.Result maximum = SentenceCalculator.calculatePreview(
                        ProfileCalculationMapper.from(p, range.maximumCompletion));
                assertTrue(maximum.maxRemainingDays <= 0);
                assertText(activity, R.id.maximumSentenceValue, context.getString(R.string.breakdown_zero_balance));
                assertText(activity, R.id.breakdownMaximumReleaseDate,
                        range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMaximumReleaseState, context.getString(R.string.breakdown_zero_reached));
            });
        }
        assertEquals("2020-02-20", db.ProfileDao().getProfileByProfileID(ordinaryID).observationDate);
    }

    @Test public void coincidentMarkersRemainReadableOnNarrowAndRtlLayouts() throws Exception {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        p.leaArrestDate = p.commitmentDate;
        p.minYears = p.maxYears = 3;
        p.charges.clear(); // This fixture intentionally replaces the existing charge with the form values.
        db.ProfileDao().updateUser(p);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            await(BreakdownActivity.class, activity ->
                    activity.findViewById(R.id.breakdownCard).getVisibility() == View.VISIBLE);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                ViewGroup timeline = activity.findViewById(R.id.breakdownTimeline);
                assertEquals(4, timeline.getChildCount()); // Slider, grouped custody, two release labels.
                assertTrue(((TextView) timeline.findViewWithTag(range.start.toString()))
                        .getText().toString().contains("LEA arrest / Jail entry"));
                assertTrue(((TextView) timeline.findViewById(R.id.timelineMinimumReleaseLabel))
                        .getText().toString().contains("Minimum zero"));
                assertTrue(((TextView) timeline.findViewById(R.id.timelineMaximumReleaseLabel))
                        .getText().toString().contains("Maximum zero"));
                assertLabelsDoNotOverlap(timeline);
            });
            screenshot("timeline-coincident.png");
            scenario.onActivity(activity -> {
                ViewGroup timeline = activity.findViewById(R.id.breakdownTimeline);
                for (int i = 1; i < timeline.getChildCount(); i++) {
                    ((TextView) timeline.getChildAt(i)).setTextSize(22);
                }
                int width = Math.round(240 * context.getResources().getDisplayMetrics().density);
                for (int direction : new int[]{View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL}) {
                    timeline.setLayoutDirection(direction);
                    timeline.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    timeline.layout(0, 0, width, timeline.getMeasuredHeight());
                    assertLabelsDoNotOverlap(timeline);
                }
            });
        }
    }

    @Test public void releaseLabelsActuallyMoveOnTheTrackAndConvergeAtZero() throws Exception {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        LocalDate first = LocalDate.of(2019, 1, 20), later = LocalDate.of(2021, 1, 20);
        int[] earlierLeft = new int[2];
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            awaitTodayResult();
            scenario.onActivity(activity -> ((Slider) activity.findViewById(R.id.breakdownDateSlider))
                    .setValue(range.dates.indexOf(first)));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                ViewGroup timeline = activity.findViewById(R.id.breakdownTimeline);
                earlierLeft[0] = timeline.findViewById(R.id.timelineMinimumReleaseLabel).getLeft();
                earlierLeft[1] = timeline.findViewById(R.id.timelineMaximumReleaseLabel).getLeft();
                assertTrue(((TextView) timeline.findViewById(R.id.timelineMinimumReleaseLabel))
                        .getText().toString().startsWith("Minimum estimate"));
                assertLabelsDoNotOverlap(timeline);
                ((Slider) activity.findViewById(R.id.breakdownDateSlider)).setValue(range.dates.indexOf(later));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                ViewGroup timeline = activity.findViewById(R.id.breakdownTimeline);
                assertTrue(timeline.findViewById(R.id.timelineMinimumReleaseLabel).getLeft() < earlierLeft[0]);
                assertTrue(timeline.findViewById(R.id.timelineMaximumReleaseLabel).getLeft() < earlierLeft[1]);
                assertLabelsDoNotOverlap(timeline);
            });
            screenshot("moving-labels-estimates.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.timelineMinimumReleaseLabel).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertEquals(range.dates.indexOf(range.minimumCompletion),
                        Math.round(((Slider) activity.findViewById(R.id.breakdownDateSlider)).getValue()));
                assertText(activity, R.id.timelineMinimumReleaseLabel,
                        "Minimum zero\n" + range.minimumCompletion.format(SentenceCalculator.DISPLAY_DATE));
            });
            screenshot("moving-labels-minimum-zero.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.timelineMaximumReleaseLabel).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertText(activity, R.id.timelineMaximumReleaseLabel,
                        "Maximum zero\n" + range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertLabelsDoNotOverlap(activity.findViewById(R.id.breakdownTimeline));
            });
            screenshot("moving-labels-both-zero.png");
        }
        assertEquals("2020-02-20", db.ProfileDao().getProfileByProfileID(ordinaryID).observationDate);
    }

    private void assertLabelsDoNotOverlap(ViewGroup timeline) {
        for (int i = 1; i < timeline.getChildCount(); i++) {
            View label = timeline.getChildAt(i);
            assertTrue(label.getLeft() >= 0);
            assertTrue(label.getRight() <= timeline.getWidth());
            assertTrue(label.getHeight() >= 48 * context.getResources().getDisplayMetrics().density - 1);
            Rect bounds = new Rect(label.getLeft(), label.getTop(), label.getRight(), label.getBottom());
            for (int j = i + 1; j < timeline.getChildCount(); j++) {
                View other = timeline.getChildAt(j);
                assertFalse(Rect.intersects(bounds, new Rect(other.getLeft(), other.getTop(),
                        other.getRight(), other.getBottom())));
            }
        }
    }

    private void awaitTodayResult() {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        LocalDate today = LocalDate.now();
        SentenceCalculator.Result r = SentenceCalculator.calculate(ProfileCalculationMapper.from(p, today));
        await(BreakdownActivity.class, activity -> {
            if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE
                    || activity.findViewById(R.id.breakdownPreviewControls).getVisibility() != View.VISIBLE) return false;
            assertText(activity, R.id.breakdownObservationDate, context.getString(R.string.breakdown_observation_today,
                    today.format(SentenceCalculator.DISPLAY_DATE)));
            SentenceProjection.Range range = SentenceProjection.create(p, today);
            assertText(activity, R.id.minimumSentenceValue, !today.isBefore(range.minimumCompletion)
                    ? context.getString(R.string.breakdown_zero_balance)
                    : context.getString(R.string.breakdown_balance_value, r.minimumText(today), r.minRemainingDays));
            assertText(activity, R.id.totalDaysValue, NumberFormat.getIntegerInstance().format(
                    ChronoUnit.DAYS.between(LocalDate.of(2018, 1, 10), today)));
            return true;
        });
    }

    @Test public void savingNewProfileUsesTodayWithoutStoringAnObservationDate() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> activity.make_new_entry(null));
            await(newEntryActivity.class, activity -> {
                ((EditText) activity.findViewById(R.id.inputFirstName)).setText("Casey");
                ((EditText) activity.findViewById(R.id.inputLastName)).setText("Park");
                ((EditText) activity.findViewById(R.id.inputLEADate)).setText("Jan 10, 2018");
                ((EditText) activity.findViewById(R.id.inputCommitmentDate)).setText("Jun 20, 2018");
                ((EditText) activity.findViewById(R.id.inputMinYears)).setText("10");
                ((EditText) activity.findViewById(R.id.inputMaxYears)).setText("15");
                activity.findViewById(R.id.saveEntryButton).performClick();
                return true;
            });
            Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
            LocalDate today = LocalDate.now();
            SentenceCalculator.Result r = SentenceCalculator.calculate(ProfileCalculationMapper.from(p, today));
            await(HomeActivity.class, activity -> {
                ProfileBreakdownDialogFragment dialog = (ProfileBreakdownDialogFragment) activity
                        .getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
                if (dialog == null || dialog.getDialog() == null) return false;
                TextView minimum = dialog.requireDialog().findViewById(R.id.minimumSentenceValue);
                assertEquals(context.getString(R.string.breakdown_balance_value, r.minimumText(today),
                        r.minRemainingDays), minimum.getText().toString());
                return true;
            });
        }
        Profile created = db.ProfileDao().getProfilesByUserID(testUser.userID).stream()
                .filter(p -> "Casey".equals(p.firstName)).findFirst().orElseThrow(AssertionError::new);
        assertNull(created.observationDate);
    }

    @Test public void editingLegacyProfilePreservesItsOldSnapshotButUsesToday() {
        Intent intent = new Intent(context, editProfileActivity.class).putExtra("profileID", ordinaryID);
        try (ActivityScenario<editProfileActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.inputFirstName)).setText("Updated");
                activity.findViewById(R.id.saveEntryButton).performClick();
            });
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (scenario.getState() != androidx.lifecycle.Lifecycle.State.DESTROYED
                    && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
            assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario.getState());
        }
        Profile edited = db.ProfileDao().getProfileByProfileID(ordinaryID);
        assertEquals("Updated", edited.firstName);
        assertEquals("2020-02-20", edited.observationDate);
    }
    @Test public void missingProfileShowsAnErrorInsteadOfInventedZeroValues() {
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, Integer.MAX_VALUE);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            await(BreakdownActivity.class, activity -> {
                TextView status = activity.findViewById(R.id.breakdownStatus);
                if (!status.getText().toString().equals(context.getString(R.string.breakdown_profile_missing))) return false;
                assertEquals(View.GONE, activity.findViewById(R.id.breakdownCard).getVisibility());
                return true;
            });
        }
    }

    private void awaitOrdinaryResult() {
        await(BreakdownActivity.class, activity -> {
            if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE) return false;
            assertText(activity, R.id.breakdownProfileName, "Alex Rivera");
            assertText(activity, R.id.leaDaysValue, "161");
            assertText(activity, R.id.jailDaysValue, "610");
            assertText(activity, R.id.prisonDaysValue, "\u2014");
            assertText(activity, R.id.totalDaysValue, "771");
            assertText(activity, R.id.totalGctaValue, "400");
            assertText(activity, R.id.totalTastmValue, "300");
            assertText(activity, R.id.minimumSentenceValue, "5y, 11m, 21d (2182 days)");
            assertText(activity, R.id.maximumSentenceValue, "10y, 11m, 21d (4008 days)");
            LocalDate preview = LocalDate.of(2020, 2, 20);
            assertText(activity, R.id.breakdownMinimumReleaseDate,
                    preview.plusDays(2182).format(SentenceCalculator.DISPLAY_DATE));
            assertText(activity, R.id.breakdownMaximumReleaseDate,
                    preview.plusDays(4008).format(SentenceCalculator.DISPLAY_DATE));
            return true;
        });
    }

    private interface Check<T> { boolean ready(T activity); }

    private <T extends Activity> void await(Class<T> type, Check<T> check) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicBoolean ready = new AtomicBoolean(false);
        while (!ready.get() && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(activity)) ready.set(check.ready(type.cast(activity)));
                }
            });
            if (!ready.get()) SystemClock.sleep(50);
        }
        assertTrue("Timed out waiting for " + type.getSimpleName(), ready.get());
    }

    private void assertText(Activity activity, int id, String expected) {
        assertEquals(expected, ((TextView) activity.findViewById(id)).getText().toString());
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5000);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = context.getExternalFilesDir("test-screenshots");
        assertNotNull(directory);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { image.recycle(); }
    }
}
