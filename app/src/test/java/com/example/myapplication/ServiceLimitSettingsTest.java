package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import com.example.myapplication.db.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.io.*;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ServiceLimitSettingsTest {
    Context context;AppDatabase db;User owner;long previousOwner;
    @Before public void setup(){
        context=ApplicationProvider.getApplicationContext();db=AppDatabase.getInstance(context);
        owner=new User();owner.username="Cutoff test";owner.userID=(int)db.UserDao().insertUser(owner);
        previousOwner=context.getSharedPreferences("user_session",0).getLong("currentUserID",-1);
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",owner.userID).commit();
    }
    @After public void cleanup(){
        db.UserDao().deleteUser(owner);
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",previousOwner).commit();
    }
    private Profile profile(){
        Profile p=new Profile();p.firstName="Cutoff";p.lastName="Test";p.userID=owner.userID;
        p.commitmentDate=p.leaArrestDate="2020-01-01";
        for(int i=0;i<4;i++){ProfileCharge c=new ProfileCharge();c.minDays=c.maxDays=100;p.charges.add(c);}
        p.serviceChoices="222";return p;
    }
    private Profile save(Profile p){p.profileID=(int)db.ProfileDao().insertProfile(p);return p;}
    @Test public void settingPersistsPerEntryAcrossEditingAndTransfer()throws Exception{
        Profile first=save(profile()),second=save(profile());
        Profile saved=db.ProfileDao().setServiceLimitMode(first.profileID,owner.userID,false);
        assertFalse(saved.considerAllowancesInServiceLimit);
        assertTrue(db.ProfileDao().getProfileByProfileID(second.profileID).considerAllowancesInServiceLimit);
        // An edit using a previously loaded model cannot overwrite the saved setting.
        first.firstName="Edited";db.ProfileDao().updateUser(first);
        saved=db.ProfileDao().getProfileByProfileID(first.profileID);assertFalse(saved.considerAllowancesInServiceLimit);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ProfileTransferCodec.write(saved,out);
        Profile imported=ProfileTransferCodec.read(new ByteArrayInputStream(out.toByteArray()));
        assertFalse(imported.considerAllowancesInServiceLimit);assertEquals("222",imported.serviceChoices);
        assertEquals(4,new SequentialSentencePlan(imported).active().number);
        saved=db.ProfileDao().setServiceLimitMode(first.profileID,owner.userID,true);
        out.reset();ProfileTransferCodec.write(saved,out);
        imported=ProfileTransferCodec.read(new ByteArrayInputStream(out.toByteArray()));
        assertTrue(imported.considerAllowancesInServiceLimit);assertEquals("222",imported.serviceChoices);
        assertEquals(3,new SequentialSentencePlan(imported).active().number);
        String old=out.toString("UTF-8").replace("\"considerAllowancesInServiceLimit\":true,","");
        assertTrue(ProfileTransferCodec.read(new ByteArrayInputStream(old.getBytes("UTF-8"))).considerAllowancesInServiceLimit);
        try{db.ProfileDao().setServiceLimitMode(first.profileID,owner.userID+1,false);fail();}catch(IllegalStateException expected){}
    }
    @Test public void detailedSwitchUpdatesWarningAndRestoresAfterReopening()throws Exception{
        Profile p=save(profile());
        try(ActivityScenario<BreakdownActivity> scenario=ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,p.profileID))){
            waitReady(scenario,true);
            scenario.onActivity(a->{
                TimelineTutorialDialog tutorial=(TimelineTutorialDialog)a.getSupportFragmentManager().findFragmentByTag(TimelineTutorialDialog.TAG);
                if(tutorial!=null)tutorial.dismissNow();
                TextView heading=a.findViewById(R.id.serviceChargesHeading);
                assertTrue(heading.getText().toString().contains("Threefold rule time reached, no more charges can be added"));
                Spanned styled=(Spanned)heading.getText();
                assertEquals(a.getColor(R.color.laya_danger),styled.getSpans(0,styled.length(),ForegroundColorSpan.class)[0].getForegroundColor());
                assertFalse(a.findViewById(R.id.serviceAddCharge).isEnabled());
                TimelineMilestonesLayout timeline=a.findViewById(R.id.breakdownTimeline);
                boolean found=false;for(int i=0;i<timeline.getChildCount();i++)if(timeline.getChildAt(i) instanceof TextView)
                    found|=((TextView)timeline.getChildAt(i)).getText().toString().startsWith("Threefold rule time reached");
                assertTrue(found);
                for(int width:new int[]{360,720}){capture(a.findViewById(R.id.breakdownCard),width,"cutoff-settings-");capture(a.findViewById(R.id.serviceChargesHeading),width,"cutoff-warning-");}
                ((MaterialSwitch)a.findViewById(R.id.considerAllowancesInServiceLimit)).performClick();
            });
            waitReady(scenario,false);
            scenario.onActivity(a->{assertFalse(((TextView)a.findViewById(R.id.serviceChargesHeading)).getText().toString().contains("no more charges"));assertNotNull(a.findViewById(R.id.serviceChargeGrid).findViewWithTag("service-charge-4"));});
            scenario.recreate();waitReady(scenario,false);
            assertFalse(db.ProfileDao().getProfileByProfileID(p.profileID).considerAllowancesInServiceLimit);
            scenario.onActivity(a->((MaterialSwitch)a.findViewById(R.id.considerAllowancesInServiceLimit)).performClick());
            waitReady(scenario,true);
            scenario.onActivity(a->assertTrue(((TextView)a.findViewById(R.id.serviceChargesHeading)).getText().toString().contains("Threefold rule time reached")));
        }
    }
    @Test public void summaryHasTheSameSavedSwitch()throws Exception{
        Profile p=save(profile());SentenceCalculator.Result result=SequentialSentencePlan.initialResult(p,LocalDate.now());
        android.os.Bundle args=ProfileBreakdownDialogFragment.createArguments("Minimum","Maximum",result);args.putInt(BreakdownActivity.EXTRA_PROFILE_ID,p.profileID);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->{ProfileBreakdownDialogFragment dialog=ProfileBreakdownDialogFragment.newInstance(args);dialog.showNow(a.getSupportFragmentManager(),ProfileBreakdownDialogFragment.TAG);
                MaterialSwitch toggle=dialog.requireDialog().findViewById(R.id.considerAllowancesInServiceLimit);assertTrue(toggle.isChecked());toggle.performClick();});
            for(int i=0;i<200&&db.ProfileDao().getProfileByProfileID(p.profileID).considerAllowancesInServiceLimit;i++){Thread.sleep(20);Shadows.shadowOf(Looper.getMainLooper()).idle();}
            assertFalse(db.ProfileDao().getProfileByProfileID(p.profileID).considerAllowancesInServiceLimit);
            scenario.recreate();
            scenario.onActivity(a->{ProfileBreakdownDialogFragment dialog=(ProfileBreakdownDialogFragment)a.getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
                assertFalse(((MaterialSwitch)dialog.requireDialog().findViewById(R.id.considerAllowancesInServiceLimit)).isChecked());});
        }
    }
    private void waitReady(ActivityScenario<BreakdownActivity> scenario,boolean enabled)throws Exception{
        AtomicBoolean ready=new AtomicBoolean();
        for(int i=0;i<250;i++){
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            scenario.onActivity(a->{MaterialSwitch toggle=a.findViewById(R.id.considerAllowancesInServiceLimit);
                ready.set(a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE&&((Slider)a.findViewById(R.id.breakdownDateSlider)).isEnabled()&&toggle.isEnabled()&&toggle.isChecked()==enabled);});
            if(ready.get())return;Thread.sleep(20);
        }fail("Breakdown did not finish updating");
    }
    private void capture(View view,int width,String prefix){
        String folder=System.getProperty("laya.test.screenshots");if(folder==null)return;
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());
        Bitmap bitmap=Bitmap.createBitmap(width,view.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas);
        try{File file=new File(folder,prefix+width+".png");file.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
        catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
    }
}
