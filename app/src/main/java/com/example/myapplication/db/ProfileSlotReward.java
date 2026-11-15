package com.example.myapplication.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Local entitlement ledger; independent of cases and excluded from profile exports. */
@Entity
public class ProfileSlotReward {
    @PrimaryKey @NonNull public String token = "";
    public long earnedAt;
    public ProfileSlotReward() {}
}
