package com.example.myapplication;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import com.example.myapplication.db.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class FreeSlotsTest {
    Context context; AppDatabase db; ProfileDao dao; int owner;
    @Before public void setup() {
        context=ApplicationProvider.getApplicationContext(); context.deleteDatabase("free-slots-test");
        open(); User u=new User();u.username="Test";owner=(int)db.UserDao().insertUser(u);
    }
    private void open(){db=Room.databaseBuilder(context,AppDatabase.class,"free-slots-test").allowMainThreadQueries().build();dao=db.ProfileDao();}
    @After public void cleanup(){db.close();context.deleteDatabase("free-slots-test");}
    private Profile profile(){Profile p=new Profile();p.userID=owner;p.firstName="Slot";p.lastName="Test";
        p.commitmentDate=p.leaArrestDate="2020-01-01";p.minYears=10;p.maxYears=20;return p;}
    private int insert(){return (int)dao.insertProfile(profile());}
    @Test public void oneInitialSlotIsEnforcedAndDeletionReusesIt(){
        assertTrue(BuildConfig.FREE_EDITION);assertEquals(1,dao.profileSlotCapacity());int id=insert();
        try{insert();fail("Second profile accepted");}catch(ProfileDao.SlotLimitException expected){}
        assertEquals(1,dao.countSavedProfiles());dao.deleteUser(dao.getProfileByProfileID(id));
        insert();assertEquals(1,dao.countSavedProfiles());assertEquals(1,dao.profileSlotCapacity());
    }
    @Test public void rewardPersistsAcrossReopeningAndDuplicateCallbacksGrantOnce(){
        SlotRewardSession reward=new SlotRewardSession(dao,"one-ad");assertFalse(reward.earned());
        assertEquals(1,dao.profileSlotCapacity());reward.onEarnedReward();reward.onEarnedReward();
        assertTrue(reward.earned());assertEquals(2,dao.profileSlotCapacity());
        db.close();open();assertEquals(2,dao.profileSlotCapacity());
        new SlotRewardSession(dao,"one-ad").onEarnedReward();assertEquals(2,dao.profileSlotCapacity());
        new SlotRewardSession(dao,"another-ad").onEarnedReward();assertEquals(3,dao.profileSlotCapacity());
        insert();insert();insert();assertEquals(0,dao.availableProfileSlots());
    }
    @Test public void noEarnedCallbackMeansNoReward(){
        SlotRewardSession cancelled=new SlotRewardSession(dao);assertFalse(cancelled.earned());
        assertEquals(1,dao.profileSlotCapacity());assertEquals(0,dao.countSlotRewards());
    }
    @Test public void importsAreAllOrNothingAndDoNotUnlockSlots(){
        try{dao.importProfiles(Arrays.asList(profile(),profile()),owner);fail("Oversized import accepted");}
        catch(ProfileDao.SlotLimitException expected){}
        assertEquals(0,dao.countSavedProfiles());assertEquals(1,dao.profileSlotCapacity());
        dao.grantProfileSlot("earned");dao.importProfiles(Arrays.asList(profile(),profile()),owner);
        assertEquals(2,dao.countSavedProfiles());
        try{dao.importProfiles(Collections.singletonList(profile()),owner);fail("Full import accepted");}
        catch(ProfileDao.SlotLimitException expected){}
        assertEquals(2,dao.countSavedProfiles());
    }
    @Test public void editRemainsAvailableWhenAllSlotsAreUsed(){
        Profile p=dao.getProfileByProfileID(insert());p.firstName="Edited";dao.updateUser(p);
        assertEquals("Edited",dao.getProfileByProfileID(p.profileID).firstName);assertEquals(1,dao.countSavedProfiles());
    }
    @Test public void concurrentSavesCannotOverfillTheLastSlot()throws Exception{
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> save=()->{start.await();try{insert();return true;}catch(ProfileDao.SlotLimitException e){return false;}};
        Future<Boolean> a=pool.submit(save),b=pool.submit(save);start.countDown();
        assertNotEquals(a.get(5,TimeUnit.SECONDS),b.get(5,TimeUnit.SECONDS));assertEquals(1,dao.countSavedProfiles());pool.shutdownNow();
    }
}
