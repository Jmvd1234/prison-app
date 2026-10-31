package com.example.myapplication;

import android.content.*;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.CheckBox;
import androidx.test.core.app.ActivityScenario;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class ProfileTransferTest {
    Context context;AppDatabase db;User source,target;SharedPreferences session;long oldOwner;
    List<String> photos=new ArrayList<>();
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=AppDatabase.getInstance(context);
        source=new User();source.username="Transfer source";source.userID=(int)db.UserDao().insertUser(source);
        target=new User();target.username="Transfer target";target.userID=(int)db.UserDao().insertUser(target);
        session=context.getSharedPreferences("user_session",0);oldOwner=session.getLong("currentUserID",-1);session.edit().putLong("currentUserID",source.userID).commit();}
    @After public void cleanup(){session.edit().putLong("currentUserID",oldOwner).commit();
        for(Profile p:db.ProfileDao().getProfilesByUserID(target.userID))if(p.image_path!=null)photos.add(p.image_path);
        db.UserDao().deleteUser(source);db.UserDao().deleteUser(target);
        for(String p:photos){File f=ProfilePhotoStore.resolve(context,p);if(f!=null)f.delete();}}
    Profile profile(){Profile p=new Profile();p.userID=source.userID;p.firstName="José, \"Test\"\n名";p.lastName="Transfer";p.commitmentDate="2020-01-01";p.leaArrestDate="2019-12-01";
        p.separateCustodyDates=true;p.prisonTransferDate="2021-01-01";p.jailCommitmentDate=p.commitmentDate;p.prisonCommitmentDate=p.prisonTransferDate;p.deductionDays=7;p.timeGapDays=3;p.stalPercent=20;
        for(int y:new int[]{5,3,2}){ProfileCharge c=new ProfileCharge();c.minYears=y-1;c.maxYears=y;p.charges.add(c);}
        p.deductionMonths.add(new DeductionMonth(2020,2,true,false));p.deductionMonths.add(new DeductionMonth(2021,3,false,true));
        SequentialSentencePlan plan=new SequentialSentencePlan(p);p.serviceChoices=plan.appendCustomDate(LocalDate.of(2020,2,1),LocalDate.of(2020,2,1));return p;}
    int save(Profile p){int id=(int)db.ProfileDao().insertProfile(p);db.ProfileDao().saveDeductionMonths(id,source.userID,p.deductionMonths);return id;}
    byte[] archive(int... ids)throws Exception{List<Integer> selected=new ArrayList<>();for(int id:ids)selected.add(id);ByteArrayOutputStream out=new ByteArrayOutputStream();ProfileTransferArchive.exportProfiles(context,selected,source.userID,out);return out.toByteArray();}
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void fullRoundTripPreservesChildrenChoicesPhotosAndCalculationsWithoutOverwriting()throws Exception{
        Profile original=profile();String path="profile_photos/"+UUID.randomUUID()+".jpg";File photo=ProfilePhotoStore.resolve(context,path);photo.getParentFile().mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(android.graphics.Color.BLUE);try(FileOutputStream out=new FileOutputStream(photo)){bitmap.compress(Bitmap.CompressFormat.JPEG,90,out);}bitmap.recycle();photos.add(path);original.image_path=path;
        int id=save(original);Profile existing=profile();existing.userID=target.userID;existing.firstName="Existing";int existingId=(int)db.ProfileDao().insertProfile(existing);
        try(ProfileTransferArchive.Preview preview=ProfileTransferArchive.read(context,new ByteArrayInputStream(archive(id)))){
            assertEquals(1,preview.profiles.size());ProfileTransferArchive.restore(context,preview,target.userID);
        }
        List<Profile> all=db.ProfileDao().getProfilesByUserID(target.userID);assertEquals(2,all.size());
        assertEquals("Existing",db.ProfileDao().getProfileByProfileID(existingId).firstName);
        Profile imported=all.get(0).profileID==existingId?all.get(1):all.get(0);Profile saved=db.ProfileDao().getProfileByProfileID(id);
        assertNotEquals(id,imported.profileID);assertEquals(saved.firstName,imported.firstName);assertEquals(3,imported.charges.size());
        assertEquals(saved.deductionMonths,imported.deductionMonths);assertEquals(saved.serviceChoices,imported.serviceChoices);
        assertEquals(saved.timeGapDays,imported.timeGapDays);assertEquals(saved.stalPercent,imported.stalPercent);assertEquals(saved.prisonTransferDate,imported.prisonTransferDate);
        assertNotEquals(path,imported.image_path);assertNotNull(ProfilePhotoStore.load(context,imported.image_path));
        assertArrayEquals(java.nio.file.Files.readAllBytes(photo.toPath()),java.nio.file.Files.readAllBytes(ProfilePhotoStore.resolve(context,imported.image_path).toPath()));
        ByteArrayOutputStream before=new ByteArrayOutputStream(),after=new ByteArrayOutputStream();
        ProfileTransferCodec.write(saved,before);ProfileTransferCodec.write(imported,after);assertArrayEquals(before.toByteArray(),after.toByteArray());
        LocalDate date=LocalDate.of(2022,3,1);assertEquals(new SequentialSentencePlan(saved).calculate(date).maxRemainingDays,new SequentialSentencePlan(imported).calculate(date).maxRemainingDays);
        for(ProfileCharge c:imported.charges)assertEquals(imported.profileID,c.profileID);
    }
    @Test public void exportOnlyIncludesSelectedOwnerEntriesAndRejectsOtherOwner()throws Exception{
        int first=save(profile()),second=save(profile());
        try(ProfileTransferArchive.Preview preview=ProfileTransferArchive.read(context,new ByteArrayInputStream(archive(second)))){assertEquals(1,preview.profiles.size());}
        Profile foreign=profile();foreign.userID=target.userID;int id=(int)db.ProfileDao().insertProfile(foreign);
        try{archive(first,id);fail("Cross-owner export");}catch(IllegalStateException expected){}
    }
    @Test public void malformedArchiveAndTraversalAreRejectedBeforeAnyDatabaseWrites()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("../outside.txt"));zip.write(1);zip.closeEntry();}
        for(byte[] bytes:new byte[][]{out.toByteArray(),"not a backup".getBytes()}){
            try(ProfileTransferArchive.Preview ignored=ProfileTransferArchive.read(context,new ByteArrayInputStream(bytes))){fail("Malformed backup accepted");}catch(IOException expected){}
        }assertTrue(db.ProfileDao().getProfilesByUserID(target.userID).isEmpty());
    }
    @Test public void failedChildInsertRollsBackAllImportedEntries()throws Exception{
        Profile first=profile(),second=profile();second.charges.get(0).minYears=99;
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER reject_transfer_test BEFORE INSERT ON ProfileCharge WHEN NEW.minYears=99 BEGIN SELECT RAISE(ABORT,'test failure'); END");
        try{try{db.ProfileDao().importProfiles(Arrays.asList(first,second),target.userID);fail();}catch(RuntimeException expected){}
            assertTrue(db.ProfileDao().getProfilesByUserID(target.userID).isEmpty());
        }finally{db.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER reject_transfer_test");}
    }
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test public void dashboardSelectionAndSelectAllSurviveRotation()throws Exception{
        save(profile());save(profile());try(ActivityScenario<DashboardActivity> scenario=ActivityScenario.launch(DashboardActivity.class)){
            waitForRows(scenario,2);scenario.onActivity(a->{a.findViewById(R.id.exportProfiles).performClick();a.findViewById(R.id.selectAllProfiles).performClick();});
            scenario.recreate();waitForRows(scenario,2);
            scenario.onActivity(a->{assertEquals("Export (2)",((android.widget.TextView)a.findViewById(R.id.exportProfiles)).getText().toString());
                assertEquals(View.VISIBLE,a.findViewById(R.id.exportSelectionControls).getVisibility());
                androidx.recyclerview.widget.RecyclerView list=a.findViewById(R.id.profileRecyclerView);
                render(a.getWindow().getDecorView(),"dashboard-export.png");
                View row=list.getChildAt(0);CheckBox checkbox=row.findViewById(R.id.exportProfileCheck);assertTrue(checkbox.isChecked());checkbox.performClick();});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{assertEquals("Export (1)",((android.widget.TextView)a.findViewById(R.id.exportProfiles)).getText().toString());a.findViewById(R.id.cancelExport).performClick();assertEquals(View.GONE,a.findViewById(R.id.exportSelectionControls).getVisibility());});
        }
    }

    @Test public void summaryButtonOpensTheSavedEntryDirectly(){
        Profile p=profile();int id=save(p);
        SentenceCalculator.Result result=SequentialSentencePlan.initialResult(p,LocalDate.now());
        android.os.Bundle values=ProfileBreakdownDialogFragment.createArguments("Minimum","Maximum",result);
        values.putInt(BreakdownActivity.EXTRA_PROFILE_ID,id);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->{ProfileBreakdownDialogFragment dialog=ProfileBreakdownDialogFragment.newInstance(values);
                dialog.showNow(a.getSupportFragmentManager(),ProfileBreakdownDialogFragment.TAG);
                View button=dialog.requireDialog().findViewById(R.id.openDetailedBreakdown);assertEquals(View.VISIBLE,button.getVisibility());button.performClick();
                Intent intent=org.robolectric.Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(intent);
                assertEquals(BreakdownActivity.class.getName(),intent.getComponent().getClassName());assertEquals(id,intent.getIntExtra(BreakdownActivity.EXTRA_PROFILE_ID,-1));});
        }
    }
    @Test public void customPickerRestoresAndSubmitsItsDateAfterRotation(){
        Profile p=profile();p.serviceChoices="";SequentialSentencePlan plan=new SequentialSentencePlan(p);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->{AddServiceChargeDialog d=AddServiceChargeDialog.create(plan,plan.start);d.showNow(a.getSupportFragmentManager(),AddServiceChargeDialog.TAG);
                d.requireDialog().findViewById(R.id.serviceAddToCustom).performClick();a.getSupportFragmentManager().executePendingTransactions();d.getChildFragmentManager().executePendingTransactions();});
            scenario.recreate();
            scenario.onActivity(a->{java.util.concurrent.atomic.AtomicReference<android.os.Bundle> result=new java.util.concurrent.atomic.AtomicReference<>();
                a.getSupportFragmentManager().setFragmentResultListener(AddServiceChargeDialog.RESULT,a,(key,value)->result.set(value));
                AddServiceChargeDialog d=(AddServiceChargeDialog)a.getSupportFragmentManager().findFragmentByTag(AddServiceChargeDialog.TAG);assertNotNull(d);
                com.google.android.material.datepicker.MaterialDatePicker<?> picker=(com.google.android.material.datepicker.MaterialDatePicker<?>)d.getChildFragmentManager().findFragmentByTag("custom_end_date");assertNotNull(picker);
                picker.requireDialog().findViewById(com.google.android.material.R.id.confirm_button).performClick();
                assertNotNull(result.get());assertEquals(plan.start.toString(),result.get().getString("customDate"));});
        }
    }
    @Test public void pendingChargeMarkerIsCappedWithinFullRangeAfterPopupDismissal()throws Exception{
        Profile p=profile();p.serviceChoices="";int id=save(p);SequentialSentencePlan plan=new SequentialSentencePlan(p);
        try(ActivityScenario<BreakdownActivity> scenario=ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,id))){
            java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
            for(int i=0;i<200;i++){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();scenario.onActivity(a->ready.set(a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE&&((com.google.android.material.slider.Slider)a.findViewById(R.id.breakdownDateSlider)).isEnabled()));if(ready.get())break;Thread.sleep(20);}
            assertTrue(ready.get());
            scenario.onActivity(a->{com.google.android.material.slider.Slider slider=a.findViewById(R.id.breakdownDateSlider);
                assertTrue(slider.getValueTo()>java.time.temporal.ChronoUnit.DAYS.between(plan.start,plan.active().maximum));
                AddServiceChargeDialog dialog=(AddServiceChargeDialog)a.getSupportFragmentManager().findFragmentByTag(AddServiceChargeDialog.TAG);assertNotNull(dialog);dialog.dismissNow();
                slider.setValue(slider.getValueTo());assertEquals(java.time.temporal.ChronoUnit.DAYS.between(plan.start,plan.active().maximum),slider.getValue(),0);assertTrue(slider.getValueTo()>java.time.temporal.ChronoUnit.DAYS.between(plan.start,plan.active().maximum));});
            scenario.recreate();
        }
    }

    @Test public void newlySavedEntrySuppliesItsGeneratedIdToTheSummary()throws Exception{
        ProfilePhotoViewModel model=new ProfilePhotoViewModel((android.app.Application)context.getApplicationContext(),new androidx.lifecycle.SavedStateHandle());
        model.initialize(null);model.save(profile(),true,new android.os.Bundle());
        for(int i=0;i<100&&model.completion.getValue()==null;i++){Thread.sleep(20);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();}
        assertNotNull(model.completion.getValue());int id=model.completion.getValue().getInt(BreakdownActivity.EXTRA_PROFILE_ID,-1);
        assertTrue(id>0);assertEquals(source.userID,db.ProfileDao().getProfileByProfileID(id).userID);
    }
    @Test public void missingFieldsAndFutureFormatCannotSilentlyImportDefaults()throws Exception{
        ByteArrayOutputStream record=new ByteArrayOutputStream();ProfileTransferCodec.write(profile(),record);
        String json=record.toString("UTF-8").replace("\"timeGapDays\":3,","");
        try{ProfileTransferCodec.read(new ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));fail("Missing field accepted");}catch(IOException expected){}
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){
            zip.putNextEntry(new ZipEntry("manifest.txt"));zip.write("Laya profiles\n99\n1\n".getBytes());zip.closeEntry();
            zip.putNextEntry(new ZipEntry("profiles/0.json"));record.writeTo(zip);zip.closeEntry();
        }
        try(ProfileTransferArchive.Preview ignored=ProfileTransferArchive.read(context,new ByteArrayInputStream(out.toByteArray()))){fail("Unknown version accepted");}catch(IOException expected){}
        assertTrue(db.ProfileDao().getProfilesByUserID(target.userID).isEmpty());
    }
    private void render(View view,String filename){
        String directory=System.getProperty("laya.test.screenshots");if(directory==null)return;
        view.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));view.layout(0,0,1080,1920);
        Bitmap bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(bitmap));
        try{File file=new File(directory,filename);file.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}}
        catch(IOException e){throw new AssertionError(e);}finally{bitmap.recycle();}
    }
    void waitForRows(ActivityScenario<DashboardActivity> scenario,int count)throws Exception{
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
        for(int i=0;i<200;i++){scenario.onActivity(a->ready.set(a.profileModels.size()==count));if(ready.get()){InstrumentationRegistry.getInstrumentation().waitForIdleSync();return;}Thread.sleep(50);}fail("Dashboard did not load");
    }
}
