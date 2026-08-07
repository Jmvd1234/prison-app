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
    //REALLY BAD NEED TO CHANGE TO HASHING JUST TO GET THIS TEST SHIT OFF THE GROUND
    public String password;
}
