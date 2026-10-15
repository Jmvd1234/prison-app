package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TooltipFlowTest {
    private Context context;
    private AppDatabase db;
    private User owner;
    private SharedPreferences session;
    private long previousOwner;
    private int profileID;

    @Before public void seed() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=AppDatabase.getInstance(context);
        owner=new User();owner.username="tooltip-test-"+UUID.randomUUID();
        owner.userID=(int)db.UserDao().insertUser(owner);
        session=context.getSharedPreferences("user_session",0);
        previousOwner=session.getLong("currentUserID",-1);
        session.edit().putLong("currentUserID",owner.userID).commit();
        Profile p=new Profile();p.userID=owner.userID;p.firstName="Alex";p.lastName="Rivera";
        p.leaArrestDate="2023-01-15";p.commitmentDate="2023-02-15";p.minYears=5;p.maxYears=10;
        profileID=(int)db.ProfileDao().insertProfile(p);
    }
    @After public void cleanup() {
        db.UserDao().deleteUser(owner);
        SharedPreferences.Editor editor=session.edit();
        if(previousOwner<0)editor.remove("currentUserID");else editor.putLong("currentUserID",previousOwner);
        editor.commit();
    }

    @Test public void formHelpOpensWithoutChangingInputsAndDismissesCleanly() throws Exception {
        try(ActivityScenario<newEntryActivity> screen=ActivityScenario.launch(newEntryActivity.class)) {
            screen.onActivity(a->((EditText)a.findViewById(R.id.inputFirstName)).setText("Draft name"));
            show(R.id.leaHelp,R.string.tooltip_lea);
            screenshot("01-lea-help");
            pressBack();
            onView(withId(R.id.tooltipMessage)).check(doesNotExist());
            show(R.id.cpiHelp,R.string.tooltip_cpi);
            screen.onActivity(a->assertFalse(((MaterialSwitch)a.findViewById(R.id.disqualifiedCPISwitch)).isChecked()));
            dismissOutside();
            onView(withId(R.id.tooltipMessage)).check(doesNotExist());
            show(R.id.timeAllowancesHelp,R.string.tooltip_ta);
            screenshot("02-time-allowances-help");
            screen.onActivity(a->assertFalse(((MaterialSwitch)a.findViewById(R.id.disqualifiedTASwitch)).isChecked()));
            screen.recreate();
            onView(withId(R.id.tooltipMessage)).check(doesNotExist());
            screen.onActivity(a->assertEquals("Draft name",((EditText)a.findViewById(R.id.inputFirstName)).getText().toString()));
            show(R.id.chargesHelp,R.string.tooltip_charges);pressBack();
            show(R.id.prisonTransferHelp,R.string.tooltip_transfer);screenshot("03-transfer-help");pressBack();
            show(R.id.manualDeductionHelp,R.string.tooltip_deductions);pressBack();
        }
        try(ActivityScenario<editProfileActivity> screen=ActivityScenario.launch(new Intent(context,editProfileActivity.class).putExtra("profileID",profileID))) {
            show(R.id.leaHelp,R.string.tooltip_lea);pressBack();
            show(R.id.cpiHelp,R.string.tooltip_cpi);pressBack();
            show(R.id.timeAllowancesHelp,R.string.tooltip_ta);pressBack();
            show(R.id.chargesHelp,R.string.tooltip_charges);pressBack();
            show(R.id.prisonTransferHelp,R.string.tooltip_transfer);pressBack();
            show(R.id.manualDeductionHelp,R.string.tooltip_deductions);pressBack();
        }
    }

    @Test public void breakdownHelpDoesNotAlterTheStoredProfile() throws Exception {
        try(ActivityScenario<BreakdownActivity> screen=ActivityScenario.launch(new Intent(context,BreakdownActivity.class)
                .putExtra(BreakdownActivity.EXTRA_PROFILE_ID,profileID))) {
            long limit=SystemClock.uptimeMillis()+15000;
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            while(!ready.get()&&SystemClock.uptimeMillis()<limit) {
                screen.onActivity(a->ready.set(a.findViewById(R.id.breakdownPreviewControls).getVisibility()==View.VISIBLE));
                SystemClock.sleep(50);
            }
            assertTrue("Breakdown loaded",ready.get());
            show(R.id.observationDateHelp,R.string.tooltip_observation);screenshot("04-observation-date-help");pressBack();
            show(R.id.releaseMarkersHelp,R.string.tooltip_markers);pressBack();
            show(R.id.monthlyDeductionHelp,R.string.tooltip_deductions);screenshot("05-deduction-help");pressBack();
        }
        Profile unchanged=db.ProfileDao().getProfileByProfileID(profileID);
        assertNull(unchanged.observationDate);assertEquals(0,unchanged.deductionDays);
        assertTrue(unchanged.deductionMonths.isEmpty());
    }

    private void show(int id,int text) {
        onView(withId(id)).perform(scrollTo(),click());
        onView(withId(R.id.tooltipMessage)).check(matches(withText(text)));
    }
    private void dismissOutside() {
        final int[] point=new int[2];
        onView(withId(R.id.tooltipMessage)).check((view,error)->{
            if(error!=null)throw error;
            view.getLocationOnScreen(point);
            point[0]=point[0]>8?point[0]-8:point[0]+view.getWidth()+8;
            point[1]+=view.getHeight()/2;
        });
        long time=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,point[0],point[1],0);
        MotionEvent up=MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,point[0],point[1],0);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(down,true);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(up,true);
        down.recycle();up.recycle();
    }
    private void screenshot(String name)throws Exception {
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("captureTooltips")))return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File directory=new File(context.getExternalFilesDir(null),"tooltip-preview");directory.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
        image.recycle();
    }
}
