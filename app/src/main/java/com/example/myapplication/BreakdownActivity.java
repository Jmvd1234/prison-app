package com.example.myapplication;

import android.os.Bundle;
import android.graphics.Paint;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.Profile;
import com.google.android.material.slider.Slider;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Today by default, with an unsaved daily preview of the same calculation. */
public class BreakdownActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profileID";
    private static final String OBSERVATION_PICKER = "breakdown_observation_picker";
    private static final String STATE_DATE = "preview_epoch_day";
    private static final String STATE_TODAY = "follow_today";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TodayDateObserver dateObserver = new TodayDateObserver(this, this::prepareProjection);
    private TextView profileName, observationDate, status, rangeStart, rangeEnd;
    private TextView minimumReleaseDate, maximumReleaseDate, minimumReleaseState, maximumReleaseState;
    private TimelineMilestonesLayout timeline;
    private View breakdownCard, previewControls, releaseDates;
    private Slider dateSlider;
    private View[] backwardDateButtons, forwardDateButtons;
    private Profile profile;
    private DeductionDatesController deductions;
    private SentenceProjection.Range range;
    private LocalDate selectedDate, projectionToday;
    private boolean followToday = true, bindingSlider;
    private int generation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_breakdown);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        findViewById(R.id.breakdownBackButton).setOnClickListener(view -> finish());
        profileName = findViewById(R.id.breakdownProfileName);
        observationDate = findViewById(R.id.breakdownObservationDate);
        observationDate.setPaintFlags(observationDate.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        observationDate.setOnClickListener(view -> showObservationDatePicker());
        status = findViewById(R.id.breakdownStatus);
        breakdownCard = findViewById(R.id.breakdownCard);
        previewControls = findViewById(R.id.breakdownPreviewControls);
        dateSlider = findViewById(R.id.breakdownDateSlider);
        backwardDateButtons = new View[]{findViewById(R.id.breakdownDayBack),
                findViewById(R.id.breakdownMonthBack), findViewById(R.id.breakdownYearBack)};
        forwardDateButtons = new View[]{findViewById(R.id.breakdownDayForward),
                findViewById(R.id.breakdownMonthForward), findViewById(R.id.breakdownYearForward)};
        ChronoUnit[] units = {ChronoUnit.DAYS, ChronoUnit.MONTHS, ChronoUnit.YEARS};
        for (int i = 0; i < units.length; i++) {
            final ChronoUnit unit = units[i];
            backwardDateButtons[i].setOnClickListener(view -> shiftDate(-1, unit));
            forwardDateButtons[i].setOnClickListener(view -> shiftDate(1, unit));
        }
        rangeStart = findViewById(R.id.breakdownRangeStart);
        rangeEnd = findViewById(R.id.breakdownRangeEnd);
        timeline = findViewById(R.id.breakdownTimeline);
        releaseDates = findViewById(R.id.breakdownReleaseDates);
        minimumReleaseDate = findViewById(R.id.breakdownMinimumReleaseDate);
        maximumReleaseDate = findViewById(R.id.breakdownMaximumReleaseDate);
        minimumReleaseState = findViewById(R.id.breakdownMinimumReleaseState);
        maximumReleaseState = findViewById(R.id.breakdownMaximumReleaseState);
        // The selected date belongs to this screen's state, not to Room or View auto-restoration.
        dateSlider.setSaveEnabled(false);
        if (savedInstanceState != null) {
            followToday = savedInstanceState.getBoolean(STATE_TODAY, true);
            if (savedInstanceState.containsKey(STATE_DATE)) {
                selectedDate = LocalDate.ofEpochDay(savedInstanceState.getLong(STATE_DATE));
            }
        }
        rebindObservationDatePicker();
        dateSlider.setLabelFormatter(value -> range == null ? "" : range.dates.get(
                Math.min(Math.round(value), range.dates.size() - 1)).format(SentenceCalculator.DISPLAY_DATE));
        dateSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (bindingSlider || range == null) return;
            LocalDate date = range.dates.get(Math.round(value));
            followToday = date.equals(LocalDate.now());
            renderDate(date);
        });

        int profileID = getIntent().getIntExtra(EXTRA_PROFILE_ID, -1);
        long userID = currentUserID();
        if (userID < 0) {
            showError(getString(R.string.breakdown_sign_in));
            return;
        }
        if (profileID <= 0) {
            showError(getString(R.string.breakdown_profile_missing));
            return;
        }
        deductions = new DeductionDatesController(this, profileID, userID, savedInstanceState, loaded -> {
            profile = loaded;
            profileName.setText(getString(R.string.breakdown_profile_name,
                    profile.firstName == null ? "" : profile.firstName,
                    profile.lastName == null ? "" : profile.lastName).trim());
            profileName.setVisibility(View.VISIBLE);
            prepareProjection();
        }, this::showError);
    }

    private long currentUserID() {
        return getSharedPreferences("user_session", MODE_PRIVATE).getLong("currentUserID", -1L);
    }

    private void prepareProjection() {
        if (profile == null || isFinishing() || isDestroyed()) return;
        final int request = ++generation;
        final LocalDate today = LocalDate.now();
        final Profile saved = profile;
        projectionToday = today;
        dateSlider.setEnabled(false);
        observationDate.setEnabled(false);
        setDateButtonsEnabled(false, false);
        executor.execute(() -> {
            SentenceCalculator.Result current = SentenceCalculator.calculate(ProfileCalculationMapper.from(saved, today));
            SentenceProjection.Range calculatedRange = null;
            String problem = null;
            if (current.available) {
                try { calculatedRange = SentenceProjection.create(saved, today); }
                catch (IllegalArgumentException error) { problem = error.getMessage(); }
            }
            final SentenceProjection.Range completedRange = calculatedRange;
            final String projectionProblem = problem;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || request != generation) return;
                if (currentUserID() != saved.userID) {
                    showError(getString(R.string.breakdown_sign_in));
                    return;
                }
                if (!today.equals(LocalDate.now())) {
                    prepareProjection();
                    return;
                }
                if (!current.available) {
                    showError(getString(R.string.breakdown_calculation_failed, current.problem));
                    return;
                }
                range = completedRange;
                if (range == null) {
                    previewControls.setVisibility(View.GONE);
                    renderDate(today);
                    status.setText(getString(R.string.breakdown_projection_failed, projectionProblem));
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                rangeStart.setText(getString(R.string.breakdown_range_start,
                        range.start.format(SentenceCalculator.DISPLAY_DATE)));
                rangeEnd.setText(getString(R.string.breakdown_range_end,
                        range.end.format(SentenceCalculator.DISPLAY_DATE)));
                bindingSlider = true;
                dateSlider.setValue(0);
                dateSlider.setValueTo(Math.max(1, range.dates.size() - 1));
                dateSlider.setStepSize(1);
                bindingSlider = false;
                dateSlider.setEnabled(range.dates.size() > 1);
                timeline.setMilestones(range, date -> {
                    followToday = date.equals(LocalDate.now());
                    if (followToday && !date.equals(projectionToday)) prepareProjection();
                    else selectDate(date);
                });
                previewControls.setVisibility(View.VISIBLE);
                // Read the latest selection: a restored picker may confirm while this work runs.
                LocalDate preview = followToday ? today : selectedDate;
                selectDate(preview != null && range.dates.contains(preview) ? preview : today);
            });
        });
    }

    private void showObservationDatePicker() {
        if (range == null || selectedDate == null || !observationDate.isEnabled()
                || getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(OBSERVATION_PICKER) != null) return;
        long start = pickerMillis(range.start), end = pickerMillis(range.end);
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(start).setEnd(end).setOpenAt(pickerMillis(selectedDate))
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(start), DateValidatorPointBackward.before(end))))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.breakdown_choose_observation_date)
                .setSelection(pickerMillis(selectedDate))
                .setCalendarConstraints(constraints).build();
        connectObservationDatePicker(picker);
        picker.showNow(getSupportFragmentManager(), OBSERVATION_PICKER);
    }

    // Material pickers represent calendar dates at midnight UTC, independently of device time zone.
    private static long pickerMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private void connectObservationDatePicker(MaterialDatePicker<Long> picker) {
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(millis -> {
            if (millis == null) return;
            LocalDate date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
            if (range != null && !range.dates.contains(date)) return;
            selectedDate = date;
            followToday = date.equals(LocalDate.now());
            if (range != null && observationDate.isEnabled()) selectDate(date);
        });
    }

    @SuppressWarnings("unchecked")
    private void rebindObservationDatePicker() {
        androidx.fragment.app.Fragment fragment = getSupportFragmentManager().findFragmentByTag(OBSERVATION_PICKER);
        if (fragment instanceof MaterialDatePicker<?>) connectObservationDatePicker((MaterialDatePicker<Long>) fragment);
    }

    private void shiftDate(int amount, ChronoUnit unit) {
        if (range == null || selectedDate == null || !dateSlider.isEnabled()) return;
        // Calendar jumps clamp to a valid day in shorter months, then to the timeline bounds.
        LocalDate date = selectedDate.plus(amount, unit);
        if (date.isBefore(range.start)) date = range.start;
        if (date.isAfter(range.end)) date = range.end;
        followToday = date.equals(LocalDate.now());
        selectDate(date);
    }

    private void setDateButtonsEnabled(boolean backward, boolean forward) {
        for (View button : backwardDateButtons) {
            button.setEnabled(backward);
            button.setAlpha(backward ? 1f : 0.35f);
        }
        for (View button : forwardDateButtons) {
            button.setEnabled(forward);
            button.setAlpha(forward ? 1f : 0.35f);
        }
    }

    private void selectDate(LocalDate date) {
        bindingSlider = true;
        dateSlider.setValue(range.dates.indexOf(date));
        bindingSlider = false;
        renderDate(date);
    }

    private void renderDate(LocalDate date) {
        selectedDate = date;
        boolean canMove = range != null && dateSlider.isEnabled();
        setDateButtonsEnabled(canMove && date.isAfter(range.start), canMove && date.isBefore(range.end));
        if (deductions != null) deductions.setObservationDate(date);
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, date));
        observationDate.setText(getString(date.equals(LocalDate.now())
                        ? R.string.breakdown_observation_today : R.string.breakdown_observation_preview,
                date.format(SentenceCalculator.DISPLAY_DATE)));
        observationDate.setContentDescription(getString(R.string.breakdown_observation_picker_description,
                observationDate.getText()));
        observationDate.setEnabled(range != null);
        findViewById(R.id.breakdownObservationRow).setVisibility(View.VISIBLE);
        if (!result.available) {
            releaseDates.setVisibility(View.GONE);
            breakdownCard.setVisibility(View.GONE);
            status.setText(getString(R.string.breakdown_calculation_failed, result.problem));
            status.setVisibility(View.VISIBLE);
            return;
        }
        SentenceProjection.ReleaseDates dates = SentenceProjection.releaseDates(range, date, result);
        minimumReleaseDate.setText(dates.minimum.format(SentenceCalculator.DISPLAY_DATE));
        maximumReleaseDate.setText(dates.maximum.format(SentenceCalculator.DISPLAY_DATE));
        minimumReleaseState.setText(dates.minimumReached ? R.string.breakdown_zero_reached : R.string.breakdown_release_estimate);
        maximumReleaseState.setText(dates.maximumReached ? R.string.breakdown_zero_reached : R.string.breakdown_release_estimate);
        releaseDates.setVisibility(View.VISIBLE);
        timeline.setPreview(date, dates);
        // Release dates stay pinned at their first zero; balances keep counting with earned credits.
        Bundle values = BreakdownViewBinder.createArguments(
                getString(R.string.breakdown_balance_value, result.minimumText(date), result.minRemainingDays),
                getString(R.string.breakdown_balance_value, result.maximumText(date), result.maxRemainingDays),
                result);
        BreakdownViewBinder.bind(breakdownCard, values);
        status.setVisibility(View.GONE);
        breakdownCard.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        observationDate.setEnabled(false);
        findViewById(R.id.deductionDatesPanel).setVisibility(View.GONE);
        releaseDates.setVisibility(View.GONE);
        previewControls.setVisibility(View.GONE);
        breakdownCard.setVisibility(View.GONE);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (deductions != null) deductions.saveState(outState);
        outState.putBoolean(STATE_TODAY, followToday);
        if (selectedDate != null) outState.putLong(STATE_DATE, selectedDate.toEpochDay());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        dateObserver.start();
        if (profile != null && !LocalDate.now().equals(projectionToday)) prepareProjection();
    }

    @Override protected void onPause() {
        dateObserver.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        ++generation;
        dateObserver.stop();
        executor.shutdownNow();
        super.onDestroy();
    }
}
