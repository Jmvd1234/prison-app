package com.example.myapplication;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeleteLoginProfileFlowTest {
    private Context context;
    private AppDatabase db;
    private LoginProfileRepository repository;
    private SharedPreferences session;
    private long previous;
    private final List<User> users=new ArrayList<>();
    private final List<String> photos=new ArrayList<>();
    private ActivityScenario<MainActivity> screen;

    @Before public void setup() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        db=AppDatabase.getInstance(context);repository=new LoginProfileRepository(db);repository.load();
        session=context.getSharedPreferences("user_session",0);previous=session.getLong("currentUserID",-1);
    }
    @After public void cleanupOnlySyntheticData() {
        if(screen!=null)screen.close();
        for(User user:users)db.UserDao().deleteUser(user);
        for(String path:photos)ProfilePhotoStore.deleteIfUnused(context,path);
        SharedPreferences.Editor edit=session.edit();
        if(previous<0)edit.remove("currentUserID");else edit.putLong("currentUserID",previous);edit.commit();
    }

    @Test public void twoConfirmationsCancelRotateAndDeleteOnlyChosenOwnersDataAndUnusedPhotos() throws Exception {
        User target=create("Remove",false),keep=create("Keep",false);
        String exclusive=photo(),shared=photo();
        int first=entry(target,exclusive),second=entry(target,shared),retained=entry(keep,shared);
        open();
        await(a->{
            View guest=a.findViewById(R.id.loginProfilesContainer).findViewWithTag(db.UserDao().getGuest().userID);
            assertEquals(View.GONE,guest.findViewById(R.id.deleteLoginProfileButton).getVisibility());return true;
        });
        screenshot("01-delete-buttons");
        tapDelete(target);
        await(a->popup(a)!=null);
        assertIntact(target,first,second,exclusive);
        screen.recreate();ready();
        await(a->popup(a)!=null);
        screen.onActivity(a->{
            assertEquals(context.getString(R.string.login_delete_continue),popup(a).getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
            popup(a).getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        });
        await(a->popup(a)==null);
        assertIntact(target,first,second,exclusive);
        tapDelete(target);await(a->popup(a)!=null);screenshot("02-first-confirmation");
        positive();awaitFinal();screenshot("03-final-confirmation");
        assertIntact(target,first,second,exclusive);
        screen.onActivity(a->popup(a).getButton(DialogInterface.BUTTON_NEGATIVE).performClick());
        await(a->popup(a)==null);assertIntact(target,first,second,exclusive);
        tapDelete(target);await(a->popup(a)!=null);positive();awaitFinal();
        screen.recreate();ready();awaitFinal();
        assertIntact(target,first,second,exclusive);
        positive();
        await(a->db.UserDao().getUserByID(target.userID)==null&&popup(a)==null
                &&a.findViewById(R.id.loginProfilesContainer).findViewWithTag(target.userID)==null);
        long until=SystemClock.uptimeMillis()+5000;
        while(ProfilePhotoStore.resolve(context,exclusive).exists()&&SystemClock.uptimeMillis()<until)SystemClock.sleep(50);
        assertFalse(ProfilePhotoStore.resolve(context,exclusive).exists());
        assertTrue(ProfilePhotoStore.resolve(context,shared).exists());
        assertNull(db.ProfileDao().getProfileByProfileID(first));assertNull(db.ProfileDao().getProfileByProfileID(second));
        children(first,0);children(second,0);children(retained,1);
        assertNotNull(db.UserDao().getUserByID(keep.userID));assertNotNull(db.UserDao().getGuest());
        assertEquals(-1,session.getLong("currentUserID",-1));
        screen.recreate();ready();
        screen.onActivity(a->assertNull(a.findViewById(R.id.loginProfilesContainer).findViewWithTag(target.userID)));
    }

    @Test public void protectedProfileRequiresPasswordInSecondPopupAndClearsItOnRotation() throws Exception {
        User target=create("Protected",true);
        int entry=entry(target,null);
        open();tapDelete(target);await(a->popup(a)!=null);
        screen.onActivity(a->assertEquals(View.GONE,popup(a).findViewById(R.id.deleteLoginPasswordFields).getVisibility()));
        positive();awaitFinal();
        screen.onActivity(a->assertEquals(View.VISIBLE,popup(a).findViewById(R.id.deleteLoginPasswordFields).getVisibility()));
        positive();error(R.string.login_password_required);
        screen.onActivity(a->password(a).setText("wrong"));positive();error(R.string.login_password_incorrect);
        assertNotNull(db.UserDao().getUserByID(target.userID));children(entry,1);
        screen.onActivity(a->password(a).setText(" correct password "));
        screen.recreate();ready();awaitFinal();
        screen.onActivity(a->{assertEquals("",password(a).getText().toString());password(a).setText(" correct password ");});
        screenshot("04-protected-confirmation");
        positive();
        await(a->db.UserDao().getUserByID(target.userID)==null&&popup(a)==null);
        children(entry,0);assertEquals(-1,session.getLong("currentUserID",-1));
    }

    private User create(String name,boolean locked)throws Exception {
        User user=repository.create(name+" "+UUID.randomUUID().toString().substring(0,6),locked," correct password ");
        users.add(user);return user;
    }
    private String photo()throws Exception {
        String path="profile_photos/"+UUID.randomUUID()+".jpg";photos.add(path);
        File file=ProfilePhotoStore.resolve(context,path);file.getParentFile().mkdirs();
        try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[]{1,2,3});}return path;
    }
    private int entry(User owner,String path) {
        Profile p=new Profile();p.userID=owner.userID;p.firstName="Synthetic";p.image_path=path;
        p.leaArrestDate="2023-01-15";p.commitmentDate="2023-02-15";p.minYears=1;p.maxYears=3;
        int id=(int)db.ProfileDao().insertProfile(p);
        db.ProfileDao().saveDeductionMonths(id,owner.userID,Collections.singletonList(new DeductionMonth(2023,2,true,true)));
        return id;
    }
    private void children(int id,int expected) {
        for(String table:new String[]{"ProfileCharge","ProfileDeduction"})
            try(android.database.Cursor c=db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM "+table+" WHERE profileID="+id)){
                c.moveToFirst();assertEquals(expected,c.getInt(0));
            }
    }
    private void assertIntact(User owner,int first,int second,String photo) {
        assertNotNull(db.UserDao().getUserByID(owner.userID));children(first,1);children(second,1);
        assertTrue(ProfilePhotoStore.resolve(context,photo).exists());assertEquals(-1,session.getLong("currentUserID",-1));
    }
    private void open(){screen=ActivityScenario.launch(MainActivity.class);ready();}
    private void ready(){await(a->a.findViewById(R.id.createLoginProfileButton).isEnabled());}
    private void tapDelete(User user){onView(allOf(withId(R.id.deleteLoginProfileButton),isDescendantOfA(withTagValue(is(user.userID))))).perform(scrollTo(),click());}
    private AlertDialog popup(MainActivity a){DeleteLoginProfileDialogFragment f=(DeleteLoginProfileDialogFragment)a.getSupportFragmentManager().findFragmentByTag(DeleteLoginProfileDialogFragment.TAG);return f==null?null:(AlertDialog)f.getDialog();}
    private EditText password(MainActivity a){return popup(a).findViewById(R.id.deleteLoginPassword);}
    private void positive(){screen.onActivity(a->popup(a).getButton(DialogInterface.BUTTON_POSITIVE).performClick());}
    private void awaitFinal(){await(a->popup(a)!=null&&popup(a).getButton(DialogInterface.BUTTON_POSITIVE)!=null
            &&popup(a).getButton(DialogInterface.BUTTON_POSITIVE).getText().toString().equals(context.getString(R.string.login_delete_action)));}
    private void error(int message){await(a->popup(a)!=null&&((TextView)popup(a).findViewById(R.id.deleteLoginError)).getText().toString().equals(context.getString(message)));}
    private void await(Predicate<MainActivity> predicate){
        long limit=SystemClock.uptimeMillis()+15000;
        while(SystemClock.uptimeMillis()<limit){AtomicBoolean done=new AtomicBoolean();screen.onActivity(a->done.set(predicate.test(a)));if(done.get())return;SystemClock.sleep(50);}
        fail("Timed out waiting for profile deletion flow");
    }
    private void screenshot(String name)throws Exception {
        if(!"true".equals(InstrumentationRegistry.getArguments().getString("captureDeletion")))return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File folder=new File(context.getExternalFilesDir(null),"delete-profile-preview");folder.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
