package com.example.myapplication.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {User.class, Profile.class, ProfileCharge.class, ProfileDeduction.class}, version=10)
public abstract class AppDatabase extends RoomDatabase {

    // Keep existing accounts, entries, charges and photos when installing this update.
    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE User ADD COLUMN passwordHash TEXT");
            db.execSQL("ALTER TABLE User ADD COLUMN isGuest INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Old prisonCommitmentDate values were switch drafts, not confirmed transfers.
            db.execSQL("ALTER TABLE Profile ADD COLUMN prisonTransferDate TEXT");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ProfileDeduction (profileID INTEGER NOT NULL, year INTEGER NOT NULL, month INTEGER NOT NULL, gcta INTEGER NOT NULL, tastm INTEGER NOT NULL, PRIMARY KEY(profileID, year, month), FOREIGN KEY(profileID) REFERENCES Profile(profileID) ON UPDATE NO ACTION ON DELETE CASCADE)");
        }
    };

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            // Do not reinterpret old switch drafts as an explicitly recorded CPI jail stage.
            db.execSQL("ALTER TABLE Profile ADD COLUMN separateCustodyDates INTEGER NOT NULL DEFAULT 0");
        }
    };

    public abstract UserDao UserDao();
    public abstract ProfileDao ProfileDao();

    private static AppDatabase INSTANCE;

    public static synchronized AppDatabase getInstance(Context context) {
        if(INSTANCE == null) {
            //apparently concept of Singleton, ig if it exists, keep it, otherwise make it
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "prison_app_db")
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .allowMainThreadQueries()
                    .build();
        }
        return INSTANCE;
    }
}
