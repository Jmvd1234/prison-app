package com.example.myapplication.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {User.class}, version=1)
public abstract class appDatabase extends RoomDatabase {

    public abstract UserDao UserDao();

    private static appDatabase INSTANCE;

    public static appDatabase getInstance(Context context) {
        if(INSTANCE == null) {
            //apparently concept of Singleton, ig if it exists, keep it, otherwise make it
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(), appDatabase.class, "prison_app_db")
                    .allowMainThreadQueries()
                    .build();
        }
        return INSTANCE;
    }
}
