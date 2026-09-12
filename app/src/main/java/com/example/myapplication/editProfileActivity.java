package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.snackbar.Snackbar;

import java.util.Calendar;

public class editProfileActivity extends AppCompatActivity {

    EditText inputDate;
    EditText inputFirstName;
    EditText inputLastName;
    EditText inputYears;
    EditText inputMonths;
    EditText inputDays;
    EditText inputCrime;
    EditText inputGCTA;
    EditText inputSTAL;
    EditText inputTASTM;

    String selectedArrestDate;

    Button saveEntryButton;
    Button deleteEntryButton;

    SharedPreferences sp;

    AppDatabase db;
    ProfileDao profileDao;
    ConstraintLayout layout;

    Calendar maxDate;
    CalendarConstraints constraints;
    int profileID;
    Profile profile;

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
        //storing the profileID transferred from the other file

        inputFirstName = findViewById(R.id.inputFirstName);
        inputLastName = findViewById(R.id.inputLastName);
        inputYears = findViewById(R.id.inputYears);
        inputMonths = findViewById(R.id.inputMonths);
        inputDays = findViewById(R.id.inputDays);
        inputCrime = findViewById(R.id.inputCrime);
        inputGCTA = findViewById(R.id.inputGCTA);
        inputSTAL = findViewById(R.id.inputSTAL);
        inputTASTM = findViewById(R.id.inputTASTM);


        inputDate = findViewById(R.id.inputDate);
        inputDate.setOnClickListener(v -> {
            showDatePickerDialog();
        });

        saveEntryButton = findViewById(R.id.saveEntryButton);
        saveEntryButton.setOnClickListener(v -> {
            saveProfile();
        });

        deleteEntryButton = findViewById(R.id.deleteEntryButton);
        deleteEntryButton.setOnClickListener(v -> {
            deleteProfile();
        });

        sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);

        db = AppDatabase.getInstance(this.getApplicationContext());
        profileDao = db.ProfileDao();
        System.out.println("PASSED PROFILE ID: " + profileID);

        profile = profileDao.getProfileByProfileID(profileID);

        System.out.println("PROFILE FOUND: " + profile);
        layout = findViewById(R.id.main);

        //Setting initial field values
        inputFirstName.setText(profile.firstName);
        inputLastName.setText(profile.lastName);
        inputYears.setText(String.valueOf(profile.sentenceYears));
        inputMonths.setText(String.valueOf(profile.sentenceMonths));
        inputDays.setText(String.valueOf(profile.sentenceDays));
        inputCrime.setText(profile.chargeName);
        inputGCTA.setText(String.valueOf(profile.GCTA));
        inputSTAL.setText(String.valueOf(profile.STAL));
        inputTASTM.setText(String.valueOf(profile.TASTM));
        inputDate.setText(profile.arrestDate);

        //setting constraints for the date picker
        maxDate = Calendar.getInstance();

        //will only allow dates before or including the current date
        constraints = new CalendarConstraints.Builder()
                .setValidator(DateValidatorPointBackward.before(maxDate.getTimeInMillis()))
                .build();
    }
    private void showDatePickerDialog() {
        MaterialDatePicker materialDatePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setCalendarConstraints(constraints)
                .build();

        materialDatePicker.addOnPositiveButtonClickListener(new MaterialPickerOnPositiveButtonClickListener() {
            @Override
            public void onPositiveButtonClick(Object o) {
                inputDate.setText("" + materialDatePicker.getHeaderText());
            }
        });

        materialDatePicker.show(getSupportFragmentManager(), "TAG");
    }

    private void deleteProfile() {
        new Thread(()-> {
            profileDao.deleteUser(profile);
            runOnUiThread(() -> finish());
        }).start();
    }

    private void saveProfile() {
        /// Note: Need to add validation. Check if null, if valid range, etc

        if (inputFirstName.getText().toString().isEmpty()) {
            Snackbar.make(layout, "First Name cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
            return;
        }
        profile.firstName = inputFirstName.getText().toString();
        if (inputLastName.getText().toString().isEmpty()) {
            Snackbar.make(layout, "Last Name cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
            return;
        }
        profile.lastName = inputLastName.getText().toString();
        //Default inputs of 0 for the integer inputs
        if (inputDate.getText().toString().isEmpty()) {
            Snackbar.make(layout, "Incarceration Date cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
            return;
        }
        profile.arrestDate = inputDate.getText().toString();
        profile.sentenceYears = inputYears.getText().toString().isEmpty()
                ? 0
                : Integer.parseInt(inputYears.getText().toString());
        profile.sentenceMonths = inputMonths.getText().toString().isEmpty()
                ? 0
                : Integer.parseInt(inputMonths.getText().toString());
        profile.sentenceDays = inputDays.getText().toString().isEmpty()
                ? 0
                : Integer.parseInt(inputDays.getText().toString());
        if (profile.sentenceYears + profile.sentenceMonths + profile.sentenceDays == 0) {
            Snackbar.make(layout, "Sentence Length cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
            return;
        }
        profile.STAL = inputSTAL.getText().toString().isEmpty()
                ? 0
                : Integer.parseInt(inputSTAL.getText().toString());
        profile.GCTA = inputGCTA.getText().toString().isEmpty()
                ? 0
                : Integer.parseInt(inputGCTA.getText().toString());
        profile.TASTM = inputTASTM.getText().toString().isEmpty()
                ? 0
                : Integer.parseInt(inputTASTM.getText().toString());
        if (inputCrime.getText().toString().isEmpty()) {
            Snackbar.make(layout, "Crime Charged cannot be empty!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
            return;
        }
        profile.chargeName = inputCrime.getText().toString();
        profile.image_path = "";

        new Thread(() -> {
            profileDao.updateUser(profile);
            runOnUiThread(() -> finish());
        }).start();

    }

    public void goHomeNE(View v){
        finish();
    }
}