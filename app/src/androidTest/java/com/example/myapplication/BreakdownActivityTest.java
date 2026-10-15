package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Paint;
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
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static org.hamcrest.Matchers.allOf;
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
                assertText(activity, R.id.totalGctaValue, "0/0");
                SentenceProjection.Range range = SentenceProjection.create(
                        db.ProfileDao().getProfileByProfileID(cpiID), LocalDate.now());
                assertTrue(((TextView) activity.findViewById(R.id.breakdownTimeline)
                        .findViewWithTag(range.commitment.toString())).getText().toString().contains("Prison commitment"));
                return true;
            });
            screenshot("activity-breakdown-cpi.png");
            pressBack();
            await(DashboardActivity.class, activity -> activity.profileModels.size() == 2);
            assertEquals(2, db.ProfileDao().getProfilesByUserID(testUser.userID).size());
        }
    }

    @Test public void dashboardDeletionUsesConfirmedIdAfterSortAndCascadesOnlyThatEntry() throws Exception {
        Profile original = db.ProfileDao().getProfileByProfileID(ordinaryID);
        db.ProfileDao().saveDeductionMonths(ordinaryID, testUser.userID,
                java.util.Collections.singletonList(new DeductionMonth(2019, 1, true, true)));
        try (ActivityScenario<DashboardActivity> scenario = ActivityScenario.launch(DashboardActivity.class)) {
            await(DashboardActivity.class, activity -> activity.profileModels.size() == 2
                    && ((RecyclerView) activity.findViewById(R.id.profileRecyclerView))
                        .findViewHolderForAdapterPosition(0) != null);
            screenshot("dashboard-entry-actions.png");
            scenario.onActivity(activity -> {
                assertEquals(ordinaryID, activity.profileModels.get(0).getProfileID());
                RecyclerView list = activity.findViewById(R.id.profileRecyclerView);
                list.findViewHolderForAdapterPosition(0).itemView
                        .findViewById(R.id.deleteProfileEntryButton).performClick();
            });
            onView(withText(context.getString(R.string.delete_entry_message, "Alex Rivera")))
                    .check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).perform(click());
            assertNotNull(db.ProfileDao().getProfileByProfileID(ordinaryID));
            scenario.onActivity(activity -> {
                RecyclerView list = activity.findViewById(R.id.profileRecyclerView);
                list.findViewHolderForAdapterPosition(0).itemView
                        .findViewById(R.id.deleteProfileEntryButton).performClick();
                activity.orderDropdown.setSelection(1);
            });
            await(DashboardActivity.class, activity -> activity.profileModels.get(0).getProfileID() == cpiID);
            onView(withId(android.R.id.button1)).perform(click());
            // Rotation while deletion completes must still refresh the new dashboard instance.
            scenario.recreate();
            await(DashboardActivity.class, activity -> activity.profileModels.size() == 1);
            assertNull(db.ProfileDao().getProfileByProfileID(ordinaryID));
            assertNotNull(db.ProfileDao().getProfileByProfileID(cpiID));
            for (String table : new String[]{"ProfileCharge", "ProfileDeduction"}) {
                try (android.database.Cursor rows = db.getOpenHelper().getReadableDatabase().query(
                        "SELECT COUNT(*) FROM " + table + " WHERE profileID = ?", new Object[]{ordinaryID})) {
                    assertTrue(rows.moveToFirst());
                    assertEquals(0, rows.getInt(0));
                }
            }
        }
    }

    @Test public void dailyPreviewRestoresResetsAndNeverChangesStoredProfile() throws Exception {
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
            scenario.onActivity(activity -> activity.findViewById(R.id.timelineTodayLabel).performClick());
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

    @Test public void observationDatePickerSynchronizesValidatesAndRestores() throws Exception {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        LocalDate chosen = LocalDate.of(2024, 2, 29);
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            awaitTodayResult();
            scenario.onActivity(activity -> {
                TextView date = activity.findViewById(R.id.breakdownObservationDate);
                assertTrue((date.getPaintFlags() & Paint.UNDERLINE_TEXT_FLAG) != 0);
                ((Slider) activity.findViewById(R.id.breakdownDateSlider)).setValue(range.dates.indexOf(chosen.minusDays(1)));
            });
            onView(withId(R.id.observationDateHelp)).perform(click());
            onView(withText(R.string.tooltip_observation)).check(matches(isDisplayed()));
            screenshot("observation-date-tooltip.png");
            pressBack();
            onView(withId(R.id.breakdownObservationDate)).perform(click());
            scenario.onActivity(activity -> assertEquals(Long.valueOf(chosen.minusDays(1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()), observationPicker(activity).getSelection()));
            onView(allOf(withText("29"), isDisplayed())).perform(click());
            screenshot("observation-material-picker.png");
            scenario.recreate();
            await(BreakdownActivity.class, activity -> activity.findViewById(R.id.breakdownPreviewControls).getVisibility() == View.VISIBLE);
            scenario.onActivity(activity -> assertEquals(Long.valueOf(chosen.atStartOfDay(ZoneOffset.UTC)
                    .toInstant().toEpochMilli()), observationPicker(activity).getSelection()));
            onView(withId(com.google.android.material.R.id.confirm_button)).perform(click());
            scenario.onActivity(activity -> assertSelectedDate(activity, p, range, chosen));
            screenshot("observation-date-selected.png");
            onView(withId(R.id.breakdownObservationDate)).perform(click());
            onView(allOf(withText("28"), isDisplayed())).perform(click());
            onView(withId(com.google.android.material.R.id.cancel_button)).perform(click());
            scenario.onActivity(activity -> assertSelectedDate(activity, p, range, chosen));
            onView(withId(R.id.breakdownObservationDate)).perform(click());
            onView(withId(com.google.android.material.R.id.mtrl_picker_header_toggle)).perform(click());
            // The test emulator uses en-US; confirm out-of-range typed dates cannot be submitted.
            scenario.onActivity(activity -> {
                MaterialDatePicker<?> picker = observationPicker(activity);
                TextInputLayout field = picker.requireView().findViewById(com.google.android.material.R.id.mtrl_picker_text_input_date);
                field.getEditText().setText("01/01/2010");
                assertFalse(picker.requireView().findViewById(com.google.android.material.R.id.confirm_button).isEnabled());
                field.getEditText().setText("01/01/2099");
                assertFalse(picker.requireView().findViewById(com.google.android.material.R.id.confirm_button).isEnabled());
                field.getEditText().setText("02/29/2024");
                assertTrue(picker.requireView().findViewById(com.google.android.material.R.id.confirm_button).isEnabled());
            });
            onView(withId(com.google.android.material.R.id.confirm_button)).perform(click());
            scenario.onActivity(activity -> {
                assertSelectedDate(activity, p, range, chosen);
                activity.findViewById(R.id.timelineTodayLabel).performClick();
                assertSelectedDate(activity, p, range, LocalDate.now());
            });
        }
        assertEquals(p.observationDate, db.ProfileDao().getProfileByProfileID(ordinaryID).observationDate);
    }

    private MaterialDatePicker<?> observationPicker(BreakdownActivity activity) {
        return (MaterialDatePicker<?>) activity.getSupportFragmentManager().findFragmentByTag("breakdown_observation_picker");
    }

    @Test public void dateButtonsUseCalendarJumpsClampAndKeepThePreviewUnsaved() throws Exception {
        Profile p = db.ProfileDao().getProfileByProfileID(ordinaryID);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        Intent intent = new Intent(context, BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID, ordinaryID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            awaitTodayResult();
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                slider.setValue(range.dates.indexOf(LocalDate.of(2024, 1, 31)));
                activity.findViewById(R.id.breakdownMonthForward).performClick();
                assertSelectedDate(activity, p, range, LocalDate.of(2024, 2, 29));
                activity.findViewById(R.id.breakdownYearForward).performClick();
                assertSelectedDate(activity, p, range, LocalDate.of(2025, 2, 28));
                activity.findViewById(R.id.breakdownYearBack).performClick();
                assertSelectedDate(activity, p, range, LocalDate.of(2024, 2, 28));
                activity.findViewById(R.id.breakdownDayForward).performClick();
                assertSelectedDate(activity, p, range, LocalDate.of(2024, 2, 29));
                activity.findViewById(R.id.breakdownDayBack).performClick();
                assertSelectedDate(activity, p, range, LocalDate.of(2024, 2, 28));
                activity.findViewById(R.id.breakdownMonthBack).performClick();
                assertSelectedDate(activity, p, range, LocalDate.of(2024, 1, 28));
                slider.setValue(1);
                activity.findViewById(R.id.breakdownYearBack).performClick();
                assertSelectedDate(activity, p, range, range.start);
                for (int id : new int[]{R.id.breakdownDayBack, R.id.breakdownMonthBack, R.id.breakdownYearBack})
                    assertFalse(activity.findViewById(id).isEnabled());
                slider.setValue(range.dates.size() - 2);
                activity.findViewById(R.id.breakdownMonthForward).performClick();
                assertSelectedDate(activity, p, range, range.end);
                for (int id : new int[]{R.id.breakdownDayForward, R.id.breakdownMonthForward, R.id.breakdownYearForward})
                    assertFalse(activity.findViewById(id).isEnabled());
                activity.findViewById(R.id.timelineTodayLabel).performClick();
                activity.findViewById(R.id.breakdownDayBack).performClick();
                assertSelectedDate(activity, p, range, LocalDate.now().minusDays(1));
            });
            screenshot("date-step-controls.png");
            scenario.recreate();
            await(BreakdownActivity.class, activity -> {
                if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE) return false;
                assertSelectedDate(activity, p, range, LocalDate.now().minusDays(1));
                return true;
            });
        }
        assertEquals(p.observationDate, db.ProfileDao().getProfileByProfileID(ordinaryID).observationDate);
    }

    private void assertSelectedDate(Activity activity, Profile p, SentenceProjection.Range range, LocalDate date) {
        assertEquals(range.dates.indexOf(date), Math.round(((Slider) activity.findViewById(R.id.breakdownDateSlider)).getValue()));
        assertText(activity, R.id.breakdownObservationDate, context.getString(
                date.equals(LocalDate.now()) ? R.string.breakdown_observation_today : R.string.breakdown_observation_preview,
                date.format(SentenceCalculator.DISPLAY_DATE)));
        assertBalances(activity, p, date);
    }

    @Test public void dailySliderCrossesLeapDayWithoutBorrowingFutureCredits() throws Exception {
        Profile p = new Profile();
        p.userID = testUser.userID;
        p.firstName = "Laya"; p.lastName = "Edge";
        p.leaArrestDate = p.commitmentDate = "2024-01-31";
        p.minMonths = 2; p.maxMonths = 3;
        int id = (int) db.ProfileDao().insertProfile(p);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        LocalDate before = LocalDate.of(2024, 2, 28), earned = before.plusDays(1);
        Intent intent = new Intent(context, BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID, id);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            await(BreakdownActivity.class, a -> a.findViewById(R.id.breakdownPreviewControls).getVisibility() == View.VISIBLE
                    && a.findViewById(R.id.breakdownCard).getVisibility() == View.VISIBLE);
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                assertEquals(1f, slider.getStepSize(), 0f);
                assertEquals((float) ChronoUnit.DAYS.between(range.start, range.end), slider.getValueTo(), 0f);
                slider.setValue(range.dates.indexOf(before));
                assertText(activity, R.id.totalGctaValue, "0/0");
                assertText(activity, R.id.totalTastmValue, "0/0");
                assertText(activity, R.id.breakdownMinimumReleaseDate, "Mar 31, 2024");
                assertBalances(activity, p, before);
            });
            screenshot("daily-before-leap-credit.png");
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                slider.setValue(slider.getValue() + slider.getStepSize());
                assertText(activity, R.id.breakdownObservationDate, context.getString(
                        R.string.breakdown_observation_preview, "Feb 29, 2024"));
                assertText(activity, R.id.totalGctaValue, "20/20");
                assertText(activity, R.id.totalTastmValue, "15/15");
                assertText(activity, R.id.breakdownMinimumReleaseDate, "Feb 29, 2024");
                assertText(activity, R.id.breakdownMinimumReleaseState, context.getString(R.string.breakdown_zero_reached));
                assertBalances(activity, p, earned);
            });
            screenshot("daily-leap-credit-reached.png");
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                slider.setValue(slider.getValue() + 1);
                assertText(activity, R.id.breakdownObservationDate, context.getString(
                        R.string.breakdown_observation_preview, "Mar 1, 2024"));
                assertText(activity, R.id.totalGctaValue, "20/20");
                assertBalances(activity, p, earned.plusDays(1));
            });
            scenario.recreate();
            await(BreakdownActivity.class, activity -> {
                if (activity.findViewById(R.id.breakdownCard).getVisibility() != View.VISIBLE) return false;
                assertText(activity, R.id.breakdownObservationDate, context.getString(
                        R.string.breakdown_observation_preview, "Mar 1, 2024"));
                assertText(activity, R.id.breakdownMinimumReleaseDate, "Feb 29, 2024");
                assertEquals(range.dates.indexOf(earned.plusDays(1)),
                        Math.round(((Slider) activity.findViewById(R.id.breakdownDateSlider)).getValue()));
                return true;
            });
            scenario.onActivity(activity -> activity.findViewById(R.id.timelineTodayLabel).performClick());
            await(BreakdownActivity.class, activity -> {
                assertText(activity, R.id.breakdownObservationDate, context.getString(
                        R.string.breakdown_observation_today, LocalDate.now().format(SentenceCalculator.DISPLAY_DATE)));
                return true;
            });
        }
        assertNull(db.ProfileDao().getProfileByProfileID(id).observationDate);
        assertEquals("2024-01-31", db.ProfileDao().getProfileByProfileID(id).commitmentDate);
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
                assertBalances(activity, p, range.maximumCompletion);
                return true;
            });
            screenshot("release-dates-zero.png");
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                int zeroIndex = range.dates.indexOf(range.maximumCompletion);
                LocalDate later = range.dates.get(zeroIndex + 1);
                LocalDate evenLater = range.dates.get(zeroIndex + 2);
                slider.setValue(zeroIndex + 1);
                assertBalances(activity, p, later);
                SentenceCalculator.Result laterResult = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, later));
                assertTrue(laterResult.maxRemainingDays < 0);
                slider.setValue(zeroIndex + 2);
                assertBalances(activity, p, evenLater);
                SentenceCalculator.Result nextResult = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, evenLater));
                assertTrue(nextResult.minRemainingDays < laterResult.minRemainingDays);
                assertTrue(nextResult.maxRemainingDays < laterResult.maxRemainingDays);
                assertText(activity, R.id.breakdownMinimumReleaseDate, range.minimumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMaximumReleaseDate, range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.timelineMinimumReleaseLabel, context.getString(R.string.breakdown_marker_minimum) + "\n" + range.minimumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.timelineMaximumReleaseLabel, context.getString(R.string.breakdown_marker_maximum) + "\n" + range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                reveal(activity.findViewById(R.id.minimumSentenceValue));
            });
            screenshot("overdue-balances.png");
            scenario.onActivity(activity -> {
                ((Slider) activity.findViewById(R.id.breakdownDateSlider)).setValue(range.dates.indexOf(first));
                assertBalances(activity, p, first);
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
                TextView todayLabel = timeline.findViewById(R.id.timelineTodayLabel);
                assertFalse(todayLabel instanceof android.widget.Button);
                assertTrue(todayLabel.isClickable());
                assertText(activity, R.id.timelineTodayLabel, context.getString(R.string.breakdown_today)
                        + "\n" + LocalDate.now().format(SentenceCalculator.DISPLAY_DATE));
                assertTrue(((TextView) timeline.findViewWithTag(range.commitment.toString()))
                        .getText().toString().contains("Jail commitment"));
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
                assertText(activity, R.id.totalGctaValue, "0/0");
                assertText(activity, R.id.totalTastmValue, "0/0");
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
                assertBalances(activity, p, range.minimumCompletion);
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
                assertBalances(activity, p, range.maximumCompletion);
                assertText(activity, R.id.breakdownMaximumReleaseDate,
                        range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
                assertText(activity, R.id.breakdownMaximumReleaseState, context.getString(R.string.breakdown_zero_reached));
                activity.findViewById(R.id.timelineTodayLabel).performClick();
                assertSelectedDate(activity, p, range, LocalDate.now());
            });
            screenshot("timeline-today-milestone.png");
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
                assertEquals(5, timeline.getChildCount()); // Slider, grouped custody, Today, two release labels.
                assertTrue(((TextView) timeline.findViewWithTag(range.start.toString()))
                        .getText().toString().contains("LEA arrest / Jail commitment"));
                assertTrue(((TextView) timeline.findViewById(R.id.timelineMinimumReleaseLabel))
                        .getText().toString().contains(context.getString(R.string.breakdown_marker_minimum)));
                assertTrue(((TextView) timeline.findViewById(R.id.timelineMaximumReleaseLabel))
                        .getText().toString().contains(context.getString(R.string.breakdown_marker_maximum)));
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
                        .getText().toString().startsWith(context.getString(R.string.breakdown_marker_minimum_estimate)));
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
                        context.getString(R.string.breakdown_marker_minimum) + "\n" + range.minimumCompletion.format(SentenceCalculator.DISPLAY_DATE));
            });
            screenshot("moving-labels-minimum-zero.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.timelineMaximumReleaseLabel).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertText(activity, R.id.timelineMaximumReleaseLabel,
                        context.getString(R.string.breakdown_marker_maximum) + "\n" + range.maximumCompletion.format(SentenceCalculator.DISPLAY_DATE));
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
            assertBalances(activity, p, today);
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
            assertText(activity, R.id.totalGctaValue, "400/400");
            assertText(activity, R.id.totalTastmValue, "300/300");
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

    @Test public void exactZeroIsNeutralAndOnlyTheNegativeBalanceTurnsRed() {
        Profile p = db.ProfileDao().getProfileByProfileID(cpiID);
        SentenceProjection.Range range = SentenceProjection.create(p, LocalDate.now());
        Intent intent = new Intent(context, BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID, cpiID);
        try (ActivityScenario<BreakdownActivity> scenario = ActivityScenario.launch(intent)) {
            await(BreakdownActivity.class, activity -> activity.findViewById(R.id.breakdownCard).getVisibility() == View.VISIBLE);
            scenario.onActivity(activity -> {
                Slider slider = activity.findViewById(R.id.breakdownDateSlider);
                int minimumIndex = range.dates.indexOf(range.minimumCompletion);
                slider.setValue(minimumIndex);
                assertBalances(activity, p, range.minimumCompletion);
                assertText(activity, R.id.minimumSentenceValue, context.getString(R.string.breakdown_zero_balance));
                assertFalse(activity.findViewById(R.id.minimumSentenceValue).isActivated());
                slider.setValue(minimumIndex + 1);
                assertBalances(activity, p, range.dates.get(minimumIndex + 1));
                assertTrue(activity.findViewById(R.id.minimumSentenceValue).isActivated());
                assertFalse(activity.findViewById(R.id.maximumSentenceValue).isActivated());
                slider.setValue(minimumIndex);
                assertFalse(activity.findViewById(R.id.minimumSentenceValue).isActivated());
            });
        }
    }

    private void assertBalances(Activity activity, Profile profile, LocalDate date) {
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, date));
        assertText(activity, R.id.minimumSentenceValue, context.getString(R.string.breakdown_balance_value,
                result.minimumText(date), result.minRemainingDays));
        assertText(activity, R.id.maximumSentenceValue, context.getString(R.string.breakdown_balance_value,
                result.maximumText(date), result.maxRemainingDays));
        TextView minimum = activity.findViewById(R.id.minimumSentenceValue);
        TextView maximum = activity.findViewById(R.id.maximumSentenceValue);
        assertEquals(result.minRemainingDays < 0, minimum.isActivated());
        assertEquals(result.maxRemainingDays < 0, maximum.isActivated());
        assertEquals(context.getColor(result.minRemainingDays < 0 ? R.color.laya_overdue_text : R.color.laya_warning_text), minimum.getCurrentTextColor());
        assertEquals(context.getColor(result.maxRemainingDays < 0 ? R.color.laya_overdue_text : R.color.laya_warning_text), maximum.getCurrentTextColor());
    }

    private void reveal(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
        if (parent instanceof android.widget.ScrollView) {
            android.widget.ScrollView scroll = (android.widget.ScrollView) parent;
            Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
            scroll.offsetDescendantRectToMyCoords(view, rect);
            scroll.scrollTo(0, rect.top - 100);
        }
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
