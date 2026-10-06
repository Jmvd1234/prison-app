package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.Ignore;
import java.util.ArrayList;
import java.util.List;

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

    // Unused legacy snapshot. Live calculations use today's/preview date.
    public String observationDate;
    public String leaArrestDate;
    public String commitmentDate;

    // The first form row uses these temporary fields. All penalties are stored in ProfileCharge.
    @Ignore public int minYears;
    @Ignore public int minMonths;
    @Ignore public int minDays;
    @Ignore public int maxYears;
    @Ignore public int maxMonths;
    @Ignore public int maxDays;
    @Ignore public List<ProfileCharge> charges = new ArrayList<>();

    public boolean notCommittedToJail;
    public boolean disqualifiedForCPI;
    public boolean disqualifiedForTimeAllowances;

    public int stalPercent;

    public int deductionDays;
    public int timeGapDays;

    public long userID;

    public String image_path;

    // Optional website conviction input; used only when TA is checked and CPI is not.
    public String convictionDate;
    // Unused legacy fields; these dates and the timeline flag do not gate calculations.
    public String sentenceServiceStartDate; // Verified enforcement start; nullable.
    public String imprisonmentStartDate;    // Original allowance/tier clock.
    public String jailCommitmentDate;       // Preserved jail-date input.
    public String prisonCommitmentDate;     // Separate prison-date input.

    @androidx.room.ColumnInfo(defaultValue = "0")
    public int cpiCreditPercent = 0;         // Legacy column; website calculations ignore it.

    @androidx.room.ColumnInfo(defaultValue = "0")
    public boolean custodyTimelineConfirmed = false;

}
