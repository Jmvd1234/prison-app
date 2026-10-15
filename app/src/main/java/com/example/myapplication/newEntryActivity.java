package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;


public class newEntryActivity extends AppCompatActivity {

    public static final String EXTRA_BREAKDOWN = "profile_breakdown";

    EditText inputFirstName;
    EditText inputLastName;

    EditText inputLEADate;

    EditText inputMinYears;
    EditText inputMinMonths;
    EditText inputMinDays;

    EditText inputMaxYears;
    EditText inputMaxMonths;
    EditText inputMaxDays;

    EditText inputDeductionRaw;
    EditText inputTimeGap;

    Spinner inputSTAL;

    Button saveEntryButton;

    SharedPreferences sp;

    AppDatabase db;
    ProfileDao profileDao;

    ConstraintLayout layout;

    // Shared triggers and separate jail/prison date inputs.
    private TriggerFormController triggerController;
    private AdditionalChargesController additionalChargesController;
    private ProfilePhotoController photoController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_new_entry);


        layout = findViewById(R.id.main);


        ViewCompat.setOnApplyWindowInsetsListener(
                layout,
                (v, insets) -> {

                    Insets systemBars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                }
        );


        // =====================================================
        // NORMAL FORM INPUTS
        // =====================================================

        inputFirstName =
                findViewById(R.id.inputFirstName);

        inputLastName =
                findViewById(R.id.inputLastName);


        inputLEADate =
                findViewById(R.id.inputLEADate);


        inputMinYears =
                findViewById(R.id.inputMinYears);

        inputMinMonths =
                findViewById(R.id.inputMinMonths);

        inputMinDays =
                findViewById(R.id.inputMinDays);


        inputMaxYears =
                findViewById(R.id.inputMaxYears);

        inputMaxMonths =
                findViewById(R.id.inputMaxMonths);

        inputMaxDays =
                findViewById(R.id.inputMaxDays);

        // Charges are kept individually; the calculator derives the combined service term.
        additionalChargesController = new AdditionalChargesController(
                findViewById(R.id.additionalChargesContainer), findViewById(R.id.addChargeButton), savedInstanceState);


        inputDeductionRaw =
                findViewById(R.id.inputDeductionRaw);

        inputTimeGap =
                findViewById(R.id.inputTimeGap);


        inputSTAL =
                findViewById(R.id.inputSTAL);


        saveEntryButton =
                findViewById(R.id.saveEntryButton);



        // =====================================================
        // DATABASE
        // =====================================================

        sp =
                getSharedPreferences(
                        "user_session",
                        Context.MODE_PRIVATE
                );


        db =
                AppDatabase.getInstance(
                        getApplicationContext()
                );

        profileDao =
                db.ProfileDao();


        // =====================================================
        // NORMAL DATE PICKERS
        // =====================================================

        /*
         * The LEA arrest date remains
         * owned by this Activity.
         *
         * TriggerFormController owns both separate custody date pickers
         * and selects the calculation anchor according to the triggers.
         */

        inputLEADate.setOnClickListener(v -> {

            showDatePickerDialog(
                    inputLEADate,
                    "Select LEA Arrest Date"
            );
        });


        // =====================================================
        // STAL DROPDOWN
        // =====================================================

        // STAL is calculated using the website's selected percentage.

        String[] stalOptions = {
                "None",
                "Returned After a Disaster (20%)",
                "Did Not Leave (40%)"
        };


        ArrayAdapter<String> stalAdapter =
                new ArrayAdapter<>(
                        this,
                        R.layout.stal_spinner_item,
                        stalOptions
                );


        stalAdapter.setDropDownViewResource(
                R.layout.stal_spinner_dropdown_item
        );


        inputSTAL.setAdapter(
                stalAdapter
        );


        // =====================================================
        // TRIGGER CONTROLLER
        // =====================================================

        // The controller owns the switches and both custody date inputs.

        triggerController =
                new TriggerFormController(
                        this,
                        findViewById(
                                R.id.triggerDetailsContainer
                        )
                );


        /*
         * null means this is a brand-new Profile,
         * rather than an existing Profile being edited.
         *
         * savedInstanceState allows temporary trigger
         * state to survive Activity recreation.
         */

        triggerController.load(
                null,
                savedInstanceState
        );


        // =====================================================
        // BUTTONS
        // =====================================================

        photoController = new ProfilePhotoController(this, null, breakdown -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_BREAKDOWN, breakdown));
            finish();
        });

        saveEntryButton.setOnClickListener(v -> {

            try {
                saveProfile();
            } catch (IllegalArgumentException error) {
                Snackbar.make(layout, error.getMessage() == null
                        ? "Check the entered numbers and dates." : error.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });


    }


    // =========================================================
    // DATE PICKER
    // =========================================================

    private void showDatePickerDialog(
            EditText target,
            String title) {

        /*
         * Give each EditText its own Fragment tag so
         * Android cannot accidentally open duplicate
         * copies of the same picker.
         */

        String pickerTag =
                "DATE_PICKER_" + target.getId();


        if (getSupportFragmentManager()
                .findFragmentByTag(pickerTag) != null) {

            return;
        }


        long selection =
                MaterialDatePicker.todayInUtcMilliseconds();


        /*
         * If this field already contains a valid date,
         * open the picker on that date instead of today.
         */

        try {

            LocalDate existingDate =
                    SentenceCalculator.optionalDate(
                            target.getText()
                                    .toString()
                                    .trim(),
                            title
                    );


            if (existingDate != null) {

                selection =
                        existingDate
                                .atStartOfDay(
                                        ZoneOffset.UTC
                                )
                                .toInstant()
                                .toEpochMilli();
            }

        }
        catch (IllegalArgumentException ignored) {

            // Invalid/empty field:
            // simply open the picker on today.
        }


        MaterialDatePicker<Long> materialDatePicker =
                MaterialDatePicker.Builder
                        .datePicker()
                        .setTitleText(title)
                        .setSelection(selection)
                        .build();


        materialDatePicker
                .addOnPositiveButtonClickListener(
                        selectedMillis -> {

                            /*
                             * MaterialDatePicker represents its
                             * selected calendar date in UTC.
                             *
                             * Convert it into a LocalDate rather
                             * than relying on header display text.
                             */

                            LocalDate selectedDate =
                                    Instant
                                            .ofEpochMilli(
                                                    selectedMillis
                                            )
                                            .atZone(
                                                    ZoneOffset.UTC
                                            )
                                            .toLocalDate();


                            target.setText(
                                    selectedDate.format(
                                            SentenceCalculator.DISPLAY_DATE
                                    )
                            );


                            target.setError(null);
                        }
                );


        materialDatePicker.show(
                getSupportFragmentManager(),
                pickerTag
        );
    }


    // =========================================================
    // OPTIONAL INTEGER INPUT
    // =========================================================

    private int getIntOrZero(
            EditText input) {

        /*
         * Sentence component fields are optional.
         * Empty means zero years/months/days.
         */

        String value =
                input.getText()
                        .toString()
                        .trim();


        if (value.isEmpty()) {

            return 0;
        }


        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Enter non-negative whole numbers within the integer range.");
        }
    }


    // =========================================================
    // SAVE PROFILE
    // =========================================================

    private void saveProfile() {
        final LocalDate observation = LocalDate.now();

        Profile profile =
                new Profile();


        profile.userID =
                sp.getLong(
                        "currentUserID",
                        -1
                );


        // =====================================================
        // NAME VALIDATION
        // =====================================================

        if (inputFirstName
                .getText()
                .toString()
                .trim()
                .isEmpty()) {

            Snackbar.make(
                    layout,
                    "First Name cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        profile.firstName =
                inputFirstName
                        .getText()
                        .toString()
                        .trim();


        if (inputLastName
                .getText()
                .toString()
                .trim()
                .isEmpty()) {

            Snackbar.make(
                    layout,
                    "Last Name cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        profile.lastName =
                inputLastName
                        .getText()
                        .toString()
                        .trim();


        // =====================================================
        // REQUIRED BASIC DATES
        // =====================================================

        // Today is supplied to the calculator; no observation date is saved or edited.

        profile.leaArrestDate =
                inputLEADate
                        .getText()
                        .toString()
                        .trim();


        /*
         * commitmentDate is NOT written here anymore.
         *
         * TriggerFormController writes it because it
         * knows whether that visible field represents
         * jail commitment or prison commitment.
         */


        // =====================================================
        // MINIMUM PENALTY
        // =====================================================

        profile.minYears =
                getIntOrZero(
                        inputMinYears
                );

        profile.minMonths =
                getIntOrZero(
                        inputMinMonths
                );

        profile.minDays =
                getIntOrZero(
                        inputMinDays
                );


        if (profile.minYears == 0
                && profile.minMonths == 0
                && profile.minDays == 0) {

            Snackbar.make(
                    layout,
                    "Minimum Penalty Period cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        // =====================================================
        // MAXIMUM PENALTY
        // =====================================================

        profile.maxYears =
                getIntOrZero(
                        inputMaxYears
                );

        profile.maxMonths =
                getIntOrZero(
                        inputMaxMonths
                );

        profile.maxDays =
                getIntOrZero(
                        inputMaxDays
                );


        if (profile.maxYears == 0
                && profile.maxMonths == 0
                && profile.maxDays == 0) {

            Snackbar.make(
                    layout,
                    "Maximum Penalty Period cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        // =====================================================
        // STAL SELECTION
        // =====================================================

        // Save the STAL percentage used by the website calculation.

        switch (
                inputSTAL.getSelectedItemPosition()
        ) {

            case 0:

                profile.stalPercent = 0;
                break;


            case 1:

                profile.stalPercent = 20;
                break;


            case 2:

                profile.stalPercent = 40;
                break;
        }


        // =====================================================
        // EXTRA ADJUSTMENTS
        // =====================================================

        profile.deductionDays =
                getIntOrZero(
                        inputDeductionRaw
                );


        profile.timeGapDays =
                getIntOrZero(
                        inputTimeGap
                );


        // =====================================================
        // TRIGGER-SPECIFIC PROFILE DATA
        // =====================================================

        // Copy custody dates and optional drafts; validate them before calculating or saving.

        final SentenceCalculator.Result calculation;
        try {

            triggerController.writeToProfile(profile);
            profile.charges = additionalChargesController.readCharges(profile);
            calculation =
                    SentenceCalculator.calculate(ProfileCalculationMapper.from(profile, observation));
            if (!calculation.available) {
                throw new IllegalArgumentException(calculation.problem);
            }

        }
        catch (IllegalArgumentException e) {

            Snackbar.make(
                    layout,
                    e.getMessage() == null
                            ? "Check the trigger-related dates."
                            : e.getMessage(),
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        // =====================================================
        // SAVE TO ROOM
        // =====================================================

        Bundle breakdown = ProfileBreakdownDialogFragment.createArguments(
                getString(R.string.breakdown_balance_value,
                        calculation.minimumText(observation), calculation.minRemainingDays),
                getString(R.string.breakdown_balance_value,
                        calculation.maximumText(observation), calculation.maxRemainingDays),
                calculation);
        // The retained controller saves the photo reference and profile together off the UI thread.
        photoController.save(profile, true, breakdown);
    }


    // =========================================================
    // SAVE TEMPORARY UI STATE
    // =========================================================

    @Override
    protected void onSaveInstanceState(
            Bundle outState) {

        /*
         * Android automatically saves normal EditText state.
         *
         * The controller has additional dynamically-created
         * fields, so it stores those values in the Bundle too.
         */

        if (triggerController != null) {

            triggerController.saveInstanceState(
                    outState
            );
        }

        if (additionalChargesController != null) additionalChargesController.saveInstanceState(outState);


        super.onSaveInstanceState(
                outState
        );
    }


    // =========================================================
    // HOME / CANCEL
    // =========================================================

    public void goHomeNE(View v) {

        finish();
    }
}
