package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
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
import com.example.myapplication.db.User;
import com.example.myapplication.db.UserDao;
import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {

    EditText signInUsername;
    EditText signInPassword;
    Button signInButton;
    ConstraintLayout layout;

    AppDatabase db;
    UserDao userDao;

    SharedPreferences sp;


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

        //Initializing inside oncreate so it does not crash my thing
        signInUsername = findViewById(R.id.signInUsername);
        signInPassword = findViewById(R.id.signInPassword);
        signInButton = findViewById(R.id.signInButton);
        layout = findViewById(R.id.main);

        db = AppDatabase.getInstance(this);
        userDao = db.UserDao();

        sp = getSharedPreferences("user_session", Context.MODE_PRIVATE);

    }


    public void signIn(View v){
//    //For now will just launch sign in page, when I set up databases will add a check into a function checking
//    //if valid credentials, and only then redirect, otherwise it'll be an alert saying invalid
//        Intent i = new Intent(this, HomeActivity.class);
//        //this next code is what switches the activity
//        startActivity(i);

        String input_username = signInUsername.getText().toString();
        String input_password = signInPassword.getText().toString();
        User current_user = userDao.getUserByUsername(input_username);
        if (current_user == null) {
            Snackbar.make(layout, "User \"" + input_username + "\" does not exist.", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
        }
        else if (input_password.equals(current_user.password)) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putLong("currentUserID", current_user.userID);
            editor.apply();
            Intent i = new Intent(this, HomeActivity.class);
            //this next code is what switches the activity
            startActivity(i);
        }
        else {
            Snackbar.make(layout, "Invalid Password!", Snackbar.LENGTH_LONG)
                    .setAction("Close", view -> {})
                    .show();
        }


    }
}