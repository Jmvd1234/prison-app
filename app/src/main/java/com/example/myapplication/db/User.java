package com.example.myapplication.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class User {

    @PrimaryKey(autoGenerate = true)
    public int userID;

    @ColumnInfo(name="username")
    public String username;

    @ColumnInfo(name="password")
    // Legacy passwords are upgraded to a salted hash after a successful unlock.
    public String password;

    public String passwordHash;

    @ColumnInfo(defaultValue = "0")
    public boolean isGuest;

    public boolean requiresPassword() {
        return !isGuest && ((passwordHash != null && !passwordHash.isEmpty())
                || (password != null && !password.isEmpty()));
    }
}
