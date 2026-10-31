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
    private ZoomTimelineScrollView zoomScroll;
    private TextView zoomLevel;
    private Profile profile;
    private DeductionDatesController deductions;
    private SentenceProjection.Range range;
    private SequentialSentencePlan servicePlan;
    private boolean serviceSaving, autoPrompted, selectNewCharge, openingChargeChecked, tutorialChecked;

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
        View observationHelp=findViewById(R.id.observationDateHelp);
        int helpPadding=Math.round(5*getResources().getDisplayMetrics().density);
        observationHelp.setPadding(helpPadding,helpPadding,helpPadding,helpPadding);
        observationDate.setPaintFlags(observationDate.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        observationDate.setOnClickListener(view -> showObservationDatePicker());
        status = findViewById(R.id.breakdownStatus);
        breakdownCard = findViewById(R.id.breakdownCard);
        previewControls = findViewById(R.id.breakdownPreviewControls);
        dateSlider = findViewById(R.id.breakdownDateSlider);
        rangeStart = findViewById(R.id.breakdownRangeStart);
        rangeEnd = findViewById(R.id.breakdownRangeEnd);
        timeline = findViewById(R.id.breakdownTimeline);
        zoomScroll=findViewById(R.id.timelineZoomScroll);
        zoomLevel=findViewById(R.id.timelineZoomLevel);
        if(savedInstanceState!=null)zoomScroll.restoreZoom(savedInstanceState.getFloat("timeline_zoom",1));
        findViewById(R.id.timelineZoomIn).setOnClickListener(v->zoomTimeline(2));
        findViewById(R.id.timelineZoomOut).setOnClickListener(v->zoomTimeline(.5f));
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
        tutorialChecked=savedInstanceState!=null&&savedInstanceState.getBoolean("timeline_tutorial_checked");
        getSupportFragmentManager().setFragmentResultListener(TimelineTutorialDialog.DISMISSED,this,
                (key,result)->dateSlider.post(this::showOpeningDialogs));
        openingChargeChecked=savedInstanceState!=null&&savedInstanceState.getBoolean("service_opening_checked");
        autoPrompted=savedInstanceState!=null&&savedInstanceState.getBoolean("service_auto_prompted");
        findViewById(R.id.serviceAddCharge).setOnClickListener(v->showAddCharge());
        getSupportFragmentManager().setFragmentResultListener(AddServiceChargeDialog.RESULT,this,(key,result)->advanceCharge(result));
        rebindObservationDatePicker();
        dateSlider.setLabelFormatter(value -> range == null ? "" : range.dates.get(
                Math.min(Math.round(value), range.dates.size() - 1)).format(SentenceCalculator.DISPLAY_DATE));
        dateSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (bindingSlider || range == null) return;
            LocalDate date = range.dates.get(Math.round(value));
            LocalDate bounded=clampSelection(date);
            if(!date.equals(bounded)){
                date=bounded;bindingSlider=true;slider.setValue(range.dates.indexOf(date));bindingSlider=false;
            }
            followToday = date.equals(LocalDate.now());
            renderDate(date);
            if(fromUser&&servicePlan!=null){
                if(date.isBefore(servicePlan.active().maximum))autoPrompted=false;
                else if(date.equals(servicePlan.active().maximum)&&!autoPrompted&&servicePlan.hasNext(date)){
                    autoPrompted=true;dateSlider.post(this::showAddCharge);
                }
            }
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
        final Profile requested = profile;
        projectionToday = today;
        dateSlider.setEnabled(false);
        observationDate.setEnabled(false);
        executor.execute(() -> {
            Profile saved;
            SequentialSentencePlan plan=null;
            SentenceProjection.Range calculatedRange=null;
            SentenceCalculator.Result current=null;
            String problem=null;
            try {
                saved=com.example.myapplication.db.AppDatabase.getInstance(this).ProfileDao().getProfileByProfileID(requested.profileID);
                if(saved==null||saved.userID!=requested.userID)throw new IllegalArgumentException("Profile no longer available.");
                if(saved.charges.size()>1){
                    plan=new SequentialSentencePlan(saved);
                    calculatedRange=plan.range(today,true);
                    current=plan.calculate(today.isBefore(plan.active().start)?plan.active().start:today);
                }else{
                    current=SentenceCalculator.calculate(ProfileCalculationMapper.from(saved,today));
                    if(current.available)calculatedRange=SentenceProjection.create(saved,today);
                }
            }catch(RuntimeException error){saved=requested;problem=error.getMessage();}
            final Profile loaded=saved;
            final SequentialSentencePlan completedPlan=plan;
            final SentenceCalculator.Result completedCurrent=current;
            final SentenceProjection.Range completedRange=calculatedRange;
            final String projectionProblem=problem;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || request != generation) return;
                if (currentUserID() != loaded.userID) {
                    showError(getString(R.string.breakdown_sign_in));
                    return;
                }
                if (!today.equals(LocalDate.now())) {
                    prepareProjection();
                    return;
                }
                if (completedCurrent==null || !completedCurrent.available || projectionProblem!=null) {
                    showError(getString(R.string.breakdown_calculation_failed, projectionProblem!=null?projectionProblem:completedCurrent==null?"Check profile inputs.":completedCurrent.problem));
                    return;
                }
                profile=loaded;
                servicePlan=completedPlan;
                range = completedRange;
                bindChargeGrid();
                findViewById(R.id.serviceAddCharge).setVisibility(View.VISIBLE);
                findViewById(R.id.serviceAddCharge).setEnabled(!serviceSaving);
                timeline.setIntermediateCharge(servicePlan!=null&&!servicePlan.finalCharge());
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
                timeline.setChargeContext(servicePlan);
                zoomScroll.configure(range.dates.size()-1,()->zoomLevel.setText(zoomScroll.getZoom()<=1.001f?getString(R.string.timeline_full):getString(R.string.timeline_zoom_level,zoomScroll.getZoom())));
                previewControls.setVisibility(View.VISIBLE);
                // Read the latest selection: a restored picker may confirm while this work runs.
                LocalDate preview = selectNewCharge?selectionFloor():followToday ? today : selectedDate;
                if(selectNewCharge){selectNewCharge=false;followToday=false;}
                selectDate(preview != null && range.dates.contains(preview) ? preview : today);
                showOpeningDialogs();
            });
        });
    }

    private void showOpeningDialogs() {
        if(range==null||!dateSlider.isEnabled()||isFinishing()||isDestroyed()
                ||getSupportFragmentManager().isStateSaved()
                ||getSupportFragmentManager().findFragmentByTag(TimelineTutorialDialog.TAG)!=null)return;
        if(!tutorialChecked){
            tutorialChecked=true;
            if(!TimelineTutorialDialog.acknowledged(this,currentUserID())){
                TimelineTutorialDialog.create(currentUserID()).showNow(getSupportFragmentManager(),TimelineTutorialDialog.TAG);
                return;
            }
        }
        if(!openingChargeChecked){
            openingChargeChecked=true;
            LocalDate today=LocalDate.now();
            if(servicePlan!=null&&!today.isBefore(servicePlan.active().maximum)
                    &&selectedDate.equals(servicePlan.active().maximum)&&servicePlan.hasNext(selectedDate)){
                autoPrompted=true;
                showAddCharge();
            }
        }
    }

    @Override protected void onResumeFragments(){
        super.onResumeFragments();
        if(dateSlider!=null)dateSlider.post(this::showOpeningDialogs);
    }

    private void showObservationDatePicker() {
        if (range == null || selectedDate == null || !observationDate.isEnabled()
                || getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(OBSERVATION_PICKER) != null) return;
        long start = pickerMillis(selectionFloor()), end = pickerMillis(selectionCeiling());
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

    private void zoomTimeline(float factor){
        if(range!=null&&selectedDate!=null)zoomScroll.zoomBy(factor,range.positionOf(selectedDate));
    }

    private void selectDate(LocalDate date) {
        date=clampSelection(date);
        bindingSlider = true;
        dateSlider.setValue(range.dates.indexOf(date));
        bindingSlider = false;
        renderDate(date);
        zoomScroll.reveal(range.positionOf(date));
    }

    private void renderDate(LocalDate date) {
        selectedDate = date;
        if (deductions != null) deductions.setObservationDate(date);
        SentenceCalculator.Result result = servicePlan==null?SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(profile, date)):servicePlan.calculate(date);
        observationDate.setText(getString(date.equals(LocalDate.now())
                        ? R.string.breakdown_observation_today : R.string.breakdown_observation_preview,
                date.format(SentenceCalculator.DISPLAY_DATE)));
        boolean intermediate=servicePlan!=null&&!servicePlan.finalCharge();
        ((TextView)findViewById(R.id.serviceMinimumHeading)).setText(intermediate?R.string.service_minimum:R.string.breakdown_minimum_release);
        ((TextView)findViewById(R.id.serviceMaximumHeading)).setText(intermediate?R.string.service_maximum:R.string.breakdown_maximum_release);
        observationDate.setContentDescription(getString(R.string.breakdown_observation_picker_description,
                observationDate.getText()));
        observationDate.setEnabled(range != null);
        findViewById(R.id.breakdownObservationRow).setVisibility(View.VISIBLE);
        findViewById(R.id.serviceChargesSection).setVisibility(View.VISIBLE);
        ((TextView)findViewById(R.id.serviceChargesHeading)).setText(
                getString(R.string.service_charges_total, profile.charges.size()));
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
        timeline.setOverdueStart(servicePlan==null?range.maximumCompletion:servicePlan.overdueStart(date));
        timeline.setLimitRelevant(servicePlan!=null&&servicePlan.limitRelevant(date));
        timeline.setPreview(date, dates);
        boolean limited=servicePlan!=null&&servicePlan.limitRelevant(date);
        findViewById(R.id.serviceLimitMessage).setVisibility(limited?View.VISIBLE:View.GONE);
        findViewById(R.id.serviceAddCharge).setEnabled(!serviceSaving&&servicePlan!=null&&servicePlan.hasNext(date));
        // Release dates stay pinned at their first zero; balances keep counting with earned credits.
        Bundle values = BreakdownViewBinder.createArguments(
                getString(R.string.breakdown_balance_value, result.minimumText(date), result.minRemainingDays),
                getString(R.string.breakdown_balance_value, result.maximumText(date), result.maxRemainingDays),
                result);
        BreakdownViewBinder.bind(breakdownCard, values);
        status.setVisibility(View.GONE);
        breakdownCard.setVisibility(View.VISIBLE);
    }

    private LocalDate selectionFloor(){return servicePlan==null?range.start:servicePlan.active().start;}

    private LocalDate selectionCeiling(){
        // A remaining serviceable charge must be appended before previewing beyond
        // this handoff. Final charges and custody-limit cutoffs retain overdue space.
        return servicePlan!=null&&servicePlan.hasNext()?servicePlan.active().maximum:range.end;
    }

    private LocalDate clampSelection(LocalDate date){
        if(date.isBefore(selectionFloor()))return selectionFloor();
        if(date.isAfter(selectionCeiling()))return selectionCeiling();
        return date;
    }

    private void showAddCharge(){
        if(servicePlan==null||selectedDate==null||!servicePlan.hasNext(selectedDate)||serviceSaving||!dateSlider.isEnabled()
                ||getSupportFragmentManager().isStateSaved()
                ||getSupportFragmentManager().findFragmentByTag(AddServiceChargeDialog.TAG)!=null)return;
        AddServiceChargeDialog.create(servicePlan,selectedDate).show(getSupportFragmentManager(),AddServiceChargeDialog.TAG);
    }

    private void bindChargeGrid() {
        android.widget.LinearLayout grid=findViewById(R.id.serviceChargeGrid);
        grid.removeAllViews();
        int count=servicePlan==null?1:servicePlan.stages.size();
        float density=getResources().getDisplayMetrics().density;
        int gap=Math.round(4*density),height=Math.round(48*density);
        android.widget.LinearLayout row=null;
        final int columns=5;
        for(int index=0;index<((count+columns)/columns)*columns;index++) {
            if(index%columns==0) {
                row=new android.widget.LinearLayout(this);
                row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                grid.addView(row,new android.widget.LinearLayout.LayoutParams(-1,-2));
            }
            final int number=index+1;
            View cell;
            if(number<=count) {
                android.widget.FrameLayout pill=new android.widget.FrameLayout(this);
                pill.setTag("service-pill-"+number);
                android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
                shape.setColor(getColor(R.color.laya_surface));shape.setCornerRadius(Math.round(20*density));
                shape.setStroke(Math.round(density),getColor(R.color.laya_border));
                pill.setBackground(new android.graphics.drawable.InsetDrawable(shape,0,gap,0,gap));
                TextView label=new TextView(this);label.setText(String.valueOf(number));label.setTextSize(14);
                label.setTextColor(getColor(R.color.laya_label));label.setGravity(android.view.Gravity.CENTER);
                label.setContentDescription(getString(R.string.service_charge_number,number));
                android.widget.FrameLayout.LayoutParams textParams=new android.widget.FrameLayout.LayoutParams(-1,-1);
                textParams.setMarginEnd(Math.round(32*density));
                pill.addView(label,textParams);
                if(number>1){
                    androidx.appcompat.widget.AppCompatImageButton close=new androidx.appcompat.widget.AppCompatImageButton(this);
                    close.setImageResource(R.drawable.charge_remove);
                    close.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.laya_label)));
                    android.util.TypedValue feedback=new android.util.TypedValue();
                    getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,feedback,true);
                    close.setBackgroundResource(feedback.resourceId);close.setPadding(gap*2,gap*2,gap*2,gap*2);
                    close.setTag("service-charge-"+number);
                    close.setContentDescription(getString(R.string.service_remove_charge,number));
                    close.setOnClickListener(v->removeCharge(number));
                    pill.addView(close,new android.widget.FrameLayout.LayoutParams(Math.round(32*density),height,android.view.Gravity.END|android.view.Gravity.CENTER_VERTICAL));
                }else pill.setTag("service-charge-1");
                cell=pill;
            } else if(number==count+1) {
                com.google.android.material.button.MaterialButton add=new com.google.android.material.button.MaterialButton(
                        this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
                add.setId(R.id.serviceAddCharge);add.setText(R.string.service_add);add.setTextSize(11);add.setAllCaps(false);
                add.setTextColor(getColor(R.color.laya_label));add.setMinWidth(0);add.setMinimumWidth(0);
                add.setPadding(gap,0,gap,0);add.setCornerRadius(Math.round(20*density));
                add.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.laya_border)));
                add.setStrokeWidth(Math.round(density));
                add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.laya_surface)));
                add.setInsetTop(gap);add.setInsetBottom(gap);
                add.setOnClickListener(v->showAddCharge());cell=add;
            } else cell=new View(this);
            android.widget.LinearLayout.LayoutParams params=new android.widget.LinearLayout.LayoutParams(0,height,1);
            if(index%columns<columns-1)params.setMarginEnd(gap);
            row.addView(cell,params);
        }
    }

    private void removeCharge(int number) {
        if(serviceSaving||servicePlan==null||!dateSlider.isEnabled()||number<2||number>servicePlan.stages.size())return;
        final String expected=profile.serviceChoices==null?"":profile.serviceChoices;
        final LocalDate handoff=servicePlan.stages.get(number-1).start;
        serviceSaving=true;
        findViewById(R.id.serviceAddCharge).setEnabled(false);
        dateSlider.setEnabled(false);
        final int id=profile.profileID;final long owner=currentUserID();
        executor.execute(()->{
            Profile saved=null;String failure=null;
            try {saved=com.example.myapplication.db.AppDatabase.getInstance(this).ProfileDao()
                    .removeServiceFrom(id,owner,expected,number);}
            catch(RuntimeException error){failure=error.getMessage();}
            final Profile completed=saved;final String problem=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                serviceSaving=false;
                if(completed!=null){
                    profile=completed;autoPrompted=true;selectNewCharge=false;
                    selectedDate=handoff;followToday=false;prepareProjection();
                } else {
                    prepareProjection();
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.service_save_failed,problem)).setPositiveButton(android.R.string.ok,null).show();
                }
            });
        });
    }

    private void advanceCharge(Bundle choice){
        if(serviceSaving)return;
        serviceSaving=true;
        findViewById(R.id.serviceAddCharge).setEnabled(false);
        dateSlider.setEnabled(false);
        final int id=getIntent().getIntExtra(EXTRA_PROFILE_ID,-1);final long owner=currentUserID();
        executor.execute(()->{
            Profile saved=null;String failure=null;
            try{
                com.example.myapplication.db.ProfileDao dao=com.example.myapplication.db.AppDatabase.getInstance(this).ProfileDao();
                LocalDate observation=LocalDate.parse(choice.getString("observation"));
                saved=choice.containsKey("customDate")
                    ?dao.advanceServiceCustom(id,owner,choice.getString("expected",""),LocalDate.parse(choice.getString("customDate")),observation)
                    :dao.advanceService(id,owner,choice.getString("expected",""),choice.getBoolean("fromMaximum"),observation);
            }
            catch(RuntimeException error){failure=error.getMessage();}
            final Profile completed=saved;final String problem=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                serviceSaving=false;
                if(completed!=null){profile=completed;autoPrompted=false;selectNewCharge=true;followToday=false;prepareProjection();}
                else{findViewById(R.id.serviceAddCharge).setEnabled(true);dateSlider.setEnabled(true);
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setMessage(getString(R.string.service_save_failed,problem)).setPositiveButton(android.R.string.ok,null).show();}
            });
        });
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
        outState.putBoolean("timeline_tutorial_checked",tutorialChecked);
        outState.putBoolean("service_auto_prompted",autoPrompted);
        outState.putBoolean("service_opening_checked",openingChargeChecked);
        outState.putFloat("timeline_zoom",zoomScroll.getZoom());
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
