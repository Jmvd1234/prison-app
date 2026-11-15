package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import com.example.myapplication.db.*;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.slider.Slider;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ChargeRewindTest {
    Context context;AppDatabase db;User owner;long oldOwner;
    @Before public void setup(){
        context=ApplicationProvider.getApplicationContext();db=AppDatabase.getInstance(context);
        owner=new User();owner.username="Rewind test";owner.userID=(int)db.UserDao().insertUser(owner);
        oldOwner=context.getSharedPreferences("user_session",0).getLong("currentUserID",-1);
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",owner.userID).commit();
        context.getSharedPreferences("timeline_help",0).edit().putBoolean("acknowledged_"+owner.userID,true).commit();
    }
    @After public void cleanup(){
        db.UserDao().deleteUser(owner);
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",oldOwner).commit();
        context.getSharedPreferences("timeline_help",0).edit().remove("acknowledged_"+owner.userID).commit();
    }
    private Profile profile(){
        Profile p=new Profile();p.userID=owner.userID;p.firstName="Rewind";p.lastName="Test";
        p.leaArrestDate=p.commitmentDate="2020-01-01";
        for(int max:new int[]{1000,800,400}){ProfileCharge c=new ProfileCharge();c.minDays=max/2;c.maxDays=max;p.charges.add(c);}
        return p;
    }
    private ActivityScenario<BreakdownActivity> open(Profile p){
        p.profileID=(int)db.ProfileDao().insertProfile(p);
        return ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,p.profileID));
    }
    @Test public void minimumMaximumAndCustomHandoffsStayFixedWhileRewinding()throws Exception{
        for(boolean allowances:new boolean[]{false,true})for(int handoff=0;handoff<3;handoff++){
            Profile p=profile();p.considerAllowancesInServiceLimit=allowances;
            SequentialSentencePlan first=new SequentialSentencePlan(p);
            p.serviceChoices=handoff==2?first.appendCustomDate(first.active().maximum.plusDays(45),first.active().maximum):first.appendChoice(false,handoff==1);
            SequentialSentencePlan plan=new SequentialSentencePlan(p);
            try(ActivityScenario<BreakdownActivity> scenario=open(p)){
                ready(scenario,2);
                scenario.onActivity(a->{
                    dismissPrompt(a);
                    Slider slider=a.findViewById(R.id.breakdownDateSlider);
                    slider.setValue(ChronoUnit.DAYS.between(plan.start,plan.active().start));
                    List<String> fixed=history(a);assertFalse(fixed.isEmpty());
                    String latest=text(a,R.id.timelineMaximumReleaseLabel);
                    slider.setValue(0);
                    assertEquals(0,slider.getValue(),0);
                    assertTrue(text(a,R.id.breakdownObservationDate).contains("Jan 1, 2020"));
                    assertEquals(fixed,history(a));
                    assertNotEquals(latest,text(a,R.id.timelineMaximumReleaseLabel));
                    assertTrue(text(a,R.id.timelineMaximumReleaseLabel).endsWith("Charge #2"));
                    assertTrue(text(a,R.id.timelineMinimumReleaseLabel).endsWith("Charge #2"));
                    // Date selection shares the full range, including the original start.
                    a.findViewById(R.id.breakdownObservationDate).performClick();
                    a.getSupportFragmentManager().executePendingTransactions();
                    MaterialDatePicker<?> picker=(MaterialDatePicker<?>)a.getSupportFragmentManager().findFragmentByTag("breakdown_observation_picker");
                    assertNotNull(picker);
                    assertEquals(plan.start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),((Long)picker.getSelection()).longValue());
                    picker.requireDialog().findViewById(com.google.android.material.R.id.confirm_button).performClick();
                });
                scenario.recreate();ready(scenario,2);
                scenario.onActivity(a->assertEquals(0,((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0));
                Profile saved=db.ProfileDao().getProfileByProfileID(p.profileID);
                assertEquals(plan.choices,saved.serviceChoices);
                assertEquals(plan.active().start,new SequentialSentencePlan(saved).active().start);
            }
        }
    }
    @Test public void addingFromARewoundDateStillLandsOnTheNewChargeStart()throws Exception{
        Profile p=profile();
        try(ActivityScenario<BreakdownActivity> scenario=open(p)){
            ready(scenario,1);
            for(int next=2;next<=3;next++){
                scenario.onActivity(a->{
                    dismissPrompt(a);
                    ((Slider)a.findViewById(R.id.breakdownDateSlider)).setValue(0);
                    a.findViewById(R.id.serviceAddCharge).performClick();a.getSupportFragmentManager().executePendingTransactions();
                    AddServiceChargeDialog dialog=(AddServiceChargeDialog)a.getSupportFragmentManager().findFragmentByTag(AddServiceChargeDialog.TAG);
                    assertNotNull(dialog);dialog.requireDialog().findViewById(R.id.serviceAddToMaximum).performClick();
                });
                ready(scenario,next);
                SequentialSentencePlan saved=new SequentialSentencePlan(db.ProfileDao().getProfileByProfileID(p.profileID));
                scenario.onActivity(a->{
                    assertEquals(ChronoUnit.DAYS.between(saved.start,saved.active().start),((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0);
                    assertTrue(text(a,R.id.breakdownObservationDate).contains(saved.active().start.format(SentenceCalculator.DISPLAY_DATE)));
                });
            }
        }
    }
    private String text(BreakdownActivity a,int id){return ((TextView)a.findViewById(id)).getText().toString();}
    private List<String> history(BreakdownActivity a){
        TimelineMilestonesLayout view=a.findViewById(R.id.breakdownTimeline);List<String> result=new ArrayList<>();
        for(int i=0;i<view.getChildCount();i++)if(view.getChildAt(i) instanceof TextView){
            String value=((TextView)view.getChildAt(i)).getText().toString();if(value.endsWith("Charge #1"))result.add(value);
        }return result;
    }
    private void dismissPrompt(BreakdownActivity a){
        AddServiceChargeDialog dialog=(AddServiceChargeDialog)a.getSupportFragmentManager().findFragmentByTag(AddServiceChargeDialog.TAG);
        if(dialog!=null)dialog.dismissNow();
    }
    private void ready(ActivityScenario<BreakdownActivity> scenario,int charge)throws Exception{
        AtomicBoolean ready=new AtomicBoolean();
        for(int i=0;i<250;i++){
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            scenario.onActivity(a->{TextView label=a.findViewById(R.id.timelineMaximumReleaseLabel);
                ready.set(a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE&&((Slider)a.findViewById(R.id.breakdownDateSlider)).isEnabled()&&label!=null&&label.getText().toString().endsWith("Charge #"+charge));});
            if(ready.get())return;Thread.sleep(20);
        }fail("Timeline did not finish updating");
    }
}
