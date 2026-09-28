package com.example.myapplication.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.Period;

@Entity(
        //Basically this is the code for establishing a foreign key
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "userID",
                childColumns = "userID",
                //cascade means if underlying User class deleted then any profiles linked to it are all deleted
                onDelete = ForeignKey.CASCADE),
        indices = @Index("userID")
)
public class Profile {

    @PrimaryKey(autoGenerate = true)
    public int profileID;
    public String firstName;
    public String lastName;

    public String observationDate;
    public String leaArrestDate;
    public String commitmentDate;

    public int minYears;
    public int minMonths;
    public int minDays;

    public int maxYears;
    public int maxMonths;
    public int maxDays;

    public boolean notCommittedToJail;
    public boolean disqualifiedForCPI;
    public boolean disqualifiedForTimeAllowances;

    public int stalPercent;

    public int deductionDays;
    public int timeGapDays;

    public long userID;

    public String image_path;


}
