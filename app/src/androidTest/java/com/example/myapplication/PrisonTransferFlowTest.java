package com.example.myapplication;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.room.Room;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import com.google.android.material.slider.Slider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrisonTransferFlowTest {
    private Context context;
    private AppDatabase db;
    private User owner;
    private SharedPreferences session;
    private long previousOwner;

    @Before public void setup() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=AppDatabase.getInstance(context);
        owner=new User();owner.username="transfer-test-"+UUID.randomUUID();
        owner.userID=(int)db.UserDao().insertUser(owner);
        session=context.getSharedPreferences("user_session",Context.MODE_PRIVATE);
        previousOwner=session.getLong("currentUserID",-1);
        session.edit().putLong("currentUserID",owner.userID).commit();
    }
    @After public void cleanup() {
        db.UserDao().deleteUser(owner);
        SharedPreferences.Editor editor=session.edit();
        if(previousOwner<0)editor.remove("currentUserID");else editor.putLong("currentUserID",previousOwner);
        editor.commit();
    }

    @Test public void addPopupBreakdownSliderEditAndClearUseTheStoredTransfer() throws Exception {
        int id;
        try(ActivityScenario<HomeActivity> home=ActivityScenario.launch(HomeActivity.class)) {
            home.onActivity(a->a.make_new_entry(null));
            await(newEntryActivity.class,a->{
                a.inputFirstName.setText("Alex");a.inputLastName.setText("Rivera");
                a.inputMinYears.setText("5");a.inputMaxYears.setText("10");
                a.inputLEADate.setText("Jan 10, 2018");
                date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018");
                date(a,R.id.inputPrisonTransferDate).setText("Mar 10, 2019");
                assertEquals(View.VISIBLE,a.findViewById(R.id.prisonTransferContainer).getVisibility());
                reveal(a.findViewById(R.id.prisonTransferContainer));return true;
            });
            screenshot("01-add-entry-transfer.png");
            await(newEntryActivity.class,a->{a.saveEntryButton.performClick();return true;});
            await(HomeActivity.class,a->{
                ProfileBreakdownDialogFragment f=(ProfileBreakdownDialogFragment)a.getSupportFragmentManager()
                        .findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
                if(f==null||f.getDialog()==null)return false;
                Dialog d=f.requireDialog();
                assertEquals("263",((TextView)d.findViewById(R.id.jailDaysValue)).getText().toString());
                assertEquals("160/160",((TextView)d.findViewById(R.id.jailGctaValue)).getText().toString());
                assertEquals("120/120",((TextView)d.findViewById(R.id.jailTastmValue)).getText().toString());
                long prison=ChronoUnit.DAYS.between(LocalDate.of(2019,3,10),LocalDate.now());
                assertEquals(number(prison),((TextView)d.findViewById(R.id.prisonDaysValue)).getText().toString());
                return true;
            });
            screenshot("02-save-breakdown.png");
            Profile saved=db.ProfileDao().getProfilesByUserID(owner.userID).get(0);
            id=saved.profileID;assertEquals("Mar 10, 2019",saved.prisonTransferDate);
        }
        Profile saved=db.ProfileDao().getProfileByProfileID(id);
        SentenceProjection.Range range=SentenceProjection.create(saved,LocalDate.now());
        try(ActivityScenario<BreakdownActivity> screen=ActivityScenario.launch(new Intent(context,BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID,id))) {
            await(BreakdownActivity.class,a->a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE);
            screen.onActivity(a->{
                TextView marker=a.findViewById(R.id.breakdownTimeline).findViewWithTag("2019-03-10");
                assertNotNull(marker);assertTrue(marker.getText().toString().contains("Prison commitment"));
                marker.performClick();
                assertText(a,R.id.jailDaysValue,"263");assertText(a,R.id.prisonDaysValue,"0");
                ((Slider)a.findViewById(R.id.breakdownDateSlider)).setValue(0);
                assertText(a,R.id.prisonDaysValue,"0");assertText(a,R.id.prisonGctaValue,"0/0");
                a.findViewById(R.id.timelineTodayLabel).performClick();
                reveal(a.findViewById(R.id.breakdownCard));
            });
            screenshot("03-timeline-and-split.png");
        }
        assertEquals("Mar 10, 2019",db.ProfileDao().getProfileByProfileID(id).prisonTransferDate);
        for(boolean clear:new boolean[]{false,true}) {
            try(ActivityScenario<editProfileActivity> edit=ActivityScenario.launch(new Intent(context,editProfileActivity.class).putExtra("profileID",id))) {
                edit.onActivity(a->{
                    assertEquals(clear?"Apr 10, 2019":"Mar 10, 2019",date(a,R.id.inputPrisonTransferDate).getText().toString());
                    if(clear)a.findViewById(R.id.clearPrisonTransferDate).performClick();
                    else date(a,R.id.inputPrisonTransferDate).setText("Apr 10, 2019");
                });
                edit.recreate();
                edit.onActivity(a->{
                    assertEquals(clear?"":"Apr 10, 2019",date(a,R.id.inputPrisonTransferDate).getText().toString());
                    reveal(a.findViewById(R.id.prisonTransferContainer));
                });
                if(!clear)screenshot("04-edit-entry-transfer.png");
                edit.onActivity(a->a.saveEntryButton.performClick());
                long end=SystemClock.uptimeMillis()+10000;
                while(edit.getState()!=Lifecycle.State.DESTROYED&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
                assertEquals(Lifecycle.State.DESTROYED,edit.getState());
            }
            Profile p=db.ProfileDao().getProfileByProfileID(id);
            assertEquals(clear?null:"Apr 10, 2019",p.prisonTransferDate);
            assertEquals(!clear,SentenceCalculator.calculate(ProfileCalculationMapper.from(p)).custodyBreakdown.prison.recorded);
        }
    }

    @Test public void bothFormsValidateRequiredOptionalAndDisabledDatesInEveryMode() {
        try(ActivityScenario<newEntryActivity> screen=ActivityScenario.launch(newEntryActivity.class)) {
            screen.onActivity(a->{
                for(int layout:new int[]{R.layout.activity_new_entry,R.layout.activity_edit_profile}) {
                    for(int flags=0;flags<8;flags++) {
                        a.setContentView(layout);
                        TriggerFormController controller=new TriggerFormController(a,a.findViewById(R.id.triggerDetailsContainer));
                        Profile p=new Profile();p.separateCustodyDates=true;
                        p.notCommittedToJail=(flags&1)!=0;p.disqualifiedForCPI=(flags&2)!=0;p.disqualifiedForTimeAllowances=(flags&4)!=0;
                        p.jailCommitmentDate="Jun 20, 2018";p.prisonTransferDate="Mar 10, 2019";
                        boolean prisonRequired=p.notCommittedToJail||p.disqualifiedForCPI;
                        p.commitmentDate=prisonRequired?p.prisonTransferDate:p.jailCommitmentDate;
                        controller.load(p,null); date(a,R.id.inputLEADate).setText("Jan 10, 2018");
                        Profile output=new Profile();controller.writeToProfile(output);
                        for(String invalid:new String[]{"bad","Jan 9, 2018",LocalDate.now().plusDays(1).toString()}) {
                            date(a,R.id.inputPrisonTransferDate).setText(invalid);
                            assertInvalid(controller,invalid);
                        }
                        if(!p.notCommittedToJail) {
                            for(String invalid:new String[]{"Jun 20, 2018","Jun 19, 2018"}) {
                                date(a,R.id.inputPrisonTransferDate).setText(invalid);assertInvalid(controller,invalid);
                            }
                        }
                        a.findViewById(R.id.clearPrisonTransferDate).performClick();
                        if(prisonRequired)assertInvalid(controller,"missing prison");
                        else {controller.writeToProfile(output);assertNull(output.prisonTransferDate);}
                        date(a,R.id.inputPrisonTransferDate).setText("Mar 10, 2019");
                        date(a,R.id.inputCommitmentDate).setText("");
                        if(prisonRequired)controller.writeToProfile(output); else assertInvalid(controller,"missing jail");
                        date(a,R.id.inputCommitmentDate).setText("bad");
                        if(p.notCommittedToJail)controller.writeToProfile(output); else assertInvalid(controller,"bad jail");
                        date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018");
                        date(a,R.id.inputLEADate).setText(LocalDate.now().plusDays(1).toString());
                        assertInvalid(controller,"future arrest");
                    }
                }
            });
        }
    }
    private void assertInvalid(TriggerFormController controller,String reason) {
        try {controller.writeToProfile(new Profile());fail("Accepted "+reason);}
        catch(IllegalArgumentException expected) { }
    }

    @Test public void optionalPickerReconnectsAfterRotationAndDraftSurvivesDirectPrisonMode() {
        try(ActivityScenario<newEntryActivity> screen=ActivityScenario.launch(newEntryActivity.class)) {
            screen.onActivity(a->{
                date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018");
                date(a,R.id.inputPrisonTransferDate).performClick();
            });
            screen.recreate();
            onView(withId(com.google.android.material.R.id.confirm_button)).perform(click());
            screen.onActivity(a->{
                assertFalse(date(a,R.id.inputPrisonTransferDate).getText().toString().isEmpty());
                a.findViewById(R.id.committedSwitch).performClick();
                assertTrue(date(a,R.id.inputPrisonTransferDate).isEnabled());
                assertFalse(date(a,R.id.inputCommitmentDate).isEnabled());
            });
            screen.recreate();
            screen.onActivity(a->{
                a.findViewById(R.id.committedSwitch).performClick();
                assertTrue(date(a,R.id.inputPrisonTransferDate).isEnabled());
                assertFalse(date(a,R.id.inputPrisonTransferDate).getText().toString().isEmpty());
                a.findViewById(R.id.clearPrisonTransferDate).performClick();
                assertEquals("",date(a,R.id.inputPrisonTransferDate).getText().toString());
            });
        }
    }

    @Test public void cpiAndTaSaveReopenAndUsePrisonForTheirDifferentClocks() {
        for(boolean cpi:new boolean[]{true,false}) {
            try(ActivityScenario<newEntryActivity> add=ActivityScenario.launch(newEntryActivity.class)) {
                add.onActivity(a->{
                    a.inputFirstName.setText(cpi?"Cpi":"Ta");a.inputLastName.setText("Custody");
                    a.inputMinYears.setText("5");a.inputMaxYears.setText("10");
                    a.inputLEADate.setText("Jan 10, 2018");
                    date(a,R.id.inputCommitmentDate).setText("Jun 20, 2018");
                    if(cpi)date(a,R.id.inputPrisonTransferDate).setText("Mar 10, 2019");
                    a.findViewById(cpi?R.id.disqualifiedCPISwitch:R.id.disqualifiedTASwitch).performClick();
                });
                onView(withId(android.R.id.button1)).perform(click());
                add.recreate();add.onActivity(a->a.saveEntryButton.performClick());
                waitClosed(add);
            }
            Profile p=db.ProfileDao().getProfilesByUserID(owner.userID).stream()
                    .filter(row->row.disqualifiedForCPI==cpi).findFirst().get();
            assertTrue(p.separateCustodyDates);assertNull(p.convictionDate);
            assertEquals(cpi?"Mar 10, 2019":"Jun 20, 2018",p.commitmentDate);
            SentenceCalculator.Result r=SentenceCalculator.calculate(ProfileCalculationMapper.from(p));
            assertTrue(r.problem,r.available);assertEquals(0,r.gctaDays);
            if(cpi)assertEquals(263,r.custodyBreakdown.jail.days);
            try(ActivityScenario<editProfileActivity> edit=ActivityScenario.launch(new Intent(context,editProfileActivity.class).putExtra("profileID",p.profileID))) {
                edit.onActivity(a->{
                    assertEquals("Jun 20, 2018",date(a,R.id.inputCommitmentDate).getText().toString());
                    assertEquals(cpi?"Mar 10, 2019":"",date(a,R.id.inputPrisonTransferDate).getText().toString());
                    assertEquals(View.GONE,a.findViewById(R.id.triggerDetailsContainer).getVisibility());
                    date(a,R.id.inputPrisonTransferDate).setText("Apr 10, 2019");
                });
                edit.recreate();edit.onActivity(a->a.saveEntryButton.performClick());waitClosed(edit);
            }
            p=db.ProfileDao().getProfileByProfileID(p.profileID);
            assertEquals("Apr 10, 2019",p.prisonTransferDate);
            r=SentenceCalculator.calculate(ProfileCalculationMapper.from(p,LocalDate.of(2019,5,10)));
            assertTrue(r.problem,r.available);assertEquals(cpi?0:20,r.gctaDays);
            assertEquals(0,r.custodyBreakdown.jail.gctaDays);
            assertEquals(cpi?0:20,r.custodyBreakdown.prison.gctaDays);
        }
    }
    private void waitClosed(ActivityScenario<?> screen) {
        long end=SystemClock.uptimeMillis()+10000;
        while(screen.getState()!=Lifecycle.State.DESTROYED&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
        assertEquals(Lifecycle.State.DESTROYED,screen.getState());
    }

    @Test public void versionSevenUpgradePreservesDataAndNeverImportsStalePrisonAliases() throws Exception {
        String name="prison-transfer-upgrade-"+UUID.randomUUID();AppDatabase upgraded=null;
        try {
            try(SQLiteDatabase old=context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null);
                BufferedReader schema=new BufferedReader(new InputStreamReader(InstrumentationRegistry.getInstrumentation()
                        .getContext().getAssets().open("login_schema_v6.sql")))) {
                String line;while((line=schema.readLine())!=null)if(!line.trim().isEmpty())old.execSQL(line);
                old.execSQL("ALTER TABLE User ADD COLUMN passwordHash TEXT");
                old.execSQL("ALTER TABLE User ADD COLUMN isGuest INTEGER NOT NULL DEFAULT 0");
                old.execSQL("INSERT INTO User (userID,username,isGuest) VALUES (10,'Guest',1)");
                old.execSQL("INSERT INTO Profile (profileID,firstName,leaArrestDate,commitmentDate,prisonCommitmentDate,notCommittedToJail,disqualifiedForCPI,disqualifiedForTimeAllowances,stalPercent,deductionDays,timeGapDays,userID,image_path) VALUES (20,'Saved','2018-01-10','2018-06-20','2019-03-10',0,0,0,20,12,3,10,'profile_photos/keep.jpg')");
                old.execSQL("INSERT INTO ProfileCharge VALUES (20,0,1,2,3,4,5,6)");old.setVersion(7);
            }
            upgraded=Room.databaseBuilder(context,AppDatabase.class,name).addMigrations(AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10).build();
            Profile p=upgraded.ProfileDao().getProfileByProfileID(20);
            assertFalse(p.separateCustodyDates);assertNull(p.prisonTransferDate);assertEquals("2019-03-10",p.prisonCommitmentDate);
            assertEquals("profile_photos/keep.jpg",p.image_path);assertEquals(1,p.charges.size());
            assertTrue(upgraded.UserDao().getUserByID(10).isGuest);
            p.prisonTransferDate="2019-04-10";upgraded.ProfileDao().updateUser(p);upgraded.close();
            upgraded=Room.databaseBuilder(context,AppDatabase.class,name).build();
            assertEquals("2019-04-10",upgraded.ProfileDao().getProfileByProfileID(20).prisonTransferDate);
        } finally {if(upgraded!=null)upgraded.close();context.deleteDatabase(name);}
    }

    private EditText date(Activity a,int id){return a.findViewById(id);}
    private String number(long n){return NumberFormat.getIntegerInstance().format(n);}
    private void assertText(Activity a,int id,String value){assertEquals(value,((TextView)a.findViewById(id)).getText().toString());}
    private void reveal(View view){view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);}
    private <T extends Activity> void await(Class<T> type,Predicate<T> check) {
        long end=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<end) {
            AtomicBoolean done=new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(type.isInstance(a))done.set(check.test(type.cast(a)));
            });
            if(done.get())return;SystemClock.sleep(50);
        }
        fail("Timed out waiting for "+type.getSimpleName());
    }
    private void screenshot(String name) throws Exception {
        if(!context.getPackageName().endsWith(".prisontransfercheck"))return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(400);
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir=context.getExternalFilesDir("transfer-preview");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        image.recycle();
    }
}
