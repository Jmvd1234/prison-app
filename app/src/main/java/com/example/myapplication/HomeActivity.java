package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.ImageButton;

public class HomeActivity extends AppCompatActivity {

    ImageButton homeBackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        homeBackButton = findViewById(R.id.homeBackButton);
        homeBackButton.setOnClickListener(v -> {finish();});

    }

    public void open_dashboard(View v){
        Intent i = new Intent(this, DashboardActivity.class);
        startActivity(i);
    }

    public void make_new_entry(View v){
        Intent i = new Intent(this, newEntryActivity.class);
        startActivity(i);
    }

    public void contact_help(View v){
        Intent i = new Intent(this, contactHelpActivity.class);
        startActivity(i);
    }

    public void logOut(View v){
        Intent i = new Intent(this, MainActivity.class);
        startActivity(i);
        getSharedPreferences("user_session", MODE_PRIVATE)
                .edit()
                .remove("currentUserID")
                .apply();
        finish();
    }
}