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
    // Effective calculation anchor: jail normally, prison for Not Committed/CPI.
    public String commitmentDate;
    @androidx.room.ColumnInfo(defaultValue = "0")
    public boolean separateCustodyDates;
    // Dedicated prison input: optional after jail, required for Not Committed/CPI.
    public String prisonTransferDate;

    // The first form row uses these temporary fields. All penalties are stored in ProfileCharge.
    @Ignore public int minYears;
    @Ignore public int minMonths;
    @Ignore public int minDays;
    @Ignore public int maxYears;
    @Ignore public int maxMonths;
    @Ignore public int maxDays;
    @Ignore public List<ProfileCharge> charges = new ArrayList<>();
    @Ignore public List<com.example.myapplication.DeductionMonth> deductionMonths = new ArrayList<>();

    public boolean notCommittedToJail;
    public boolean disqualifiedForCPI;
    public boolean disqualifiedForTimeAllowances;

    public int stalPercent;

    public int deductionDays;
    public int timeGapDays;

    public long userID;

    // App-private relative photo filename; null/empty means the default placeholder.
    public String image_path;

    // Retired form field. TA allowance calculations now use the dedicated prison date.
    public String convictionDate;
    // Legacy service metadata remains unused. Jail/prison drafts are populated by the separate inputs.
    public String sentenceServiceStartDate; // Verified enforcement start; nullable.
    public String imprisonmentStartDate;    // Original allowance/tier clock.
    public String jailCommitmentDate;       // Separate jail input/draft.
    public String prisonCommitmentDate;     // Separate prison input; also retained as a legacy fallback.

    @androidx.room.ColumnInfo(defaultValue = "0")
    public int cpiCreditPercent = 0;         // Legacy column; website calculations ignore it.

    @androidx.room.ColumnInfo(defaultValue = "0")
    public boolean custodyTimelineConfirmed = false;

}
