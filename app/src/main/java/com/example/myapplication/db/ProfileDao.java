package com.example.myapplication.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProfileDao {

    @Insert
    long insertProfile(Profile profile);

    @Query("SELECT * FROM Profile where userID = :userID")
    List<Profile> getProfilesByUserID(long userID);

    @Query("SELECT * FROM Profile where profileID = :profileID LIMIT 1")
    Profile getProfileByProfileID(int profileID);

    @Delete
    void deleteUser(Profile profile);

    @Update
    void updateUser(Profile profile);
}
