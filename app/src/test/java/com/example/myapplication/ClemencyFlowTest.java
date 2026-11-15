package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import com.example.myapplication.db.*;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ClemencyFlowTest {
    Context context;AppDatabase db;User owner;long previousOwner;
    @Before public void setup(){
        context=ApplicationProvider.getApplicationContext();db=AppDatabase.getInstance(context);
        owner=new User();owner.username="Clemency test";owner.userID=(int)db.UserDao().insertUser(owner);
        previousOwner=context.getSharedPreferences("user_session",0).getLong("currentUserID",-1);
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",owner.userID).commit();
        context.getSharedPreferences("timeline_help",0).edit().putBoolean("acknowledged_"+owner.userID,true).commit();
    }
    @After public void cleanup(){
        db.UserDao().deleteUser(owner);
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",previousOwner).commit();
    }
    private Profile profile(){
        Profile p=new Profile();p.userID=owner.userID;p.firstName="Clemency";p.lastName="Example";
        p.leaArrestDate=p.commitmentDate=LocalDate.now().minusYears(7).toString();p.separateCustodyDates=true;
        p.jailCommitmentDate=p.commitmentDate;p.convicted=true;p.clemencyCategory="SINGLE_GENERAL";
        ProfileCharge c=new ProfileCharge();c.minYears=30;c.maxYears=40;p.charges.add(c);c.fillFirstFormRow(p);
        return p;
    }
    private Profile save(Profile p){p.profileID=(int)db.ProfileDao().insertProfile(p);return p;}
    @Test public void newEntryShowsIndependentTriggerWarningWrappedPickerAndRestoresSelection(){
        try(ActivityScenario<newEntryActivity> s=ActivityScenario.launch(newEntryActivity.class)){
            s.onActivity(a->{
                assertEquals(View.GONE,a.findViewById(R.id.clemencyDetails).getVisibility());
                ((MaterialSwitch)a.findViewById(R.id.convictedSwitch)).performClick();
                assertEquals(View.VISIBLE,a.findViewById(R.id.clemencyDetails).getVisibility());
                assertEquals("None",text(a,R.id.clemencyCategory));
                assertFalse(((MaterialSwitch)a.findViewById(R.id.disqualifiedTASwitch)).isChecked());
                a.findViewById(R.id.clemencyCategory).performClick();
                AlertDialog warning=(AlertDialog)ShadowDialog.getLatestDialog();
                assertEquals(a.getString(R.string.trigger_warning_clemency),((TextView)warning.findViewById(android.R.id.message)).getText().toString());
                warning.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                assertEquals("None",text(a,R.id.clemencyCategory));
                a.findViewById(R.id.clemencyCategory).performClick();
                ((AlertDialog)ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                a.getSupportFragmentManager().executePendingTransactions();
                ClemencyCategoryDialog picker=(ClemencyCategoryDialog)a.getSupportFragmentManager().findFragmentByTag(ClemencyCategoryDialog.TAG);
                AlertDialog dialog=(AlertDialog)picker.requireDialog();
                assertEquals(7,dialog.getListView().getCount());
                for(int i=1;i<7;i++){
                    TextView row=(TextView)dialog.getListView().getAdapter().getView(i,null,dialog.getListView());
                    assertTrue(row.getText().toString().contains(ClemencyCategory.values()[i].years+" years"));
                    capture(row,280,"choice-"+i);assertTrue(row.getLineCount()>1);
                }
                dialog.getListView().performItemClick(dialog.getListView().getAdapter().getView(5,null,dialog.getListView()),5,5);
                a.getSupportFragmentManager().executePendingTransactions();
                assertTrue(text(a,R.id.clemencyCategory).startsWith("20 years"));
                for(int width:new int[]{320,640})capture(a.findViewById(R.id.clemencyDetails),width,"form");
            });
            s.recreate();s.onActivity(a->{
                assertTrue(((MaterialSwitch)a.findViewById(R.id.convictedSwitch)).isChecked());
                assertTrue(text(a,R.id.clemencyCategory).startsWith("20 years"));
                ((MaterialSwitch)a.findViewById(R.id.convictedSwitch)).performClick();
                assertEquals(View.GONE,a.findViewById(R.id.clemencyDetails).getVisibility());
                ((MaterialSwitch)a.findViewById(R.id.convictedSwitch)).performClick();assertEquals("None",text(a,R.id.clemencyCategory));
                assertTrue(a.getString(R.string.clemency_help).contains("If more than one category appears applicable, have counsel or the BPP confirm the applicable category."));
            });
        }
    }
    @Test public void editingLoadsCategoryAndSharedControllerWritesBothLayouts(){
        Profile p=save(profile());
        try(ActivityScenario<editProfileActivity> s=ActivityScenario.launch(new Intent(context,editProfileActivity.class).putExtra("profileID",p.profileID))){
            s.onActivity(a->{assertTrue(((MaterialSwitch)a.findViewById(R.id.convictedSwitch)).isChecked());assertTrue(text(a,R.id.clemencyCategory).startsWith("10 years"));});
            s.recreate();s.onActivity(a->assertTrue(text(a,R.id.clemencyCategory).startsWith("10 years")));
        }
        try(ActivityScenario<newEntryActivity> s=ActivityScenario.launch(newEntryActivity.class)){
            s.onActivity(a->{for(int layout:new int[]{R.layout.activity_new_entry,R.layout.activity_edit_profile}){
                a.setContentView(layout);TriggerFormController c=new TriggerFormController(a,a.findViewById(R.id.triggerDetailsContainer));c.load(p,null);
                Profile output=new Profile();c.writeToProfile(output);assertTrue(output.convicted);assertEquals(p.clemencyCategory,output.clemencyCategory);
                ((MaterialSwitch)a.findViewById(R.id.convictedSwitch)).performClick();c.writeToProfile(output);assertFalse(output.convicted);assertEquals("NONE",output.clemencyCategory);
            }});
        }
    }
    @Test public void storageAndTransferPreserveCategoryAndModeAndValidateImportedSelections()throws Exception{
        Profile p=save(profile());db.ProfileDao().setServiceLimitMode(p.profileID,owner.userID,false);
        p=db.ProfileDao().getProfileByProfileID(p.profileID);assertTrue(p.convicted);assertEquals("SINGLE_GENERAL",p.clemencyCategory);
        p.firstName="Changed";db.ProfileDao().updateUser(p);p=db.ProfileDao().getProfileByProfileID(p.profileID);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ProfileTransferCodec.write(p,out);String json=out.toString("UTF-8");
        Profile imported=read(json);assertTrue(imported.convicted);assertEquals(p.clemencyCategory,imported.clemencyCategory);assertFalse(imported.considerAllowancesInServiceLimit);
        String old=json.replace("\"convicted\":true,","").replace("\"clemencyCategory\":\"SINGLE_GENERAL\",","");
        imported=read(old);assertFalse(imported.convicted);assertEquals("NONE",imported.clemencyCategory);
        for(String bad:new String[]{json.replace("SINGLE_GENERAL","BOGUS"),json.replace("\"convicted\":true","\"convicted\":false"),json.replace("\"convicted\":true,","")}){
            try{read(bad);fail("Invalid clemency metadata accepted");}catch(IOException expected){}
        }
    }
    private Profile read(String json)throws IOException{return ProfileTransferCodec.read(new ByteArrayInputStream(json.getBytes("UTF-8")));}
    @Test public void timelineMarkerBannerAndSharedToggleFollowSelectedDateAndSurviveReopening()throws Exception{
        Profile p=save(profile());LocalDate review=ClemencyAssessment.create(p,null).reviewDate;
        LocalDate start=LocalDate.parse(p.leaArrestDate);
        try(ActivityScenario<BreakdownActivity> s=ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,p.profileID))){
            ready(s,true);
            s.onActivity(a->{
                assertEquals(View.VISIBLE,a.findViewById(R.id.clemencyBanner).getVisibility());
                TextView marker=a.findViewById(R.id.timelineClemencyLabel);assertNotNull(marker);
                assertEquals(a.getColor(R.color.clemency_marker),marker.getCurrentTextColor());
                assertTrue(marker.getText().toString().contains(review.format(SentenceCalculator.DISPLAY_DATE)));
                Slider slider=a.findViewById(R.id.breakdownDateSlider);
                slider.setValue(ChronoUnit.DAYS.between(start,review.minusDays(1)));
                assertEquals(View.GONE,a.findViewById(R.id.clemencyBanner).getVisibility());
                marker.performClick();assertEquals(ChronoUnit.DAYS.between(start,review),slider.getValue(),0);
                assertEquals(View.VISIBLE,a.findViewById(R.id.clemencyBanner).getVisibility());
                for(int width:new int[]{320,640}){capture(a.findViewById(R.id.clemencyBanner),width,"banner");capture(a.findViewById(R.id.breakdownCard),width,"breakdown");}
                ((MaterialSwitch)a.findViewById(R.id.considerAllowancesInServiceLimit)).performClick();
            });
            ready(s,false);
            s.onActivity(a->{assertEquals(View.GONE,a.findViewById(R.id.clemencyBanner).getVisibility());assertTrue(text(a,R.id.timelineClemencyLabel).contains(start.plusYears(10).format(SentenceCalculator.DISPLAY_DATE)));});
            s.recreate();ready(s,false);
            s.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.clemencyBanner).getVisibility()));
        }
    }
    @Test public void dashboardUsesRedActionAndRefreshesAfterSharedSettingChanges()throws Exception{
        Profile p=save(profile());
        try(ActivityScenario<DashboardActivity> s=ActivityScenario.launch(DashboardActivity.class)){
            AtomicBoolean ready=new AtomicBoolean();
            for(int i=0;i<300;i++){Shadows.shadowOf(Looper.getMainLooper()).idle();s.onActivity(a->ready.set(!a.profileModels.isEmpty()));if(ready.get())break;Thread.sleep(20);}
            assertTrue(ready.get());
            s.onActivity(a->{profileModel m=a.profileModels.get(0);assertTrue(m.isClemencyRecommended());assertEquals(a.getString(R.string.clemency_action),m.getSuggestedAction());
                Profile_RecyclerViewAdapter adapter=new Profile_RecyclerViewAdapter(a,a.profileModels);
                android.widget.FrameLayout parent=new android.widget.FrameLayout(a);
                Profile_RecyclerViewAdapter.MyViewHolder holder=adapter.onCreateViewHolder(parent,0);adapter.onBindViewHolder(holder,0);
                assertEquals(a.getColor(R.color.laya_danger),((TextView)holder.itemView.findViewById(R.id.actionBox)).getCurrentTextColor());
                m.setClemencyRecommended(false);adapter.onBindViewHolder(holder,0);
                assertEquals(a.getColor(R.color.laya_text),((TextView)holder.itemView.findViewById(R.id.actionBox)).getCurrentTextColor());
            });
        }
    }
    private void ready(ActivityScenario<BreakdownActivity> scenario,boolean enabled)throws Exception{
        AtomicBoolean ready=new AtomicBoolean();
        for(int i=0;i<300;i++){
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            scenario.onActivity(a->{MaterialSwitch toggle=a.findViewById(R.id.considerAllowancesInServiceLimit);
                ready.set(a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE&&((Slider)a.findViewById(R.id.breakdownDateSlider)).isEnabled()&&toggle.isEnabled()&&toggle.isChecked()==enabled);});
            if(ready.get())return;Thread.sleep(20);
        }fail("Breakdown did not finish updating");
    }
    private String text(android.app.Activity a,int id){return ((TextView)a.findViewById(id)).getText().toString();}
    private void capture(View view,int width,String name){
        String folder=System.getProperty("laya.test.screenshots");
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));view.layout(0,0,width,view.getMeasuredHeight());
        if(folder==null)return;
        Bitmap bitmap=Bitmap.createBitmap(width,Math.max(1,view.getHeight()),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas);
        try{File file=new File(folder,name+"-"+width+".png");file.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
        catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
    }
}
