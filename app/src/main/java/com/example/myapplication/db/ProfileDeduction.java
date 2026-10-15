package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import com.example.myapplication.DeductionMonth;

@Entity(primaryKeys = {"profileID", "year", "month"}, foreignKeys = @ForeignKey(
        entity = Profile.class, parentColumns = "profileID", childColumns = "profileID",
        onDelete = ForeignKey.CASCADE))
public class ProfileDeduction {
    public int profileID, year, month;
    public boolean gcta, tastm;

    public DeductionMonth toMonth() { return new DeductionMonth(year, month, gcta, tastm); }
    public static ProfileDeduction from(int profileID, DeductionMonth value) {
        ProfileDeduction row = new ProfileDeduction();
        row.profileID = profileID; row.year = value.year; row.month = value.month;
        row.gcta = value.gcta; row.tastm = value.tastm;
        return row;
    }
}
