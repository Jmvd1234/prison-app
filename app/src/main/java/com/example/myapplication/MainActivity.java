package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        //creating event listener so the register text redirects to the register page
        TextView registerPrompt = findViewById(R.id.registerPrompt);

        registerPrompt.setOnClickListener(view -> {
            //apparently the arrow is java's lambda which lets you make one-time use functions seamlessly, in this case the onclick listener
            Intent i = new Intent(this, RegisterActivity.class);
            startActivity(i);
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText signInUsername = findViewById(R.id.signInUsername);
        EditText signInPassword = findViewById(R.id.signInPassword);
        Button signInButton = findViewById(R.id.signInButton);

    }

    public void signIn(View v){
    //For now will just launch sign in page, when I set up databases will add a check into a function checking
    //if valid credentials, and only then redirect, otherwise it'll be an alert saying invalid
        Intent i = new Intent(this, HomeActivity.class);
        //this next code is what switches the activity
        startActivity(i);
    }
}