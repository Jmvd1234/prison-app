package com.example.myapplication.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Dao
public abstract class ProfileDao {
    @Insert protected abstract long insertProfileRow(Profile profile);
    @Update protected abstract int updateProfileRow(Profile profile);
    @Insert protected abstract void insertChargeRows(List<ProfileCharge> charges);
    @Query("DELETE FROM ProfileCharge WHERE profileID = :profileID")
    protected abstract void deleteChargeRows(int profileID);

    @Transaction
    @Query("SELECT * FROM Profile WHERE userID = :userID")
    protected abstract List<ProfileWithCharges> loadProfiles(long userID);

    @Transaction
    @Query("SELECT * FROM Profile WHERE profileID = :profileID LIMIT 1")
    protected abstract ProfileWithCharges loadProfile(int profileID);

    @Transaction
    public long insertProfile(Profile profile) {
        List<ProfileCharge> charges = snapshot(profile);
        long id = insertProfileRow(profile);
        insertChargeRows(forOwner(charges, Math.toIntExact(id)));
        return id;
    }

    @Transaction
    public void updateUser(Profile profile) {
        List<ProfileCharge> charges = snapshot(profile);
        if (updateProfileRow(profile) != 1) throw new IllegalStateException("Profile no longer exists.");
        deleteChargeRows(profile.profileID);
        insertChargeRows(forOwner(charges, profile.profileID));
    }

    @Transaction
    public List<Profile> getProfilesByUserID(long userID) {
        List<Profile> profiles = new ArrayList<>();
        for (ProfileWithCharges row : loadProfiles(userID)) profiles.add(row.toProfile());
        return profiles;
    }

    @Transaction
    public Profile getProfileByProfileID(int profileID) {
        ProfileWithCharges row = loadProfile(profileID);
        return row == null ? null : row.toProfile();
    }

    @Delete public abstract void deleteUser(Profile profile);

    private static List<ProfileCharge> snapshot(Profile profile) {
        // Also supports callers building a one-charge profile from the first form row.
        List<ProfileCharge> charges = profile.charges == null || profile.charges.isEmpty()
                ? Collections.singletonList(ProfileCharge.firstFormRow(profile)) : profile.charges;
        return forOwner(charges, profile.profileID);
    }

    private static List<ProfileCharge> forOwner(List<ProfileCharge> charges, int owner) {
        List<ProfileCharge> ordered = new ArrayList<>();
        for (int position = 0; position < charges.size(); position++) {
            ProfileCharge charge = charges.get(position);
            if (charge == null) throw new IllegalArgumentException("A charge cannot be empty.");
            ordered.add(charge.copyFor(owner, position));
        }
        return ordered;
    }
}
