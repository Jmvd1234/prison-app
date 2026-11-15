package com.example.myapplication;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ApplicationProvider;
import com.example.myapplication.db.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="w360dp-h800dp-mdpi")
public class FreeNavigationTest {
    Context context;AppDatabase db;long owner;
    @Before public void setup(){
        AdRuntime.requestsEnabled=false;AdRuntime.rewardShowing=false;
        context=ApplicationProvider.getApplicationContext();db=AppDatabase.getInstance(context);db.clearAllTables();
        owner=Edition.localOwner(context);context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",owner).commit();
    }
    @After public void cleanup(){db.clearAllTables();AdRuntime.requestsEnabled=true;}
    private void full(){Profile p=new Profile();p.userID=owner;p.firstName="Free";p.lastName="Test";
        p.commitmentDate=p.leaArrestDate="2020-01-01";p.minYears=10;p.maxYears=20;db.ProfileDao().insertProfile(p);}
    @Test public void oneAutomaticOwnerAndHomeHasNoUserSwitcher(){
        assertEquals(owner,Edition.localOwner(context));assertEquals(1,db.UserDao().getAllUsers().size());
        try(ActivityController<HomeActivity> c=Robolectric.buildActivity(HomeActivity.class).setup()){
            assertEquals(View.GONE,c.get().findViewById(R.id.homeBackButton).getVisibility());
            assertEquals(View.GONE,c.get().findViewById(R.id.homeLoginProfileName).getVisibility());
            assertEquals("0 of 1 profiles used",((TextView)c.get().findViewById(R.id.freeSlotStatus)).getText().toString());
            c.get().findViewById(R.id.newEntryButton).performClick();
            assertEquals(newEntryActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivityForResult().intent.getComponent().getClassName());
        }
    }
    @Test public void fullHomeOffersOptionalRewardAndCancelKeepsCapacity(){
        full();try(ActivityController<HomeActivity> c=Robolectric.buildActivity(HomeActivity.class).setup()){
            c.get().findViewById(R.id.newEntryButton).performClick();
            AlertDialog dialog=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());
            assertTrue(((TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("one additional profile slot"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull(Shadows.shadowOf(c.get()).getNextStartedActivityForResult());assertEquals(1,db.ProfileDao().profileSlotCapacity());
        }
    }
    @Test public void unavailableAdDoesNotGrantAndExistingProfileRemainsAccessible(){
        full();try(ActivityController<HomeActivity> c=Robolectric.buildActivity(HomeActivity.class).setup()){
            c.get().findViewById(R.id.freeUnlockSlot).performClick();
            ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            AlertDialog error=(AlertDialog)ShadowDialog.getLatestDialog();
            assertTrue(((TextView)error.findViewById(android.R.id.message)).getText().toString().contains("Ads are unavailable"));
            assertEquals(1,db.ProfileDao().profileSlotCapacity());assertEquals(1,db.ProfileDao().countSavedProfiles());
            error.getButton(AlertDialog.BUTTON_POSITIVE).performClick();c.get().findViewById(R.id.dashboardButton).performClick();
            assertEquals(DashboardActivity.class.getName(),Shadows.shadowOf(c.get()).getNextStartedActivity().getComponent().getClassName());
        }
    }
    @Test public void directNewEntrySaveCannotBypassCapacity(){
        full();try(ActivityController<newEntryActivity> c=Robolectric.buildActivity(newEntryActivity.class).setup()){
            c.get().findViewById(R.id.saveEntryButton).performClick();
            assertTrue(ShadowDialog.getLatestDialog().isShowing());assertEquals(1,db.ProfileDao().countSavedProfiles());
        }
    }
}
