package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;
import com.google.android.material.slider.Slider;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Today by default, with an unsaved monthly preview of the same calculation. */
public class BreakdownActivity extends AppCompatActivity {
    public static final String EXTRA_PROFILE_ID = "profileID";
    private static final String STATE_DATE = "preview_epoch_day";
    private static final String STATE_TODAY = "follow_today";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TodayDateObserver dateObserver = new TodayDateObserver(this, this::prepareProjection);
    private TextView profileName, observationDate, status, rangeStart, rangeEnd;
    private TimelineMilestonesLayout timeline;
    private View breakdownCard, previewControls;
    private Slider dateSlider;
    private Profile profile;
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
        status = findViewById(R.id.breakdownStatus);
        breakdownCard = findViewById(R.id.breakdownCard);
        previewControls = findViewById(R.id.breakdownPreviewControls);
        dateSlider = findViewById(R.id.breakdownDateSlider);
        rangeStart = findViewById(R.id.breakdownRangeStart);
        rangeEnd = findViewById(R.id.breakdownRangeEnd);
        timeline = findViewById(R.id.breakdownTimeline);
        // The selected date belongs to this screen's state, not to Room or View auto-restoration.
        dateSlider.setSaveEnabled(false);
        if (savedInstanceState != null) {
            followToday = savedInstanceState.getBoolean(STATE_TODAY, true);
            if (savedInstanceState.containsKey(STATE_DATE)) {
                selectedDate = LocalDate.ofEpochDay(savedInstanceState.getLong(STATE_DATE));
            }
        }
        dateSlider.setLabelFormatter(value -> range == null ? "" : range.dates.get(
                Math.min(Math.round(value), range.dates.size() - 1)).format(SentenceCalculator.DISPLAY_DATE));
        dateSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (bindingSlider || range == null) return;
            LocalDate date = range.dates.get(Math.round(value));
            followToday = date.equals(LocalDate.now());
            renderDate(date);
        });
        findViewById(R.id.breakdownTodayButton).setOnClickListener(view -> {
            followToday = true;
            LocalDate today = LocalDate.now();
            if (range == null || !today.equals(projectionToday)) prepareProjection();
            else selectDate(today);
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
        ProfileDao dao = AppDatabase.getInstance(getApplicationContext()).ProfileDao();
        executor.execute(() -> {
            try {
                Profile loaded = dao.getProfileByProfileID(profileID);
                if (loaded == null || loaded.userID != userID) {
                    postError(getString(R.string.breakdown_profile_missing));
                    return;
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (currentUserID() != userID) {
                        showError(getString(R.string.breakdown_sign_in));
                        return;
                    }
                    profile = loaded;
                    profileName.setText(getString(R.string.breakdown_profile_name,
                            profile.firstName == null ? "" : profile.firstName,
                            profile.lastName == null ? "" : profile.lastName).trim());
                    profileName.setVisibility(View.VISIBLE);
                    prepareProjection();
                });
            } catch (RuntimeException error) {
                postError(getString(R.string.breakdown_load_failed));
            }
        });
    }

    private long currentUserID() {
        return getSharedPreferences("user_session", MODE_PRIVATE).getLong("currentUserID", -1L);
    }

    private void prepareProjection() {
        if (profile == null || isFinishing() || isDestroyed()) return;
        final int request = ++generation;
        final LocalDate today = LocalDate.now();
        final LocalDate preview = followToday ? null : selectedDate;
        final Profile saved = profile;
        projectionToday = today;
        dateSlider.setEnabled(false);
        executor.execute(() -> {
            SentenceCalculator.Result current = SentenceCalculator.calculate(ProfileCalculationMapper.from(saved, today));
            SentenceProjection.Range calculatedRange = null;
            String problem = null;
            if (current.available) {
                try { calculatedRange = SentenceProjection.create(saved, today, preview); }
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
                    selectDate(date);
                });
                previewControls.setVisibility(View.VISIBLE);
                selectDate(preview != null && range.dates.contains(preview) ? preview : today);
            });
        });
    }

    private void selectDate(LocalDate date) {
        bindingSlider = true;
        dateSlider.setValue(range.dates.indexOf(date));
        bindingSlider = false;
        renderDate(date);
    }

    private void renderDate(LocalDate date) {
        selectedDate = date;
        SentenceCalculator.Result result = SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, date));
        observationDate.setText(getString(date.equals(LocalDate.now())
                        ? R.string.breakdown_observation_today : R.string.breakdown_observation_preview,
                date.format(SentenceCalculator.DISPLAY_DATE)));
        observationDate.setVisibility(View.VISIBLE);
        if (!result.available) {
            breakdownCard.setVisibility(View.GONE);
            status.setText(getString(R.string.breakdown_calculation_failed, result.problem));
            status.setVisibility(View.VISIBLE);
            return;
        }
        Bundle values = BreakdownViewBinder.createArguments(
                getString(R.string.breakdown_balance_value, result.minimumText(date), result.minRemainingDays),
                getString(R.string.breakdown_balance_value, result.maximumText(date), result.maxRemainingDays),
                result);
        BreakdownViewBinder.bind(breakdownCard, values);
        status.setVisibility(View.GONE);
        breakdownCard.setVisibility(View.VISIBLE);
    }

    private void postError(String message) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) showError(message); });
    }

    private void showError(String message) {
        previewControls.setVisibility(View.GONE);
        breakdownCard.setVisibility(View.GONE);
        status.setText(message);
        status.setVisibility(View.VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
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
