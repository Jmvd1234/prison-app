package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import com.google.android.material.slider.Slider;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeductionDatesFlowTest {
    private Context context;
    private AppDatabase db;
    private SharedPreferences session;
    private long previous;
    private User user;
    private int profileID;

    @Before public void seedSyntheticProfile() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=AppDatabase.getInstance(context);session=context.getSharedPreferences("user_session",0);
        previous=session.getLong("currentUserID",-1);
        user=new User();user.username="Deduction test "+UUID.randomUUID();user.userID=(int)db.UserDao().insertUser(user);
        Profile p=new Profile();p.userID=user.userID;p.firstName="Alex";p.lastName="Rivera";
        p.leaArrestDate="2023-01-15";p.commitmentDate="2023-02-15";p.minYears=5;p.maxYears=10;
        p.deductionDays=7;profileID=(int)db.ProfileDao().insertProfile(p);
        session.edit().putLong("currentUserID",user.userID).commit();
    }
    @After public void removeOnlySyntheticRecords() {
        if(user!=null)db.UserDao().deleteUser(user);
        SharedPreferences.Editor edit=session.edit();
        if(previous<0)edit.remove("currentUserID");else edit.putLong("currentUserID",previous);edit.commit();
    }
    private Profile saved(){return db.ProfileDao().getProfileByProfileID(profileID);}
    private ActivityScenario<BreakdownActivity> open(){return ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,profileID));}

    @Test public void invalidSavedDatesShowAnErrorWithoutOpeningDeductionControls() {
        Profile p=saved();p.commitmentDate="invalid date";db.ProfileDao().updateUser(p);
        try(ActivityScenario<BreakdownActivity> screen=open()) {
            await(a->text(a.findViewById(R.id.main),R.id.breakdownStatus).contains("valid date"));
            screen.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.deductionDatesPanel).getVisibility()));
        }
    }

    @Test public void storageIsAtomicOwnerScopedSurvivesEditsAndCascades() {
        List<DeductionMonth> rows=Arrays.asList(new DeductionMonth(2025,3,true,false),new DeductionMonth(2023,2,false,true));
        Profile p=db.ProfileDao().saveDeductionMonths(profileID,user.userID,rows);
        assertEquals(7,p.deductionDays);assertEquals(2,p.deductionMonths.size());assertEquals(1,p.charges.size());
        assertEquals(2023,p.deductionMonths.get(0).year);
        p.firstName="Renamed";db.ProfileDao().updateUser(p);
        assertEquals(2,saved().deductionMonths.size());assertEquals("Renamed",saved().firstName);
        try{db.ProfileDao().saveDeductionMonths(profileID,user.userID+1,Collections.emptyList());fail("Wrong owner saved");}catch(IllegalStateException expected){}
        try{db.ProfileDao().saveDeductionMonths(profileID,user.userID,Arrays.asList(rows.get(0),rows.get(0)));fail("Duplicate accepted");}catch(IllegalArgumentException expected){}
        assertEquals(2,saved().deductionMonths.size());
        db.ProfileDao().saveDeductionMonths(profileID,user.userID,Collections.emptyList());
        assertEquals(7,saved().deductionDays);assertTrue(saved().deductionMonths.isEmpty());
        assertEquals(7,SentenceCalculator.calculate(ProfileCalculationMapper.from(saved())).appliedDeductionDays);
        db.ProfileDao().saveDeductionMonths(profileID,user.userID,rows);
        db.ProfileDao().deleteUser(saved());
        try(android.database.Cursor cursor=db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM ProfileDeduction WHERE profileID="+profileID)) {
            cursor.moveToFirst();assertEquals(0,cursor.getInt(0));
        }
    }

    @Test public void monthPopupCheckboxesRotationRecalculationAndReopen() throws Exception {
        try(ActivityScenario<BreakdownActivity> screen=open()) {
            ready();screen.onActivity(a->a.findViewById(R.id.deductionAddMonth).performClick());
            await(a->popup(a)!=null);
            screen.onActivity(a->{
                View dialog=popup(a);assertFalse(dialog.findViewById(R.id.deductionDialogAdd).isEnabled());
                ((Spinner)dialog.findViewById(R.id.deductionDialogMonth)).setSelection(3);
                ((EditText)dialog.findViewById(R.id.deductionDialogYear)).setText("2025");
            });
            await(a->popup(a).findViewById(R.id.deductionDialogAdd).isEnabled());
            screenshot("01-add-month");
            screen.recreate();ready();
            await(a->popup(a)!=null&&popup(a).findViewById(R.id.deductionDialogAdd).isEnabled());
            screen.onActivity(a->popup(a).findViewById(R.id.deductionDialogAdd).performClick());
            await(a->rows(a).getChildCount()==1);
            screen.onActivity(a->{
                View row=rows(a).getChildAt(0);
                assertTrue(((CheckBox)row.findViewById(R.id.deductionGcta)).isChecked());
                assertTrue(((CheckBox)row.findViewById(R.id.deductionTastm)).isChecked());
                assertEquals("Tier 2 • Month 26",text(row,R.id.deductionMonthTier));
                assertEquals("38 days",text(row,R.id.deductionMonthTotal));
                reveal(a.findViewById(R.id.deductionDatesPanel));
            });
            screenshot("02-selected-month");
            assertTrue(saved().deductionMonths.isEmpty());
            screen.onActivity(a->rows(a).getChildAt(0).findViewById(R.id.deductionGcta).performClick());
            screen.recreate();ready();
            screen.onActivity(a->{
                assertFalse(((CheckBox)rows(a).getChildAt(0).findViewById(R.id.deductionGcta)).isChecked());
                assertEquals("15 days",text(a.findViewById(R.id.deductionDatesPanel),R.id.deductionSelectedDays));
                a.findViewById(R.id.deductionRecalculate).performClick();
            });
            await(a->saved().deductionMonths.size()==1&&text(a.findViewById(R.id.breakdownCard),R.id.breakdownDeductionValue).equals("15 days"));
            assertEquals(7,saved().deductionDays);
            screen.onActivity(a->reveal(a.findViewById(R.id.deductionDatesPanel)));
            screenshot("04-saved-month");
            screen.onActivity(a->{a.findViewById(R.id.deductionAddMonth).performClick();});
            await(a->popup(a)!=null);
            screen.onActivity(a->{((Spinner)popup(a).findViewById(R.id.deductionDialogMonth)).setSelection(3);((EditText)popup(a).findViewById(R.id.deductionDialogYear)).setText("2025");});
            await(a->text(popup(a),R.id.deductionDialogError).contains("Duplicate"));
            screen.onActivity(a->{assertFalse(popup(a).findViewById(R.id.deductionDialogAdd).isEnabled());((EditText)popup(a).findViewById(R.id.deductionDialogYear)).setText("2022");});
            await(a->text(popup(a),R.id.deductionDialogError).contains("allowance start"));
            screen.onActivity(a->popup(a).findViewById(R.id.deductionDialogCancel).performClick());
        }
        try(ActivityScenario<BreakdownActivity> reopened=open()) {
            ready();reopened.onActivity(a->{
                assertEquals(1,rows(a).getChildCount());
                assertFalse(((CheckBox)rows(a).getChildAt(0).findViewById(R.id.deductionGcta)).isChecked());
                assertTrue(((CheckBox)rows(a).getChildAt(0).findViewById(R.id.deductionTastm)).isChecked());
            });
        }
    }

    @Test public void sliderUsesOnlyEarnedDeductionsAndRefreshesItsCompletionDates() {
        List<DeductionMonth> months=Collections.singletonList(new DeductionMonth(2023,2,true,true));
        Profile profile=db.ProfileDao().saveDeductionMonths(profileID,user.userID,months);
        SentenceProjection.Range range=SentenceProjection.create(profile,LocalDate.now());
        try(ActivityScenario<BreakdownActivity> screen=open()) {
            ready();screen.onActivity(a->{
                Slider slider=a.findViewById(R.id.breakdownDateSlider);
                slider.setValue(0);
                assertEquals("0 days",text(a.findViewById(R.id.breakdownCard),R.id.breakdownDeductionValue));
                slider.setValue(range.dates.indexOf(LocalDate.of(2023,3,15)));
                assertEquals("35 days",text(a.findViewById(R.id.breakdownCard),R.id.breakdownDeductionValue));
                assertTrue(text(a.findViewById(R.id.deductionDatesPanel),R.id.deductionAppliedDays).contains("35 days"));
                rows(a).getChildAt(0).findViewById(R.id.deductionRemoveMonth).performClick();
                a.findViewById(R.id.deductionRecalculate).performClick();
            });
            await(a->saved().deductionMonths.isEmpty()&&a.findViewById(R.id.breakdownDateSlider).isEnabled()
                    &&text(a.findViewById(R.id.breakdownCard),R.id.breakdownDeductionValue).equals("7 days"));
            screen.onActivity(a->assertTrue(text(a.findViewById(R.id.deductionDatesPanel),R.id.deductionAppliedDays).contains("7 days")));
        }
        assertNull(saved().observationDate);assertEquals(7,saved().deductionDays);
    }

    @Test public void searchPaginationAndRemovalUseOriginalMonthIdentity() throws Exception {
        List<DeductionMonth> months=new ArrayList<>();
        for(int i=0;i<12;i++){YearMonth m=YearMonth.of(2023,2).plusMonths(i);months.add(new DeductionMonth(m.getYear(),m.getMonthValue(),true,true));}
        db.ProfileDao().saveDeductionMonths(profileID,user.userID,months);
        try(ActivityScenario<BreakdownActivity> screen=open()) {
            ready();screen.onActivity(a->{assertEquals(10,rows(a).getChildCount());a.findViewById(R.id.deductionNext).performClick();assertEquals(2,rows(a).getChildCount());
                ((EditText)a.findViewById(R.id.deductionSearch)).setText("Mar");assertEquals(1,rows(a).getChildCount());
                assertEquals("2023-03",rows(a).getChildAt(0).getTag());rows(a).getChildAt(0).findViewById(R.id.deductionRemoveMonth).performClick();
                assertEquals(0,rows(a).getChildCount());a.findViewById(R.id.deductionClearSearch).performClick();
                assertEquals(10,rows(a).getChildCount());a.findViewById(R.id.deductionRecalculate).performClick();
            });
            await(a->saved().deductionMonths.size()==11&&a.findViewById(R.id.deductionRecalculate).isEnabled());
            assertTrue(saved().deductionMonths.stream().noneMatch(m->m.date().equals(YearMonth.of(2023,3))));
            screen.onActivity(a->{((EditText)a.findViewById(R.id.deductionSearch)).setText("2024");assertEquals(1,rows(a).getChildCount());reveal(a.findViewById(R.id.deductionDatesPanel));});
            screenshot("03-filtered-saved-month");
        }
        try(ActivityScenario<editProfileActivity> edit=ActivityScenario.launch(new Intent(context,editProfileActivity.class).putExtra("profileID",profileID))) {
            edit.onActivity(a->{
                assertFalse(a.findViewById(R.id.inputDeductionRaw).isEnabled());
                assertEquals(0.45f,a.findViewById(R.id.inputDeductionRaw).getAlpha(),0f);
                assertEquals("385",text(a.findViewById(R.id.main),R.id.inputDeductionRaw));
            });
        }
        assertEquals(7,saved().deductionDays);
        db.ProfileDao().saveDeductionMonths(profileID,user.userID,Collections.emptyList());
        try(ActivityScenario<editProfileActivity> edit=ActivityScenario.launch(new Intent(context,editProfileActivity.class).putExtra("profileID",profileID))) {
            edit.onActivity(a->{
                EditText manual=a.findViewById(R.id.inputDeductionRaw);
                assertTrue(manual.isEnabled());assertEquals(1f,manual.getAlpha(),0f);
                assertEquals("7",manual.getText().toString());
                assertEquals(View.GONE,a.findViewById(R.id.deductionEditHint).getVisibility());
            });
        }
    }

    private LinearLayout rows(BreakdownActivity a){return a.findViewById(R.id.deductionMonthsList);}
    private View popup(BreakdownActivity a){AddDeductionMonthDialog d=(AddDeductionMonthDialog)a.getSupportFragmentManager().findFragmentByTag(AddDeductionMonthDialog.TAG);return d==null||d.getDialog()==null?null:d.requireDialog().getWindow().getDecorView();}
    private String text(View root,int id){return ((TextView)root.findViewById(id)).getText().toString();}
    private void ready(){await(a->a.findViewById(R.id.deductionDatesPanel).getVisibility()==View.VISIBLE&&a.findViewById(R.id.breakdownDateSlider).isEnabled());}
    private void reveal(View view){
        android.view.ViewParent parent=view.getParent();
        while(parent!=null&&!(parent instanceof android.widget.ScrollView))parent=parent.getParent();
        if(parent instanceof android.widget.ScrollView){
            android.widget.ScrollView scroll=(android.widget.ScrollView)parent;
            Rect rect=new Rect(0,0,view.getWidth(),view.getHeight());
            scroll.offsetDescendantRectToMyCoords(view,rect);scroll.scrollTo(0,rect.top);
        }
    }
    private void await(Predicate<BreakdownActivity> check) {
        long limit=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<limit){AtomicBoolean done=new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof BreakdownActivity)done.set(check.test((BreakdownActivity)a));});
            if(done.get())return;SystemClock.sleep(50);
        }fail("Timed out waiting for deduction screen");
    }
    private void screenshot(String name)throws Exception {
        if(!context.getPackageName().endsWith(".deductioncheck"))return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(200);
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File directory=new File(context.getExternalFilesDir(null),"deduction-preview");directory.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
