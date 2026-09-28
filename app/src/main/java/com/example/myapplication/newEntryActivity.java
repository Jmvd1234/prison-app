package com.example.myapplication;

import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

public class newEntryActivity extends AppCompatActivity {

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

    SharedPreferences sp;

    AppDatabase db;
    ProfileDao profileDao;
    ConstraintLayout layout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_new_entry);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

        //Makes the default observation date today's date WHEN THE ACTIVITY IS FIRST CREATED
        if (savedInstanceState == null) { //prevents rewriting the date if there is a saved state that is NOT null
            SimpleDateFormat formatter =
                    new SimpleDateFormat("MMM d, yyyy", Locale.US);

            inputObservationDate.setText(
                    formatter.format(new Date())
            );
        }

        //Onclick listeners to input/edit the different  dates.
        //Note: Storing observation date lowkey does not make sense, we edit this LATERRR frick
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

        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveEntryButton.setOnClickListener(v -> {
            saveProfile();
        });

        deleteEntryButton = findViewById(R.id.deleteEntryButton);
        deleteEntryButton.setOnClickListener(v -> {
            finish();
        });

        sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);

        db = AppDatabase.getInstance(this.getApplicationContext());
        profileDao = db.ProfileDao();

        layout = findViewById(R.id.main);

        //STAL is a special type of deduction
        //Basically removes a percentage of an inmate's deduction based on different scenarios
        //which are presented here
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

        //IF not committed to jail
        //need to change the text from jail commitment date to prison commitment date
        //TODO: Check how this changes the calculations as well
        committedSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (isChecked) {
                        commitmentDateLabel.setText(
                                "Prison Commitment Date"
                        );
                    }
                    else {
                        commitmentDateLabel.setText(
                                "Jail Commitment Date"
                        );
                    }
                }
        );

        //Along with subtracting the other time allowances
        //This function means that it should also not accept inputs
        //from the STAL dropdown
        //Value preserved BUT does not mean it will be used in the calculations
        disqualifiedTASwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    inputSTAL.setEnabled(!isChecked);

                }
        );

    }

    //two inputs: the id of the field being edited, and the title of the date picker
    private void showDatePickerDialog(EditText target, String title) {

        MaterialDatePicker<Long> materialDatePicker =
                MaterialDatePicker.Builder.datePicker()
                        .setTitleText(title)
                        .setSelection(
                                MaterialDatePicker.todayInUtcMilliseconds()
                        )
                        .build();

        materialDatePicker.addOnPositiveButtonClickListener(selection -> {
            //updates the displayed date inside the text box based on what is chosen
            target.setText(materialDatePicker.getHeaderText());
        });

        materialDatePicker.show(
                getSupportFragmentManager(),
                "DATE_PICKER"
        );
    }

    private int getIntOrZero(EditText input) {
        //For the input years, because some of their inputs are optional and should just return zero
        //therefore will just save 0 if they are empty

        if (input.getText().toString().isEmpty()) {
            return 0;
        }

        return Integer.parseInt(
                input.getText().toString()
        );
    }

    private void saveProfile() {

        Profile profile = new Profile();

        profile.userID =
                sp.getLong("currentUserID", -1);



        //saving + validating first name
        if (inputFirstName.getText().toString().trim().isEmpty()) {

            Snackbar.make(
                    layout,
                    "First Name cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }

        profile.firstName =
                inputFirstName.getText().toString().trim();


        //saving + validating last name
        if (inputLastName.getText().toString().trim().isEmpty()) {

            Snackbar.make(
                    layout,
                    "Last Name cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }

        profile.lastName =
                inputLastName.getText().toString().trim();


        //Validating all dates
        if (inputObservationDate.getText().toString().isEmpty()) {

            Snackbar.make(
                    layout,
                    "Observation Date cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }

        if (inputLEADate.getText().toString().isEmpty()) {

            Snackbar.make(
                    layout,
                    "LEA Arrest Date cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }

        if (inputCommitmentDate.getText().toString().isEmpty()) {

            Snackbar.make(
                    layout,
                    "Commitment Date cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        profile.observationDate =
                inputObservationDate.getText().toString();

        profile.leaArrestDate =
                inputLEADate.getText().toString();

        profile.commitmentDate =
                inputCommitmentDate.getText().toString();


        //getting minimum penalty times
        profile.minYears =
                getIntOrZero(inputMinYears);

        profile.minMonths =
                getIntOrZero(inputMinMonths);

        profile.minDays =
                getIntOrZero(inputMinDays);


        if (profile.minYears
                + profile.minMonths
                + profile.minDays == 0) {

            Snackbar.make(
                    layout,
                    "Minimum Penalty Period cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }



        //getting maximum penalty times
        profile.maxYears =
                getIntOrZero(inputMaxYears);

        profile.maxMonths =
                getIntOrZero(inputMaxMonths);

        profile.maxDays =
                getIntOrZero(inputMaxDays);


        if (profile.maxYears
                + profile.maxMonths
                + profile.maxDays == 0) {

            Snackbar.make(
                    layout,
                    "Maximum Penalty Period cannot be empty!",
                    Snackbar.LENGTH_LONG
            ).show();

            return;
        }


        //saving how the switches are triggered
        profile.notCommittedToJail =
                committedSwitch.isChecked();

        profile.disqualifiedForCPI =
                disqualifiedCPISwitch.isChecked();

        profile.disqualifiedForTimeAllowances =
                disqualifiedTASwitch.isChecked();



        //Because inputSTAL uses an array for a dropdown
        //can use a switch-case statement for different outcomes
        //Based on the current part of the array selected by the dropdown
        switch (inputSTAL.getSelectedItemPosition()) {

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



        //storing extra adjustments
        //TODO: Make sense of these
        profile.deductionDays =
                getIntOrZero(inputDeductionRaw);

        profile.timeGapDays =
                getIntOrZero(inputTimeGap);


        profile.image_path = "";



        //Saving everything to Room
        new Thread(() -> {

            profileDao.insertProfile(profile);

            runOnUiThread(() -> finish());

        }).start();
    }

    public void goHomeNE(View v){
        finish();
    }
}