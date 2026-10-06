package com.example.myapplication.db;

import androidx.room.Embedded;
import androidx.room.Relation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Room loads child rows in a batch, avoiding a separate query for every dashboard card. */
public class ProfileWithCharges {
    @Embedded public Profile profile;
    @Relation(parentColumn = "profileID", entityColumn = "profileID")
    public List<ProfileCharge> charges;

    public Profile toProfile() {
        if (charges == null || charges.isEmpty()) throw new IllegalStateException("Profile has no saved charges.");
        profile.charges = new ArrayList<>(charges);
        profile.charges.sort(Comparator.comparingInt(charge -> charge.position));
        profile.charges.get(0).fillFirstFormRow(profile);
        return profile;
    }
}
