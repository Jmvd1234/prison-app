package com.example.myapplication.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {User.class, Profile.class}, version=1)
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao UserDao();
    public abstract ProfileDao ProfileDao();

    private static AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if(INSTANCE == null) {
            //apparently concept of Singleton, ig if it exists, keep it, otherwise make it
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "prison_app_db")
                    .allowMainThreadQueries()
                    .build();
        }
        return INSTANCE;
    }
}
