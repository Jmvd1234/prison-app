package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

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
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class editProfileActivity extends AppCompatActivity {

    EditText inputFirstName;
    EditText inputLastName;

    EditText inputObservationDate;
    EditText inputLEADate;
    EditText inputCommitmentDate;

    EditText inputMinYears;
    EditText inputMinMonths;
    EditText inputMinDays;

    EditText inputMaxYears;
    EditText inputMaxMonths;
    EditText inputMaxDays;

    EditText inputDeductionRaw;
    EditText inputTimeGap;

    Spinner inputSTAL;

    MaterialSwitch committedSwitch;
    MaterialSwitch disqualifiedCPISwitch;
    MaterialSwitch disqualifiedTASwitch;

    TextView commitmentDateLabel;

    Button saveEntryButton;
    Button deleteEntryButton;

    AppDatabase db;
    ProfileDao profileDao;

    ConstraintLayout layout;

    int profileID;
    Profile profile;

    DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern(
                    "MMM d, uuuu",
                    Locale.US
            );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        profileID = getIntent().getIntExtra("profileID", -1);
        //storing the profileID transferred from the recyclerview

        if (profileID == -1) {
            finish();
            return;
        }


        inputFirstName = findViewById(R.id.inputFirstName);
        inputLastName = findViewById(R.id.inputLastName);

        inputObservationDate = findViewById(R.id.inputObservationDate);
        inputLEADate = findViewById(R.id.inputLEADate);
        inputCommitmentDate = findViewById(R.id.inputCommitmentDate);

        inputMinYears = findViewById(R.id.inputMinYears);
        inputMinMonths = findViewById(R.id.inputMinMonths);
        inputMinDays = findViewById(R.id.inputMinDays);

        inputMaxYears = findViewById(R.id.inputMaxYears);
        inputMaxMonths = findViewById(R.id.inputMaxMonths);
        inputMaxDays = findViewById(R.id.inputMaxDays);

        inputDeductionRaw = findViewById(R.id.inputDeductionRaw);
        inputTimeGap = findViewById(R.id.inputTimeGap);

        inputSTAL = findViewById(R.id.inputSTAL);

        committedSwitch = findViewById(R.id.committedSwitch);
        disqualifiedCPISwitch = findViewById(R.id.disqualifiedCPISwitch);
        disqualifiedTASwitch = findViewById(R.id.disqualifiedTASwitch);

        commitmentDateLabel = findViewById(R.id.commitmentDateLabel);

        saveEntryButton = findViewById(R.id.saveEntryButton);
        deleteEntryButton = findViewById(R.id.deleteEntryButton);

        layout = findViewById(R.id.main);


        db = AppDatabase.getInstance(this.getApplicationContext());
        profileDao = db.ProfileDao();

        profile = profileDao.getProfileByProfileID(profileID);

        if (profile == null) {
            Snackbar.make(layout, "Profile could not be found!", Snackbar.LENGTH_LONG)
                    .show();

            finish();
            return;
        }


        //setting up the STAL dropdown
        String[] stalOptions = {
                "None",
                "Returned After a Disaster (20%)",
                "Did Not Leave (40%)"
        };

        ArrayAdapter<String> stalAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                stalOptions
        );

        stalAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
        );

        inputSTAL.setAdapter(stalAdapter);


        //changes the commitment date label based on the toggle
        committedSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    updateCommitmentLabel();
                }
        );


        //if disqualified, STAL is not considered in the calculations
        disqualifiedTASwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    inputSTAL.setEnabled(!isChecked);
                }
        );


        //Setting initial field values
        if (savedInstanceState == null) {
            populateFields();
        }


        inputObservationDate.setOnClickListener(v -> {
            showDatePickerDialog(
                    inputObservationDate,
                    "Select Observation Date"
            );
        });

        inputLEADate.setOnClickListener(v -> {
            showDatePickerDialog(
                    inputLEADate,
                    "Select LEA Arrest Date"
            );
        });

        inputCommitmentDate.setOnClickListener(v -> {
            showDatePickerDialog(
                    inputCommitmentDate,
                    commitmentDateLabel.getText().toString()
            );
        });


        saveEntryButton.setOnClickListener(v -> {
            saveProfile();
        });

        deleteEntryButton.setOnClickListener(v -> {
            deleteProfile();
        });
    }


    private void populateFields() {

        inputFirstName.setText(profile.firstName);
        inputLastName.setText(profile.lastName);

        inputObservationDate.setText(profile.observationDate);
        inputLEADate.setText(profile.leaArrestDate);
        inputCommitmentDate.setText(profile.commitmentDate);

        inputMinYears.setText(String.valueOf(profile.minYears));
        inputMinMonths.setText(String.valueOf(profile.minMonths));
        inputMinDays.setText(String.valueOf(profile.minDays));

        inputMaxYears.setText(String.valueOf(profile.maxYears));
        inputMaxMonths.setText(String.valueOf(profile.maxMonths));
        inputMaxDays.setText(String.valueOf(profile.maxDays));

        inputDeductionRaw.setText(String.valueOf(profile.deductionDays));
        inputTimeGap.setText(String.valueOf(profile.timeGapDays));


        //Setting initial toggle values
        committedSwitch.setChecked(profile.notCommittedToJail);
        disqualifiedCPISwitch.setChecked(profile.disqualifiedForCPI);
        disqualifiedTASwitch.setChecked(profile.disqualifiedForTimeAllowances);

        updateCommitmentLabel();

        inputSTAL.setEnabled(
                !profile.disqualifiedForTimeAllowances
        );


        //Setting initial STAL dropdown value
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


    private void updateCommitmentLabel() {

        if (committedSwitch.isChecked()) {
            commitmentDateLabel.setText("Prison Commitment Date");
        }
        else {
            commitmentDateLabel.setText("Jail Commitment Date");
        }
    }


    private void showDatePickerDialog(EditText target, String title) {

        //by default opens on today's date
        long selectedDate =
                MaterialDatePicker.todayInUtcMilliseconds();


        //if editing an existing date, opens the picker on that date instead
        try {
            LocalDate currentDate =
                    LocalDate.parse(
                            target.getText().toString(),
                            dateFormatter
                    );

            selectedDate =
                    currentDate
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .toEpochMilli();
        }
        catch (DateTimeParseException ignored) {
        }


        MaterialDatePicker<Long> materialDatePicker =
                MaterialDatePicker.Builder.datePicker()
                        .setTitleText(title)
                        .setSelection(selectedDate)
                        .build();


        materialDatePicker.addOnPositiveButtonClickListener(selection -> {

            LocalDate selected =
                    Instant.ofEpochMilli(selection)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate();

            target.setText(
                    selected.format(dateFormatter)
            );
        });


        materialDatePicker.show(
                getSupportFragmentManager(),
                "DATE_PICKER"
        );
    }


    private void deleteProfile() {

        new Thread(() -> {

            profileDao.deleteUser(profile);

            runOnUiThread(() -> finish());

        }).start();
    }


    private void saveProfile() {
        /// Note: Need to add more validation. Check valid date order/ranges etc

        if (inputFirstName.getText().toString().trim().isEmpty()) {

            Snackbar.make(layout, "First Name cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }

        profile.firstName =
                inputFirstName.getText().toString().trim();


        if (inputLastName.getText().toString().trim().isEmpty()) {

            Snackbar.make(layout, "Last Name cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }

        profile.lastName =
                inputLastName.getText().toString().trim();


        if (inputObservationDate.getText().toString().isEmpty()) {

            Snackbar.make(layout, "Observation Date cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }


        if (inputLEADate.getText().toString().isEmpty()) {

            Snackbar.make(layout, "LEA Arrest Date cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }


        if (inputCommitmentDate.getText().toString().isEmpty()) {

            Snackbar.make(layout, "Commitment Date cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }


        profile.observationDate =
                inputObservationDate.getText().toString();

        profile.leaArrestDate =
                inputLEADate.getText().toString();

        profile.commitmentDate =
                inputCommitmentDate.getText().toString();


        //Default inputs of 0 for the integer inputs
        profile.minYears = getIntOrZero(inputMinYears);
        profile.minMonths = getIntOrZero(inputMinMonths);
        profile.minDays = getIntOrZero(inputMinDays);


        if (profile.minYears
                + profile.minMonths
                + profile.minDays == 0) {

            Snackbar.make(layout, "Minimum Penalty Period cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }


        profile.maxYears = getIntOrZero(inputMaxYears);
        profile.maxMonths = getIntOrZero(inputMaxMonths);
        profile.maxDays = getIntOrZero(inputMaxDays);


        if (profile.maxYears
                + profile.maxMonths
                + profile.maxDays == 0) {

            Snackbar.make(layout, "Maximum Penalty Period cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();

            return;
        }


        profile.notCommittedToJail =
                committedSwitch.isChecked();

        profile.disqualifiedForCPI =
                disqualifiedCPISwitch.isChecked();

        profile.disqualifiedForTimeAllowances =
                disqualifiedTASwitch.isChecked();


        //saving the actual STAL percentage rather than dropdown position
        switch (inputSTAL.getSelectedItemPosition()) {

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


        profile.deductionDays =
                getIntOrZero(inputDeductionRaw);

        profile.timeGapDays =
                getIntOrZero(inputTimeGap);


        new Thread(() -> {

            profileDao.updateUser(profile);

            runOnUiThread(() -> finish());

        }).start();
    }


    private int getIntOrZero(EditText input) {

        if (input.getText().toString().isEmpty()) {
            return 0;
        }

        return Integer.parseInt(
                input.getText().toString()
        );
    }


    public void goHomeNE(View v) {
        finish();
    }
}