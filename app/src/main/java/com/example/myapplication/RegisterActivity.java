package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.User;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.UserDao;
import com.google.android.material.snackbar.Snackbar;

public class RegisterActivity extends AppCompatActivity {

    AppDatabase db;
    UserDao userDao;
    SharedPreferences sp;
    ConstraintLayout layout;
    ImageButton registerBackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registerLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        final EditText registerUsername = findViewById(R.id.registerUsername);
        final EditText registerPassword = findViewById(R.id.registerPassword);
        Button registerButton = findViewById(R.id.registerButton);
        registerButton.setOnClickListener(v -> saveNewUser(registerUsername.getText().toString(), registerPassword.getText().toString()));
        registerBackButton = findViewById(R.id.registerBackButton);
        registerBackButton.setOnClickListener(v -> {finish();});
        db = AppDatabase.getInstance(this.getApplicationContext());
        userDao = db.UserDao();
        layout = findViewById(R.id.registerLayout);

        sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);
    }

    private void saveNewUser(String input_username, String input_password) {
        //WILL NEED TO ADD FRICKIONG VALIDATION!! THAT NO OTHER USER EXSISTS, NOR THIS CONTAINS INVALID CHARACTERS
        if (userDao.countUsersWithUsername(input_username) > 0) {
            Snackbar.make(layout, "User \"" + input_username + "\" already exists!.", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
        }
        else {
            User user = new User();
            user.username = input_username;
            user.password=input_password;
            long user_ID = db.UserDao().insertUser(user);
            SharedPreferences.Editor editor = sp.edit();
            editor.putLong("currentUserID", user_ID);
            editor.apply();
            Intent i = new Intent(this, HomeActivity.class);
            //this next code is what switches the activity
            startActivity(i);
            finish();
        }
    }

}