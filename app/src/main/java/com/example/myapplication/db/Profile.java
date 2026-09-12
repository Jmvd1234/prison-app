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

    public String chargeName;
    public String arrestDate;
    public int sentenceYears;
    public int sentenceMonths;
    public int sentenceDays;

    public int STAL;
    public int GCTA;
    public int TASTM;

    public long userID;

    public String image_path;


}
