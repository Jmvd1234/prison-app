package com.example.myapplication;

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


public class editProfileActivity extends AppCompatActivity {

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
    Button deleteEntryButton;

    AppDatabase db;
    ProfileDao profileDao;

    ConstraintLayout layout;

    int profileID;
    Profile profile;

    // Shared website-style switches, commitment date and optional conviction date.
    private TriggerFormController triggerController;
    private AdditionalChargesController additionalChargesController;
    private ProfilePhotoController photoController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_edit_profile);


        layout =
                findViewById(R.id.main);


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
        // PROFILE ID
        // =====================================================

        /*
         * The RecyclerView sends the Profile's ID when
         * its image is clicked.
         */

        profileID =
                getIntent().getIntExtra(
                        "profileID",
                        -1
                );


        if (profileID == -1) {

            finish();
            return;
        }


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


        inputDeductionRaw =
                findViewById(R.id.inputDeductionRaw);

        inputTimeGap =
                findViewById(R.id.inputTimeGap);


        inputSTAL =
                findViewById(R.id.inputSTAL);


        saveEntryButton =
                findViewById(R.id.saveEntryButton);

        deleteEntryButton =
                findViewById(R.id.deleteEntryButton);


        // =====================================================
        // DATABASE
        // =====================================================

        db =
                AppDatabase.getInstance(
                        getApplicationContext()
                );

        profileDao =
                db.ProfileDao();


        profile =
                profileDao.getProfileByProfileID(
                        profileID
                );


        if (profile == null) {

            Snackbar.make(
                    layout,
                    "Profile could not be found!",
                    Snackbar.LENGTH_LONG
            ).show();

            finish();
            return;
        }


        // =====================================================
        // STAL DROPDOWN
        // =====================================================

        // The mapper passes the stored STAL selection to the calculator.

        String[] stalOptions = {
                "None",
                "Returned After a Disaster (20%)",
                "Did Not Leave (40%)"
        };


        ArrayAdapter<String> stalAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        stalOptions
                );


        stalAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );


        inputSTAL.setAdapter(
                stalAdapter
        );


        // =====================================================
        // RESTORE ORDINARY PROFILE FIELDS
        // =====================================================

        /*
         * On a fresh opening of the edit screen, populate
         * the normal inputs from Room.
         *
         * If Android is recreating this Activity, allow
         * its normal View-state restoration instead.
         */

        if (savedInstanceState == null) {

            populateFields();
        }

        additionalChargesController = new AdditionalChargesController(
                findViewById(R.id.additionalChargesContainer), findViewById(R.id.addChargeButton),
                profile, savedInstanceState);


        // =====================================================
        // TRIGGER CONTROLLER
        // =====================================================

        // The controller owns the three switches and the single conditional date field.

        triggerController =
                new TriggerFormController(
                        this,
                        findViewById(
                                R.id.triggerDetailsContainer
                        )
                );


        /*
         * Unlike newEntryActivity, pass the existing
         * Profile so the controller can restore all the
         * trigger-related values stored in Room.
         */

        triggerController.load(
                profile,
                savedInstanceState
        );


        // =====================================================
        // NORMAL DATE PICKERS
        // =====================================================

        /*
         * Commitment date is deliberately NOT handled here.
         *
         * Its meaning can change between jail and prison,
         * so TriggerFormController owns that picker.
         */

        inputLEADate.setOnClickListener(v -> {

            showDatePickerDialog(
                    inputLEADate,
                    "Select LEA Arrest Date"
            );
        });


        // =====================================================
        // BUTTONS
        // =====================================================

        photoController = new ProfilePhotoController(this, profile.image_path, result -> finish());

        saveEntryButton.setOnClickListener(v -> {

            try {
                saveProfile();
            } catch (IllegalArgumentException error) {
                Snackbar.make(layout, error.getMessage() == null
                        ? "Check the entered numbers and dates." : error.getMessage(),
                        Snackbar.LENGTH_LONG).show();
            }
        });


        deleteEntryButton.setOnClickListener(v -> {

            deleteProfile();
        });
    }


    // =========================================================
    // POPULATE NORMAL FIELDS
    // =========================================================

    private void populateFields() {

        inputFirstName.setText(
                profile.firstName
        );

        inputLastName.setText(
                profile.lastName
        );


        inputLEADate.setText(
                profile.leaArrestDate
        );


        inputMinYears.setText(
                String.valueOf(
                        profile.minYears
                )
        );

        inputMinMonths.setText(
                String.valueOf(
                        profile.minMonths
                )
        );

        inputMinDays.setText(
                String.valueOf(
                        profile.minDays
                )
        );


        inputMaxYears.setText(
                String.valueOf(
                        profile.maxYears
                )
        );

        inputMaxMonths.setText(
                String.valueOf(
                        profile.maxMonths
                )
        );

        inputMaxDays.setText(
                String.valueOf(
                        profile.maxDays
                )
        );


        inputDeductionRaw.setText(
                String.valueOf(
                        profile.deductionDays
                )
        );

        inputTimeGap.setText(
                String.valueOf(
                        profile.timeGapDays
                )
        );


        // Restore the website's STAL selection in every trigger mode.

        if (profile.stalPercent == 20) {

            inputSTAL.setSelection(1);
        }

        else if (profile.stalPercent == 40) {

            inputSTAL.setSelection(2);
        }

        else {

            inputSTAL.setSelection(0);
        }
    }


    // =========================================================
    // NORMAL DATE PICKER
    // =========================================================

    private void showDatePickerDialog(
            EditText target,
            String title) {

        String pickerTag =
                "DATE_PICKER_" + target.getId();


        if (getSupportFragmentManager()
                .findFragmentByTag(pickerTag) != null) {

            return;
        }


        long selectedDate =
                MaterialDatePicker.todayInUtcMilliseconds();


        /*
         * If the field already contains a date,
         * open the picker on that date.
         */

        try {

            LocalDate currentDate =
                    SentenceCalculator.optionalDate(
                            target.getText()
                                    .toString()
                                    .trim(),
                            title
                    );


            if (currentDate != null) {

                selectedDate =
                        currentDate
                                .atStartOfDay(
                                        ZoneOffset.UTC
                                )
                                .toInstant()
                                .toEpochMilli();
            }

        }
        catch (IllegalArgumentException ignored) {

            // Invalid or empty input:
            // simply open on today's date.
        }


        MaterialDatePicker<Long> materialDatePicker =
                MaterialDatePicker.Builder
                        .datePicker()
                        .setTitleText(title)
                        .setSelection(selectedDate)
                        .build();


        materialDatePicker
                .addOnPositiveButtonClickListener(
                        selection -> {

                            LocalDate selected =
                                    Instant
                                            .ofEpochMilli(
                                                    selection
                                            )
                                            .atZone(
                                                    ZoneOffset.UTC
                                            )
                                            .toLocalDate();


                            target.setText(
                                    selected.format(
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
    // DELETE PROFILE
    // =========================================================

    private void deleteProfile() {
        photoController.delete(profile);
    }


    // =========================================================
    // SAVE PROFILE
    // =========================================================

    private void saveProfile() {

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
         * Commitment date is NOT written here.
         *
         * TriggerFormController knows whether the visible
         * field represents jail or prison commitment.
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
        // STAL
        // =====================================================

        switch (
                inputSTAL.getSelectedItemPosition()
        ) {

            case 1:

                profile.stalPercent = 20;
                break;


            case 2:

                profile.stalPercent = 40;
                break;


            default:

                profile.stalPercent = 0;
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
        // TRIGGER DATA
        // =====================================================

        /*
         * Copies all trigger-specific UI values into the
         * existing Profile object before Room updates it.
         */

        try {

            triggerController.writeToProfile(profile);
            profile.charges = additionalChargesController.readCharges(profile);
            SentenceCalculator.Result calculation =
                    SentenceCalculator.calculate(ProfileCalculationMapper.from(profile));
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
        // UPDATE ROOM
        // =====================================================

        photoController.save(profile, false, null);
    }


    // =========================================================
    // OPTIONAL INTEGER
    // =========================================================

    private int getIntOrZero(
            EditText input) {

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
    // SAVE TEMPORARY TRIGGER STATE
    // =========================================================

    @Override
    protected void onSaveInstanceState(
            Bundle outState) {

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
