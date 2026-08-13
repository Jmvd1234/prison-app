package com.example.myapplication.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface ProfileDao {

    @Insert
    long insertProfile(Profile profile);

    @Query("SELECT * FROM Profile where userID = :userID")
    Profile getProfilesByUserID(int userID);

    @Query("SELECT * FROM Profile where profileID = :profileID")
    Profile getProfilesByProfileID(int profileID);

    @Delete
    void deleteUser(Profile profile);

    @Update
    void updateUser(Profile profile);
}
