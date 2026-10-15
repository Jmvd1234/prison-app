package com.example.myapplication;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LoginProfileStorageTest {
    private Context context;
    private AppDatabase db;
    private LoginProfileRepository repository;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
        repository = new LoginProfileRepository(db);
    }
    @After public void close() { db.close(); }

    @Test public void freshInstallHasOnlyGuestAndRepeatedLoadsKeepItsEntries() throws Exception {
        List<User> users = repository.load();
        assertEquals(1, users.size());
        User guest = users.get(0);
        assertTrue(guest.isGuest);
        assertFalse(guest.requiresPassword());
        Profile entry = new Profile();entry.userID = guest.userID;entry.firstName = "Guest entry";
        entry.leaArrestDate = "2024-01-01";entry.commitmentDate = "2024-02-01";entry.minYears = 1;entry.maxYears = 3;
        db.ProfileDao().insertProfile(entry);
        assertEquals(guest.userID, repository.load().get(0).userID);
        assertEquals(1, db.ProfileDao().getProfilesByUserID(guest.userID).size());
        User other = repository.create("Alex", false, "hidden password must be ignored");
        assertNull(other.passwordHash);
        assertFalse(other.requiresPassword());
        assertEquals(other.userID, repository.unlock(other.userID, null).userID);
        assertTrue(db.ProfileDao().getProfilesByUserID(other.userID).isEmpty());
    }

    @Test public void passwordsAreOptionalButProtectedProfilesRequireAnExactMatch() throws Exception {
        User user = repository.create("  Blake  ", true, " secret ");
        assertEquals("Blake", user.username);
        assertNull(user.password);
        assertNotNull(user.passwordHash);
        assertFalse(ProfilePassword.needsUpdate(user.passwordHash));
        assertTrue(user.requiresPassword());
        assertEquals(user.userID, repository.unlock(user.userID, " secret ").userID);
        invalidPassword(user.userID, null);
        invalidPassword(user.userID, "secret");
    }

    @Test public void blankDuplicateAndReservedNamesAndMissingPasswordsAreRejected() throws Exception {
        repository.load();
        invalidCreate("   ", false, "", R.string.login_name_required);
        invalidCreate("guest", false, "", R.string.login_guest_name_reserved);
        invalidCreate("Blake", true, "", R.string.login_password_required);
        repository.create("Casey", false, "");
        invalidCreate(" casey ", false, "", R.string.login_name_taken);
        assertEquals(2, db.UserDao().getAllUsers().size());
    }

    @Test public void deletionCascadesOnlySelectedOwnerAndNeverDeletesGuest() throws Exception {
        User guest=repository.load().get(0);
        User target=repository.create("Remove",false,"");
        User keep=repository.create("Keep",false,"");
        int removed=entryWithDeductions(target,"profile_photos/11111111-1111-1111-1111-111111111111.jpg");
        int retained=entryWithDeductions(keep,"profile_photos/22222222-2222-2222-2222-222222222222.jpg");
        int guestEntry=entryWithDeductions(guest,null);
        List<String> paths=repository.delete(target.userID,null);
        assertEquals(1,paths.size());
        assertEquals("profile_photos/11111111-1111-1111-1111-111111111111.jpg",paths.get(0));
        assertNull(db.UserDao().getUserByID(target.userID));
        assertNull(db.ProfileDao().getProfileByProfileID(removed));
        assertChildCount("ProfileCharge",removed,0);assertChildCount("ProfileDeduction",removed,0);
        assertNotNull(db.ProfileDao().getProfileByProfileID(retained));
        assertChildCount("ProfileCharge",retained,1);assertChildCount("ProfileDeduction",retained,1);
        try{repository.delete(guest.userID,null);fail("Deleted Guest");}
        catch(LoginProfileRepository.InvalidInput expected){assertEquals(R.string.login_delete_guest_blocked,expected.message);}
        assertNotNull(db.ProfileDao().getProfileByProfileID(guestEntry));
        assertEquals(guest.userID,repository.load().get(0).userID);
        assertNotNull(repository.create("Remove",false,""));
    }

    @Test public void deletionRequiresCurrentPasswordAndRejectsMissingProfiles() throws Exception {
        User protectedUser=repository.create("Protected",true," exact password ");
        int entry=entryWithDeductions(protectedUser,null);
        for(String password:new String[]{null,"wrong","exact password"}) {
            try{repository.delete(protectedUser.userID,password);fail("Deleted with incorrect password");}
            catch(LoginProfileRepository.InvalidInput expected){assertTrue(expected.passwordField);}
            assertNotNull(db.UserDao().getUserByID(protectedUser.userID));
            assertChildCount("ProfileDeduction",entry,1);
        }
        repository.delete(protectedUser.userID," exact password ");
        assertNull(db.UserDao().getUserByID(protectedUser.userID));
        try{repository.delete(protectedUser.userID," exact password ");fail("Missing profile accepted");}
        catch(LoginProfileRepository.InvalidInput expected){assertEquals(R.string.login_profile_missing,expected.message);}
        User legacy=new User();legacy.username="Legacy delete";legacy.password="old secret";
        legacy.userID=(int)db.UserDao().insertUser(legacy);
        try{repository.delete(legacy.userID,"wrong");fail("Legacy password bypass");}
        catch(LoginProfileRepository.InvalidInput expected){assertTrue(expected.passwordField);}
        repository.delete(legacy.userID,"old secret");
        assertNull(db.UserDao().getUserByID(legacy.userID));
    }

    private int entryWithDeductions(User owner,String photo) {
        Profile p=new Profile();p.userID=owner.userID;p.firstName="Entry";p.image_path=photo;
        p.leaArrestDate="2023-01-15";p.commitmentDate="2023-02-15";p.minYears=1;p.maxYears=3;
        int id=(int)db.ProfileDao().insertProfile(p);
        db.ProfileDao().saveDeductionMonths(id,owner.userID,java.util.Collections.singletonList(new DeductionMonth(2023,2,true,true)));
        return id;
    }
    private void assertChildCount(String table,int profileID,int expected) {
        try(android.database.Cursor c=db.getOpenHelper().getReadableDatabase()
                .query("SELECT COUNT(*) FROM "+table+" WHERE profileID="+profileID)) {
            c.moveToFirst();assertEquals(expected,c.getInt(0));
        }
    }

    @Test public void oldAccountsUnlockAndUpgradeWithoutChangingTheirOwnerID() throws Exception {
        User old = new User();old.username = "Previous account";old.password = "old secret";
        old.userID = (int) db.UserDao().insertUser(old);
        invalidPassword(old.userID, "wrong");
        assertEquals("old secret", db.UserDao().getUserByID(old.userID).password);
        assertEquals(old.userID, repository.unlock(old.userID, "old secret").userID);
        User upgraded = db.UserDao().getUserByID(old.userID);
        assertNull(upgraded.password);
        assertNotNull(upgraded.passwordHash);
        assertTrue(upgraded.requiresPassword());
        assertEquals(old.userID, repository.unlock(old.userID, "old secret").userID);
    }

    @Test public void slowHashesAreReplacedOnlyAfterSuccessfulAuthentication() throws Exception {
        User old = new User();old.username = "Previous protected profile";
        old.passwordHash = "v1$PBKDF2WithHmacSHA256$600000$000102030405060708090a0b0c0d0e0f$"
                + "a625b99416f6c9a479adebc00728b0e922b3197df417c3eef0671b067f78dc38";
        old.userID = (int) db.UserDao().insertUser(old);
        invalidPassword(old.userID, "wrong");
        assertEquals(old.passwordHash, db.UserDao().getUserByID(old.userID).passwordHash);
        assertEquals(old.userID, repository.unlock(old.userID, "old PBKDF2 password").userID);
        User updated = db.UserDao().getUserByID(old.userID);
        assertFalse(ProfilePassword.needsUpdate(updated.passwordHash));
        assertNull(updated.password);
        String fastHash = updated.passwordHash;
        assertEquals(old.userID, repository.unlock(old.userID, "old PBKDF2 password").userID);
        assertEquals(fastHash, db.UserDao().getUserByID(old.userID).passwordHash);
    }

    @Test public void versionSixUpgradePreservesEntriesChargesPhotosAndGuestPersistsOnReopen() throws Exception {
        String name = "profile-login-migration-" + UUID.randomUUID();
        AppDatabase upgraded = null;
        try {
            try(SQLiteDatabase old = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null);
                BufferedReader schema = new BufferedReader(new InputStreamReader(
                        InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("login_schema_v6.sql")))) {
                String line;
                while ((line = schema.readLine()) != null) if (!line.trim().isEmpty()) old.execSQL(line);
                old.execSQL("INSERT INTO User (userID,username,password) VALUES (10,'Existing','secret')");
                old.execSQL("INSERT INTO Profile (profileID,firstName,leaArrestDate,commitmentDate,notCommittedToJail,disqualifiedForCPI,disqualifiedForTimeAllowances,stalPercent,deductionDays,timeGapDays,userID,image_path) VALUES (20,'Saved entry','2024-01-01','2024-02-01',0,0,0,20,12,3,10,'profile_photos/keep.jpg')");
                old.execSQL("INSERT INTO ProfileCharge VALUES (20,0,1,2,3,4,5,6)");
                old.setVersion(6);
            }
            upgraded = Room.databaseBuilder(context,AppDatabase.class,name).addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build();
            User user = upgraded.UserDao().getUserByID(10);
            assertEquals("secret",user.password);assertFalse(user.isGuest);assertNull(user.passwordHash);
            Profile entry = upgraded.ProfileDao().getProfileByProfileID(20);
            assertEquals(10,entry.userID);assertEquals(12,entry.deductionDays);
            assertEquals("profile_photos/keep.jpg",entry.image_path);
            assertEquals(1,entry.charges.size());assertEquals(4,entry.charges.get(0).maxYears);
            int guestID = new LoginProfileRepository(upgraded).load().get(0).userID;
            upgraded.close();
            upgraded = Room.databaseBuilder(context,AppDatabase.class,name).addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build();
            assertEquals(guestID,new LoginProfileRepository(upgraded).load().get(0).userID);
            assertEquals(2,upgraded.UserDao().getAllUsers().size());
        } finally {if(upgraded!=null)upgraded.close();context.deleteDatabase(name);}
    }

    private void invalidPassword(int id,String password) throws Exception {
        try { repository.unlock(id,password);fail("Protected profile opened with an invalid password"); }
        catch(LoginProfileRepository.InvalidInput error) { assertEquals(R.string.login_password_incorrect,error.message); }
    }
    private void invalidCreate(String name,boolean require,String password,int expected) throws Exception {
        try {repository.create(name,require,password);fail("Invalid profile was saved");}
        catch(LoginProfileRepository.InvalidInput error) {assertEquals(expected,error.message);}
    }
}
