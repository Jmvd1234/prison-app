package com.example.myapplication;

import android.database.Cursor;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import com.example.myapplication.db.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChargeStorageTest {
    private AppDatabase db;
    private User user;

    @Before public void setupFreshSchema() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                AppDatabase.class).allowMainThreadQueries().build();
        user = new User();
        user.username = "charge-storage-test";
        user.password = "synthetic-only";
        user.userID = (int) db.UserDao().insertUser(user);
    }

    @After public void close() { db.close(); }

    private Profile profile() {
        Profile p = new Profile();
        p.userID = user.userID;
        p.firstName = "Stored";
        p.lastName = "Charges";
        p.commitmentDate = "2020-01-01";
        for (int i = 1; i <= 3; i++) {
            ProfileCharge charge = new ProfileCharge();
            charge.minYears = i;
            charge.maxYears = i + 1;
            charge.profileID = 999; // DAO must bind rows to the actual inserted parent.
            charge.position = 999;
            p.charges.add(charge);
        }
        return p;
    }

    @Test public void allIndividualTermsRoundTripAndReplacementDeletesRemovedCharges() {
        int id = (int) db.ProfileDao().insertProfile(profile());
        Profile loaded = db.ProfileDao().getProfileByProfileID(id);
        assertEquals(3, loaded.charges.size());
        assertEquals(1, loaded.minYears);
        for (int i = 0; i < 3; i++) {
            assertEquals(id, loaded.charges.get(i).profileID);
            assertEquals(i, loaded.charges.get(i).position);
            assertEquals(i + 1, loaded.charges.get(i).minYears);
        }
        loaded.charges.remove(1);
        loaded.charges.get(1).minMonths = 7;
        db.ProfileDao().updateUser(loaded);
        Profile listed = db.ProfileDao().getProfilesByUserID(user.userID).get(0);
        assertEquals(2, listed.charges.size());
        assertEquals(3, listed.charges.get(1).minYears);
        assertEquals(7, listed.charges.get(1).minMonths);
        assertEquals(1, listed.charges.get(1).position);
        assertEquals(2, countCharges());
        db.ProfileDao().deleteUser(listed);
        assertEquals(0, countCharges());
        db.ProfileDao().insertProfile(profile());
        db.UserDao().deleteUser(user);
        assertEquals(0, countCharges());
    }

    @Test public void aFailedChildWriteRollsBackTheProfileAndEveryCharge() {
        int id = (int) db.ProfileDao().insertProfile(profile());
        db.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_test_charge BEFORE INSERT ON ProfileCharge "
                + "WHEN NEW.minYears = 99 BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END");
        Profile changed = db.ProfileDao().getProfileByProfileID(id);
        changed.firstName = "Must roll back";
        changed.charges.get(0).minYears = 99;
        try {
            db.ProfileDao().updateUser(changed);
            fail("Expected the synthetic child-write failure");
        } catch (RuntimeException expected) { }
        Profile unchanged = db.ProfileDao().getProfileByProfileID(id);
        assertEquals("Stored", unchanged.firstName);
        assertEquals(3, unchanged.charges.size());
        assertEquals(1, unchanged.charges.get(0).minYears);
        Profile failedInsert = profile();
        failedInsert.charges.get(0).minYears = 99;
        try {
            db.ProfileDao().insertProfile(failedInsert);
            fail("Expected the synthetic child-write failure");
        } catch (RuntimeException expected) { }
        assertEquals(1, db.ProfileDao().getProfilesByUserID(user.userID).size());
        assertEquals(3, countCharges());
    }

    private int countCharges() {
        try (Cursor cursor = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM ProfileCharge")) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }
}
