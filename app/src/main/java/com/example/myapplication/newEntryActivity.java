package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.Profile;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;

public class newEntryActivity extends AppCompatActivity {

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

    Button saveEntryButton;

    SharedPreferences sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);

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
            finish();
        });

    }

    private void showDatePickerDialog() {
        MaterialDatePicker materialDatePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date").build();

        materialDatePicker.addOnPositiveButtonClickListener(new MaterialPickerOnPositiveButtonClickListener() {
            @Override
            public void onPositiveButtonClick(Object o) {
                inputDate.setText("" + materialDatePicker.getHeaderText());
            }
        });

        materialDatePicker.show(getSupportFragmentManager(), "TAG");
    }

    private void saveProfile() {
        Profile profile = new Profile();
        profile.userID = sp.getLong("currentUserID", -1);
        //default value of -1 if the user ID is not found for any reason
        profile.firstName = inputFirstName.getText().toString();
        profile.lastName = inputLastName.getText().toString();
        profile.sentenceYears = Integer.parseInt(inputYears.getText().toString());
        profile.sentenceMonths = Integer.parseInt(inputMonths.getText().toString());
        profile.sentenceDays = Integer.parseInt(inputMonths.getText().toString());
        profile.STAL = Integer.parseInt(inputSTAL.getText().toString());
        profile.GCTA = Integer.parseInt(inputGCTA.getText().toString());
        profile.TASTM = Integer.parseInt(inputTASTM.getText().toString());
        profile.image_path = "";
    }

    public void goHomeNE(View v){
        finish();
    }
}