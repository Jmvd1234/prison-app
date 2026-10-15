package com.example.myapplication.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface UserDao {
    @Query("SELECT * FROM User")
    List<User> getAllUsers();

    @Query("SELECT * FROM User ORDER BY isGuest DESC, username COLLATE NOCASE, userID")
    List<User> getLoginProfiles();

    @Query("SELECT * FROM User WHERE isGuest = 1 LIMIT 1")
    User getGuest();

    @Query("SELECT COUNT(*) FROM User WHERE username = :username COLLATE NOCASE")
    int countLoginNames(String username);

    @Query("UPDATE User SET passwordHash = :hash, password = NULL WHERE userID = :id")
    void upgradePassword(int id, String hash);

    @Insert
    long insertUser(User user);

    @Query("SELECT * FROM User where username = :username LIMIT 1")
    User getUserByUsername(String username);

    @Query("SELECT * FROM User where userID = :userID LIMIT 1")
    User getUserByID(int userID);

    @Query("SELECT COUNT(*) FROM User where username = :username")
    int countUsersWithUsername(String username);

    @Delete
    void deleteUser(User user);

    @Update
    void updateUser(User user);



}
