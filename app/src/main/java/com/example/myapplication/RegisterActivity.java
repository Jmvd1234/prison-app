package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.db.User;
import com.example.myapplication.db.appDatabase;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        final EditText registerUsername = findViewById(R.id.registerUsername);
        final EditText registerPassword = findViewById(R.id.registerUsername);
        Button registerButton = findViewById(R.id.registerButton);
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNewUser(registerUsername.getText().toString(), registerPassword.getText().toString());
            }
        });
    }

    private void saveNewUser(String input_username, String input_password) {
        //WILL NEED TO ADD FRICKIONG VALIDATION!! THAT NO OTHER USER EXSISTS, NOR THIS CONTAINS INVALID CHARACTERS
        appDatabase db = appDatabase.getInstance(this.getApplicationContext());
        User user = new User();
        user.username = input_username;
        user.password=input_password;
        db.UserDao().insertUser(user);

        finish();
    }

}