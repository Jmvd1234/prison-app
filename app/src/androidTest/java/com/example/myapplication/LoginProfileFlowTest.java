package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LoginProfileFlowTest {
    private Context context;
    private AppDatabase db;
    private SharedPreferences session;
    private long previousUser;
    private final List<String> names = new ArrayList<>();
    private int guestEntryID;

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = AppDatabase.getInstance(context);
        session = context.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        previousUser = session.getLong("currentUserID",-1L);
    }
    @After public void cleanupOnlySyntheticRecords() {
        if (guestEntryID > 0) {
            Profile entry=db.ProfileDao().getProfileByProfileID(guestEntryID);
            if(entry!=null) db.ProfileDao().deleteUser(entry);
        }
        for (String name:names) {
            User user=db.UserDao().getUserByUsername(name);
            if(user!=null)db.UserDao().deleteUser(user);
        }
        SharedPreferences.Editor edit=session.edit();
        if(previousUser<0)edit.remove("currentUserID");else edit.putLong("currentUserID",previousUser);
        edit.commit();
    }

    @Test public void createAndSelectProfilesValidatePasswordsAndSurviveRotation() throws Exception {
        String openName=name("Avery"), lockedName=name("Blair");
        String secret=" correct password ";
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            ready();screenshot("01-profile-picker");
            on(MainActivity.class,a->a.findViewById(R.id.createLoginProfileButton).performClick());
            on(MainActivity.class,a->{
                assertEquals(View.GONE,dialog(a).findViewById(R.id.loginPasswordFields).getVisibility());
                positive(a);
            });
            error(R.string.login_name_required);
            on(MainActivity.class,a->{
                text(a,R.id.loginProfileUsername,"Guest");positive(a);
            });
            error(R.string.login_guest_name_reserved);
            on(MainActivity.class,a->{
                text(a,R.id.loginProfileUsername,openName);
                toggle(a).setChecked(true);text(a,R.id.loginProfilePassword,"discard this");
                toggle(a).setChecked(false);
                assertEquals("",((EditText)dialog(a).findViewById(R.id.loginProfilePassword)).getText().toString());
            });
            screenshot("02-create-without-password");
            on(MainActivity.class,this::positive);
            created(openName);assertNull(db.UserDao().getUserByUsername(openName).passwordHash);
            select(openName);home(db.UserDao().getUserByUsername(openName).userID);
            screenshot("03-selected-profile-home");switchProfile();
            assertEquals(-1,session.getLong("currentUserID",-1L));

            on(MainActivity.class,a->{a.findViewById(R.id.createLoginProfileButton).performClick();text(a,R.id.loginProfileUsername,lockedName);toggle(a).setChecked(true);});
            scenario.recreate();
            ready();
            on(MainActivity.class,a->{
                assertEquals(lockedName,((EditText)dialog(a).findViewById(R.id.loginProfileUsername)).getText().toString());
                assertTrue(toggle(a).isChecked());positive(a);
            });
            error(R.string.login_password_required);
            screenshot("04-create-with-password");
            on(MainActivity.class,a->{text(a,R.id.loginProfilePassword,secret);positive(a);});
            scenario.recreate();
            created(lockedName);
            User locked=db.UserDao().getUserByUsername(lockedName);
            assertTrue(locked.requiresPassword());assertNull(locked.password);
            screenshot("05-multiple-profiles");
            select(lockedName);
            on(MainActivity.class,a->{
                assertEquals(View.GONE,dialog(a).findViewById(R.id.loginUsernameFields).getVisibility());
                assertEquals(View.GONE,dialog(a).findViewById(R.id.loginRequirePassword).getVisibility());
                text(a,R.id.loginProfilePassword,"wrong");positive(a);
            });
            error(R.string.login_password_incorrect);
            assertEquals(-1,session.getLong("currentUserID",-1L));
            screenshot("06-wrong-password");
            // Rotate while the dialog is open. A successful fast unlock can already
            // have stopped MainActivity before ActivityScenario.recreate() runs.
            scenario.recreate();ready();
            on(MainActivity.class,a->assertEquals("",((EditText)dialog(a).findViewById(R.id.loginProfilePassword)).getText().toString()));
            on(MainActivity.class,a->{text(a,R.id.loginProfilePassword,secret);positive(a);});
            home(locked.userID);
            switchProfile();select(lockedName);
            on(MainActivity.class,a->assertEquals("",((EditText)dialog(a).findViewById(R.id.loginProfilePassword)).getText().toString()));
            screenshot("07-unlock-again");
            on(MainActivity.class,a->dialog(a).getButton(DialogInterface.BUTTON_NEGATIVE).performClick());
            await(MainActivity.class,a->a.getSupportFragmentManager().findFragmentByTag(LoginProfileDialogFragment.TAG)==null);
            assertEquals(-1,session.getLong("currentUserID",-1L));
            on(MainActivity.class,a->{a.findViewById(R.id.createLoginProfileButton).performClick();text(a,R.id.loginProfileUsername," "+openName.toLowerCase()+" ");positive(a);});
            error(R.string.login_name_taken);
            on(MainActivity.class,a->dialog(a).getButton(DialogInterface.BUTTON_NEGATIVE).performClick());
        }
    }

    @Test public void manyProfilesScrollAndSystemBackReturnsToPicker() throws Exception {
        LoginProfileRepository repository=new LoginProfileRepository(db);
        User last=null;
        for(int i=0;i<10;i++) last=repository.create(name("Scroll "+i),false,"");
        User selected=last;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            ready();
            on(MainActivity.class,a->{
                ScrollView scroll=a.findViewById(R.id.main);
                assertTrue(scroll.canScrollVertically(1));
                scroll.fullScroll(View.FOCUS_DOWN);
            });
            await(MainActivity.class,a->a.findViewById(R.id.main).getScrollY()>0);
            screenshot("08-scrollable-profiles");
            select(selected.username);home(selected.userID);
            on(HomeActivity.class,a->a.getOnBackPressedDispatcher().onBackPressed());
            ready();assertEquals(-1,session.getLong("currentUserID",-1L));
        }
    }

    @Test public void guestRetainsEntriesAndSwitchingProfilesChangesTheDashboardOwner() throws Exception {
        String otherName=name("Casey");
        User other=new LoginProfileRepository(db).create(otherName,false,"");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            ready();User guest=db.UserDao().getGuest();
            guestEntryID=entry(guest.userID,"Guest saved entry");
            int otherEntryID=entry(other.userID,"Private entry");
            select(context.getString(R.string.login_guest));home(guest.userID);
            on(HomeActivity.class,a->a.findViewById(R.id.dashboardButton).performClick());
            await(DashboardActivity.class,a->a.profileModels.stream().anyMatch(p->p.getProfileID()==guestEntryID));
            on(DashboardActivity.class,a->assertFalse(a.profileModels.stream().anyMatch(p->p.getProfileID()==otherEntryID)));
            on(DashboardActivity.class,a->a.findViewById(R.id.homeButton).performClick());
            home(guest.userID);switchProfile();
            assertNotNull(db.ProfileDao().getProfileByProfileID(guestEntryID));
            select(otherName);home(other.userID);
            on(HomeActivity.class,a->a.findViewById(R.id.dashboardButton).performClick());
            await(DashboardActivity.class,a->a.profileModels.stream().anyMatch(p->p.getProfileID()==otherEntryID));
            on(DashboardActivity.class,a->assertFalse(a.profileModels.stream().anyMatch(p->p.getProfileID()==guestEntryID)));
            on(DashboardActivity.class,a->a.findViewById(R.id.homeButton).performClick());
            home(other.userID);switchProfile();select(context.getString(R.string.login_guest));home(guest.userID);
            assertNotNull(db.ProfileDao().getProfileByProfileID(guestEntryID));switchProfile();
        }
    }

    private int entry(int owner,String first) {
        Profile p=new Profile();p.userID=owner;p.firstName=first;p.lastName="Test";
        p.leaArrestDate="2024-01-01";p.commitmentDate="2024-06-01";p.minYears=5;p.maxYears=10;
        return (int)db.ProfileDao().insertProfile(p);
    }
    private String name(String base) {String name=base+" "+UUID.randomUUID().toString().substring(0,8);names.add(name);return name;}
    private AlertDialog dialog(MainActivity a) {return (AlertDialog)((LoginProfileDialogFragment)a.getSupportFragmentManager().findFragmentByTag(LoginProfileDialogFragment.TAG)).requireDialog();}
    private MaterialSwitch toggle(MainActivity a) {return dialog(a).findViewById(R.id.loginRequirePassword);}
    private void text(MainActivity a,int id,String value) {((EditText)dialog(a).findViewById(id)).setText(value);}
    private void positive(MainActivity a) {dialog(a).getButton(DialogInterface.BUTTON_POSITIVE).performClick();}
    private void ready() throws Exception {await(MainActivity.class,a->a.findViewById(R.id.createLoginProfileButton).isEnabled());}
    private void error(int id) throws Exception {await(MainActivity.class,a->{TextView error=dialog(a).findViewById(R.id.loginDialogError);return error.getVisibility()==View.VISIBLE&&error.getText().toString().equals(context.getString(id));});}
    private void created(String name) throws Exception {
        await(MainActivity.class,a->a.getSupportFragmentManager().findFragmentByTag(LoginProfileDialogFragment.TAG)==null
                && db.UserDao().getUserByUsername(name)!=null);
        assertEquals(-1,session.getLong("currentUserID",-1L));
    }
    private void select(String name) throws Exception {
        ready();on(MainActivity.class,a->{
            LinearLayout list=a.findViewById(R.id.loginProfilesContainer);
            for(int i=0;i<list.getChildCount();i++){
                View row=list.getChildAt(i);
                if(((TextView)row.findViewById(R.id.loginProfileName)).getText().toString().equals(name)){row.performClick();return;}
            }
            fail("Profile row not found: "+name);
        });
    }
    private void home(int id) throws Exception {await(HomeActivity.class,a->session.getLong("currentUserID",-1L)==id);}
    private void switchProfile() throws Exception {on(HomeActivity.class,a->a.findViewById(R.id.homeBackButton).performClick());ready();}
    private <T extends Activity> void on(Class<T> type,Consumer<T> action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) if(type.isInstance(a)){action.accept(type.cast(a));return;}
            fail("No resumed "+type.getSimpleName());
        });
    }
    private <T extends Activity> void await(Class<T> type,Predicate<T> condition) throws Exception {
        long end=SystemClock.uptimeMillis()+20_000;
        while(SystemClock.uptimeMillis()<end){
            AtomicBoolean done=new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(type.isInstance(a)&&condition.test(type.cast(a)))done.set(true);
            });
            if(done.get())return;SystemClock.sleep(50);
        }
        fail("Timed out waiting for "+type.getSimpleName());
    }
    private void screenshot(String name) throws Exception {
        if(!context.getPackageName().endsWith(".profilelogincheck"))return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir=new File(context.getExternalFilesDir(null),"login-preview");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
