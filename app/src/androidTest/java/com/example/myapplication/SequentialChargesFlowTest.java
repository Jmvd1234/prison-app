package com.example.myapplication;

import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.*;
import com.google.android.material.slider.Slider;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SequentialChargesFlowTest {
    Context context;AppDatabase db;User owner;SharedPreferences session;long oldOwner;boolean hadOwner;
    @Before public void seed(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=AppDatabase.getInstance(context);owner=new User();owner.username="Sequential synthetic";owner.userID=(int)db.UserDao().insertUser(owner);session=context.getSharedPreferences("user_session",Context.MODE_PRIVATE);hadOwner=session.contains("currentUserID");oldOwner=session.getLong("currentUserID",-1);session.edit().putLong("currentUserID",owner.userID).commit();}
    @After public void cleanup(){SharedPreferences.Editor e=session.edit();if(hadOwner)e.putLong("currentUserID",oldOwner);else e.remove("currentUserID");e.commit();db.UserDao().deleteUser(owner);}
    int entry(){Profile p=new Profile();p.userID=owner.userID;p.firstName="Sequential";p.lastName="Trial";p.commitmentDate=LocalDate.now().minusYears(1).toString();p.leaArrestDate=p.commitmentDate;p.disqualifiedForCPI=true;p.disqualifiedForTimeAllowances=true;
        for(int max:new int[]{2,6,4}){ProfileCharge c=new ProfileCharge();c.minYears=max/2;c.maxYears=max;p.charges.add(c);}return (int)db.ProfileDao().insertProfile(p);}
    ActivityScenario<BreakdownActivity> open(int id){return ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,id));}
    void ready(ActivityScenario<BreakdownActivity>s,int number){AtomicBoolean done=new AtomicBoolean();long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){s.onActivity(a->done.set(a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE&&((Slider)a.findViewById(R.id.breakdownDateSlider)).isEnabled()&&((TextView)a.findViewById(R.id.timelineMinimumReleaseLabel)).getText().toString().endsWith("Charge #"+number)));if(done.get()){InstrumentationRegistry.getInstrumentation().waitForIdleSync();return;}SystemClock.sleep(50);}s.onActivity(a->fail("Not ready: "+((TextView)a.findViewById(R.id.breakdownStatus)).getText()));}
    AddServiceChargeDialog dialog(BreakdownActivity a){return (AddServiceChargeDialog)a.getSupportFragmentManager().findFragmentByTag(AddServiceChargeDialog.TAG);}
    void show(ActivityScenario<BreakdownActivity>s){s.onActivity(a->a.findViewById(R.id.serviceAddCharge).performClick());InstrumentationRegistry.getInstrumentation().waitForIdleSync();}
    void choose(ActivityScenario<BreakdownActivity>s,boolean nextMax,boolean fromMax){show(s);s.onActivity(a->{AddServiceChargeDialog d=dialog(a);assertNotNull(d);d.requireDialog().findViewById(fromMax?R.id.serviceAddToMaximum:R.id.serviceAddToMinimum).performClick();});}
    @Test public void popupShowsBothTermsAndRotationPersistsFullRangeHandoff(){int id=entry();SequentialSentencePlan first=new SequentialSentencePlan(db.ProfileDao().getProfileByProfileID(id));try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s,1);show(s);
        s.onActivity(a->{AddServiceChargeDialog d=dialog(a);assertTrue(d.requireDialog().findViewById(R.id.serviceAddToMinimum).isEnabled());assertTrue(d.requireDialog().findViewById(R.id.serviceMinimumChoice) instanceof TextView);assertFalse(d.requireDialog().findViewById(R.id.serviceMinimumChoice) instanceof CheckBox);});
        s.recreate();ready(s,1);s.onActivity(a->{AddServiceChargeDialog d=dialog(a);assertNotNull(d);assertTrue(((TextView)d.requireDialog().findViewById(R.id.serviceMaximumChoice)).getText().toString().contains("4y"));saveView(d.requireDialog().getWindow().getDecorView(),"sequential-popup.png");d.requireDialog().findViewById(R.id.serviceAddToMinimum).performClick();});ready(s,2);
        Profile saved=db.ProfileDao().getProfileByProfileID(id);assertEquals("0",saved.serviceChoices);assertEquals(first.capDays,saved.maximumTotalServiceDays);
        s.onActivity(a->{Slider slider=a.findViewById(R.id.breakdownDateSlider);assertEquals(java.time.temporal.ChronoUnit.DAYS.between(first.start,first.active().minimum),slider.getValue(),0);slider.setValue(0);assertEquals(java.time.temporal.ChronoUnit.DAYS.between(first.start,first.active().minimum),slider.getValue(),0);assertTrue(((TextView)a.findViewById(R.id.breakdownObservationDate)).getText().toString().contains(first.active().minimum.format(SentenceCalculator.DISPLAY_DATE)));assertEquals("Min. time served",((TextView)a.findViewById(R.id.serviceMinimumHeading)).getText().toString());TimelineMilestonesLayout overview=a.findViewById(R.id.breakdownTimeline);assertTrue(hasBubble(overview,"Min. time served", "Charge #1"));assertFalse(hasBubble(overview,"Actual custody limit"));overview.requestRectangleOnScreen(new android.graphics.Rect(0,0,overview.getWidth(),overview.getHeight()),true);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->saveView(a.getWindow().getDecorView(),"sequential-overview.png"));
        s.recreate();ready(s,2);s.onActivity(a->assertEquals(java.time.temporal.ChronoUnit.DAYS.between(first.start,first.active().minimum),((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0));
    }}
    @Test public void restoredPopupCanSubmitBeforeProjectionFinishes(){
        int id=entry();try(ActivityScenario<BreakdownActivity>s=open(id)){
            ready(s,1);show(s);s.recreate();
            s.onActivity(a->{AddServiceChargeDialog d=dialog(a);assertNotNull(d);
                d.requireDialog().findViewById(R.id.serviceMinimumChoice).performClick();
                d.requireDialog().findViewById(R.id.serviceAddToMaximum).performClick();});
            ready(s,2);assertEquals("2",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
        }
    }
    boolean hasBubble(TimelineMilestonesLayout overview,String text){return hasBubble(overview,text,"");}
    boolean hasBubble(TimelineMilestonesLayout overview,String text,String suffix){for(int i=0;i<overview.getChildCount();i++)if(overview.getChildAt(i).getVisibility()==View.VISIBLE&&overview.getChildAt(i) instanceof TextView&&((TextView)overview.getChildAt(i)).getText().toString().startsWith(text)&&((TextView)overview.getChildAt(i)).getText().toString().endsWith(suffix))return true;return false;}
    void touchEnd(BreakdownActivity a){Slider slider=a.findViewById(R.id.breakdownDateSlider);long now=SystemClock.uptimeMillis();float x=slider.getWidth()-slider.getTrackSidePadding(),y=slider.getHeight()/2f;MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,x,y,0);slider.dispatchTouchEvent(down);slider.dispatchTouchEvent(up);down.recycle();up.recycle();}
    @Test public void draggingPastMaximumPromptsOnceUntilMovingBackBelowMaximum(){try(ActivityScenario<BreakdownActivity>s=open(entry())){ready(s,1);s.onActivity(a->{((Slider)a.findViewById(R.id.breakdownDateSlider)).setValue(0);touchEnd(a);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{assertNotNull(dialog(a));dialog(a).dismiss();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(this::touchEnd);InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->assertNull(dialog(a)));}}
    @Test public void lastChargeHasReleaseLabelsAndNoPopup(){int id=entry();try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s,1);choose(s,false,true);ready(s,2);choose(s,true,false);ready(s,3);s.onActivity(a->{assertEquals(View.VISIBLE,a.findViewById(R.id.serviceAddCharge).getVisibility());assertFalse(a.findViewById(R.id.serviceAddCharge).isEnabled());assertEquals(a.getString(R.string.breakdown_minimum_release),((TextView)a.findViewById(R.id.serviceMinimumHeading)).getText().toString());touchEnd(a);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->assertNull(dialog(a)));assertEquals("20",db.ProfileDao().getProfileByProfileID(id).serviceChoices);}}
    @Test public void choicesAreOwnerCheckedRejectStaleWritesAndResetWhenTermsChange(){int id=entry();Profile saved=db.ProfileDao().advanceService(id,owner.userID,"",false,true);assertEquals("2",saved.serviceChoices);
        try{db.ProfileDao().advanceService(id,owner.userID,"",false,true);fail();}catch(IllegalStateException expected){}
        try{db.ProfileDao().advanceService(id,owner.userID+100,"2",false,true);fail();}catch(IllegalStateException expected){}
        saved.firstName="Renamed";db.ProfileDao().updateUser(saved);assertEquals("2",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
        saved.charges.get(0).maxYears=3;db.ProfileDao().updateUser(saved);assertEquals("",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
    }
    @Test public void hardLimitBlocksAdditionalChargesAndAllowsOverdueObservation(){
        int id=entry();Profile p=db.ProfileDao().getProfileByProfileID(id);p.charges.clear();
        for(int i=0;i<4;i++){ProfileCharge c=new ProfileCharge();c.minDays=90;c.maxDays=100;p.charges.add(c);}
        p.commitmentDate=LocalDate.now().minusDays(50).toString();p.leaArrestDate=p.commitmentDate;db.ProfileDao().updateUser(p);
        db.ProfileDao().advanceService(id,owner.userID,"",false,true);db.ProfileDao().advanceService(id,owner.userID,"2",false,true);
        try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s,3);s.onActivity(a->{
            Slider slider=a.findViewById(R.id.breakdownDateSlider);slider.setValue(200);
            assertFalse(a.findViewById(R.id.serviceAddCharge).isEnabled());
            assertEquals("actual custody limit reached.",((TextView)a.findViewById(R.id.serviceLimitMessage)).getText().toString());
            assertEquals(View.VISIBLE,a.findViewById(R.id.serviceLimitMessage).getVisibility());
            assertTrue(hasBubble(a.findViewById(R.id.breakdownTimeline),"Actual custody limit"));
            slider.setValue(320);assertEquals(320,slider.getValue(),0);assertNull(dialog(a));
            saveView(a.getWindow().getDecorView(),"sequential-cutoff.png");
        });}
    }
    @Test public void removingChargeTruncatesRouteAndCanReAddAfterRotation(){
        int id=entry();
        try(ActivityScenario<BreakdownActivity>s=open(id)){
            ready(s,1);choose(s,false,true);ready(s,2);choose(s,false,false);ready(s,3);
            s.onActivity(a->{
                View grid=a.findViewById(R.id.serviceChargeGrid);
                assertFalse(grid.findViewWithTag("service-charge-1").isClickable());
                grid.findViewWithTag("service-charge-3").performClick();
            });ready(s,2);assertEquals("2",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
            s.onActivity(a->a.findViewById(R.id.serviceChargeGrid).findViewWithTag("service-charge-2").performClick());
            ready(s,1);Profile p=db.ProfileDao().getProfileByProfileID(id);
            assertEquals("",p.serviceChoices);assertEquals(3,p.charges.size());
            s.recreate();ready(s,1);choose(s,false,false);ready(s,2);
            assertEquals("0",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
            s.onActivity(a->saveView(a.findViewById(R.id.breakdownObservationRow),"charge-removal-grid.png"));
        }
    }
    @Test public void removalRejectsFirstChargeWrongOwnerAndStaleRoute(){
        int id=entry();db.ProfileDao().advanceService(id,owner.userID,"",false,true);
        try{db.ProfileDao().removeServiceFrom(id,owner.userID,"2",1);fail();}catch(IllegalArgumentException expected){}
        try{db.ProfileDao().removeServiceFrom(id,owner.userID+100,"2",2);fail();}catch(IllegalStateException expected){}
        try{db.ProfileDao().removeServiceFrom(id,owner.userID,"",2);fail();}catch(IllegalStateException expected){}
        assertEquals("2",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
    }
    @Test public void chargesFillFiveColumnsAndRemovingMiddleClearsLaterRows(){
        int id=entry();Profile p=db.ProfileDao().getProfileByProfileID(id);p.charges.clear();
        for(int i=0;i<8;i++){ProfileCharge c=new ProfileCharge();c.minDays=1;c.maxDays=100;p.charges.add(c);}
        p.commitmentDate=LocalDate.now().toString();p.leaArrestDate=p.commitmentDate;db.ProfileDao().updateUser(p);
        String route="";for(int i=0;i<6;i++){db.ProfileDao().advanceService(id,owner.userID,route,false,false);route+="0";}
        try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s,7);s.onActivity(a->{
            LinearLayout grid=a.findViewById(R.id.serviceChargeGrid);assertEquals(2,grid.getChildCount());
            for(int i=0;i<7;i++){ViewGroup row=(ViewGroup)grid.getChildAt(i/5);assertEquals(i==0?"service-charge-1":"service-pill-"+(i+1),row.getChildAt(i%5).getTag());}
            saveView(a.findViewById(R.id.serviceChargesSection),"charge-removal-seven.png");
            grid.findViewWithTag("service-charge-4").performClick();
        });ready(s,3);assertEquals("00",db.ProfileDao().getProfileByProfileID(id).serviceChoices);
        assertEquals(8,db.ProfileDao().getProfileByProfileID(id).charges.size());}
    }
    @Test public void pendingChargeCapsSelectionEvenAfterPromptDismissal(){
        int id=entry();SequentialSentencePlan first=new SequentialSentencePlan(db.ProfileDao().getProfileByProfileID(id));
        float end=java.time.temporal.ChronoUnit.DAYS.between(first.start,first.active().maximum);
        try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s,1);
            s.onActivity(a->{Slider slider=a.findViewById(R.id.breakdownDateSlider);slider.setValue(0);touchEnd(a);assertEquals(end,slider.getValue(),0);});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a->{assertNotNull(dialog(a));dialog(a).dismiss();});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a->{Slider slider=a.findViewById(R.id.breakdownDateSlider);touchEnd(a);assertEquals(end,slider.getValue(),0);
                slider.setValue(slider.getValueTo());assertEquals(end,slider.getValue(),0);
                
                
                
                assertNull(dialog(a));});
            s.recreate();ready(s,1);s.onActivity(a->assertEquals(end,((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0));
            choose(s,false,true);ready(s,2);
            SequentialSentencePlan next=new SequentialSentencePlan(db.ProfileDao().getProfileByProfileID(id));
            s.onActivity(a->{Slider slider=a.findViewById(R.id.breakdownDateSlider);slider.setValue(end+1);assertEquals(end+1,slider.getValue(),0);
                slider.setValue(slider.getValueTo());assertEquals(java.time.temporal.ChronoUnit.DAYS.between(next.start,next.active().maximum),slider.getValue(),0);});
        }
    }
    @Test public void openingPastMaximumPromptsOnceAndDismissalSurvivesRotation(){
        int id=entry();Profile p=db.ProfileDao().getProfileByProfileID(id);
        p.commitmentDate=LocalDate.now().minusYears(8).toString();p.leaArrestDate=p.commitmentDate;db.ProfileDao().updateUser(p);
        SequentialSentencePlan plan=new SequentialSentencePlan(db.ProfileDao().getProfileByProfileID(id));
        try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s,1);
            s.onActivity(a->{assertNotNull(dialog(a));assertEquals(java.time.temporal.ChronoUnit.DAYS.between(plan.start,plan.active().maximum),((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0);});
            s.recreate();ready(s,1);s.onActivity(a->{assertNotNull(dialog(a));dialog(a).dismiss();});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.recreate();ready(s,1);
            s.onActivity(a->assertNull(dialog(a)));
            choose(s,false,true);ready(s,2);s.onActivity(a->assertNull(dialog(a)));
        }
    }
    void saveView(View view,String name){Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{bitmap.recycle();}}
}
