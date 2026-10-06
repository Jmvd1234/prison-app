package com.example.myapplication.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import com.example.myapplication.SentenceCalculator;

/** One ordered, individual sentence; capped totals are derived, never stored. */
@Entity(primaryKeys = {"profileID", "position"},
        foreignKeys = @ForeignKey(entity = Profile.class, parentColumns = "profileID",
                childColumns = "profileID", onDelete = ForeignKey.CASCADE),
        indices = @Index("profileID"))
public class ProfileCharge {
    public int profileID, position;
    public int minYears, minMonths, minDays, maxYears, maxMonths, maxDays;

    public static ProfileCharge firstFormRow(Profile profile) {
        ProfileCharge charge = new ProfileCharge();
        charge.minYears = profile.minYears;
        charge.minMonths = profile.minMonths;
        charge.minDays = profile.minDays;
        charge.maxYears = profile.maxYears;
        charge.maxMonths = profile.maxMonths;
        charge.maxDays = profile.maxDays;
        return charge;
    }

    public void fillFirstFormRow(Profile profile) {
        profile.minYears = minYears;
        profile.minMonths = minMonths;
        profile.minDays = minDays;
        profile.maxYears = maxYears;
        profile.maxMonths = maxMonths;
        profile.maxDays = maxDays;
    }

    public SentenceCalculator.Penalty toPenalty() {
        return new SentenceCalculator.Penalty(minYears, minMonths, minDays, maxYears, maxMonths, maxDays);
    }

    public ProfileCharge copyFor(int owner, int order) {
        ProfileCharge copy = new ProfileCharge();
        copy.profileID = owner;
        copy.position = order;
        copy.minYears = minYears;
        copy.minMonths = minMonths;
        copy.minDays = minDays;
        copy.maxYears = maxYears;
        copy.maxMonths = maxMonths;
        copy.maxDays = maxDays;
        return copy;
    }
}
