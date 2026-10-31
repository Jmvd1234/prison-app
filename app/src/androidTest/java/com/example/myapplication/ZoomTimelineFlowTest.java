package com.example.myapplication;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.myapplication.db.*;
import com.google.android.material.slider.Slider;
import org.junit.*;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ZoomTimelineFlowTest {
    Context context;AppDatabase db;User owner;SharedPreferences session;long oldOwner;boolean hadOwner;
    @Before public void seed(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();db=AppDatabase.getInstance(context);owner=new User();owner.username="Snake synthetic";owner.userID=(int)db.UserDao().insertUser(owner);session=context.getSharedPreferences("user_session",Context.MODE_PRIVATE);hadOwner=session.contains("currentUserID");oldOwner=session.getLong("currentUserID",-1);session.edit().putLong("currentUserID",owner.userID).commit();}
    @After public void cleanup(){SharedPreferences.Editor e=session.edit();if(hadOwner)e.putLong("currentUserID",oldOwner);else e.remove("currentUserID");e.commit();db.UserDao().deleteUser(owner);}
    int entry(int years){Profile p=new Profile();p.userID=owner.userID;p.firstName="Snake";p.lastName="Timeline";p.commitmentDate="2000-01-01";p.leaArrestDate="1999-12-01";p.minYears=years/2;p.maxYears=years;p.disqualifiedForCPI=true;p.disqualifiedForTimeAllowances=true;return (int)db.ProfileDao().insertProfile(p);}
    ActivityScenario<BreakdownActivity> open(int id){return ActivityScenario.launch(new Intent(context,BreakdownActivity.class).putExtra(BreakdownActivity.EXTRA_PROFILE_ID,id));}
    void ready(ActivityScenario<BreakdownActivity>s){AtomicBoolean done=new AtomicBoolean();long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){s.onActivity(a->done.set(a.findViewById(R.id.breakdownCard).getVisibility()==View.VISIBLE&&a.findViewById(R.id.timelineZoomScroll).getWidth()>0));if(done.get())return;SystemClock.sleep(50);}final String[] reason={""};s.onActivity(a->reason[0]=((TextView)a.findViewById(R.id.breakdownStatus)).getText().toString());fail("Not ready: "+reason[0]);}
    void orientation(ActivityScenario<BreakdownActivity>s,boolean landscape){AtomicBoolean done=new AtomicBoolean();long end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){s.onActivity(a->{View root=a.getWindow().getDecorView();done.set(root.getWidth()>0&&(root.getWidth()>root.getHeight())==landscape);});if(done.get()){InstrumentationRegistry.getInstrumentation().waitForIdleSync();return;}SystemClock.sleep(50);}fail("Orientation did not settle");}
    @Test public void zoomPanFitAndRotationKeepObservation(){int id=entry(40);try(ActivityScenario<BreakdownActivity>s=open(id)){ready(s);
        final float[] value={0};s.onActivity(a->{value[0]=((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue();((ZoomTimelineScrollView)a.findViewById(R.id.timelineZoomScroll)).zoomBy(.00001f,0);a.findViewById(R.id.timelineZoomIn).performClick();});InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        s.onActivity(a->{ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);assertEquals(2,z.getZoom(),.01);assertTrue(z.getChildAt(0).getWidth()>z.getWidth());z.scrollTo(100,0);assertEquals(value[0],((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0);});
        s.recreate();ready(s);InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);assertEquals(2,z.getZoom(),.01);assertEquals(value[0],((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0);z.zoomBy(.00001f,0);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        s.onActivity(a->{ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);assertEquals(1,z.getZoom(),0);assertEquals(z.getWidth(),z.getChildAt(0).getWidth());assertEquals(0,z.getScrollX());});assertNull(db.ProfileDao().getProfileByProfileID(id).observationDate);
    }}
    @Test public void originalSliderStillSelectsExactDatesAtMaximumZoom(){try(ActivityScenario<BreakdownActivity>s=open(entry(40))){ready(s);s.onActivity(a->{ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);z.zoomBy(10000,.5f);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{Slider slider=a.findViewById(R.id.breakdownDateSlider);slider.setValue(101);String date=((TextView)a.findViewById(R.id.breakdownObservationDate)).getText().toString();assertTrue("Date: "+date,date.contains(LocalDate.of(1999,12,1).plusDays(101).format(SentenceCalculator.DISPLAY_DATE)));assertTrue("Track="+slider.getTrackWidth()+" last="+slider.getValueTo()+" zoom="+((ZoomTimelineScrollView)a.findViewById(R.id.timelineZoomScroll)).getZoom(),slider.getTrackWidth()/slider.getValueTo()>=3*a.getResources().getDisplayMetrics().density);});}}
    float pinchY=-1;
    void pinchEvent(View v,long start,int action,float span,int count){
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=v.getWidth()/2f+(i==0?-span/2:span/2);coords[i].y=pinchY<0?v.getHeight()-10:pinchY;coords[i].pressure=1;coords[i].size=1;}
        MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);v.dispatchTouchEvent(event);event.recycle();
    }
    @Test public void pinchZoomsAndRenders(){try(ActivityScenario<BreakdownActivity>s=open(entry(40))){ready(s);final float[] value={0};s.onActivity(a->{ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);value[0]=((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue();long now=SystemClock.uptimeMillis();pinchEvent(z,now,MotionEvent.ACTION_DOWN,240,1);pinchEvent(z,now,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),240,2);pinchEvent(z,now,MotionEvent.ACTION_MOVE,400,2);pinchEvent(z,now,MotionEvent.ACTION_MOVE,600,2);pinchEvent(z,now,MotionEvent.ACTION_MOVE,800,2);pinchEvent(z,now,MotionEvent.ACTION_MOVE,1000,2);pinchEvent(z,now,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),600,2);pinchEvent(z,now,MotionEvent.ACTION_UP,600,1);assertTrue("Zoom="+z.getZoom()+" width="+z.getWidth()+" height="+z.getHeight(),z.getZoom()>1);assertEquals(value[0],((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{View v=a.findViewById(R.id.timelineZoomScroll);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{saveRender(a,"zoom-portrait.png");a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});orientation(s,true);ready(s);s.onActivity(a->{assertEquals(value[0],((Slider)a.findViewById(R.id.breakdownDateSlider)).getValue(),0);View v=a.findViewById(R.id.timelineZoomScroll);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{saveRender(a,"zoom-landscape.png");});}}
    @Test public void pinchingOnTrackPreservesObservation(){try(ActivityScenario<BreakdownActivity>s=open(entry(40))){ready(s);s.onActivity(a->{ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);Slider slider=a.findViewById(R.id.breakdownDateSlider);float value=slider.getValue();pinchY=slider.getTop()+slider.getHeight()/2f;long now=SystemClock.uptimeMillis();pinchEvent(z,now,MotionEvent.ACTION_DOWN,240,1);pinchEvent(z,now,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),240,2);for(int span=400;span<=1000;span+=200)pinchEvent(z,now,MotionEvent.ACTION_MOVE,span,2);pinchEvent(z,now,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),1000,2);pinchEvent(z,now,MotionEvent.ACTION_UP,1000,1);assertTrue(z.getZoom()>1);assertEquals(value,slider.getValue(),0);});}}
    void dragEvent(ZoomTimelineScrollView z,int action,float x){View slider=((android.view.ViewGroup)z.getChildAt(0)).getChildAt(0);long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(now,now,action,x,slider.getTop()+slider.getHeight()/2f,0);z.dispatchTouchEvent(event);event.recycle();}
    float handleX(ZoomTimelineScrollView z,Slider slider){return slider.getTrackSidePadding()+(slider.getValue()-slider.getValueFrom())/(slider.getValueTo()-slider.getValueFrom())*slider.getTrackWidth()-z.getScrollX();}
    @Test public void edgeDragPansBothWaysKeepsHandleVisibleAndStopsOnLift(){try(ActivityScenario<BreakdownActivity>s=open(entry(40))){ready(s);final BreakdownActivity[] activity={null};s.onActivity(a->activity[0]=a);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{BreakdownActivity a=activity[0];ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);z.zoomBy(8,.5f);});InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        final float[] previous=new float[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{BreakdownActivity a=activity[0];ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);Slider slider=a.findViewById(R.id.breakdownDateSlider);z.scrollTo(Math.round(slider.getTrackSidePadding()+slider.getValue()/slider.getValueTo()*slider.getTrackWidth()-z.getWidth()/2f),0);dragEvent(z,MotionEvent.ACTION_DOWN,handleX(z,slider));dragEvent(z,MotionEvent.ACTION_MOVE,z.getWidth()-12*a.getResources().getDisplayMetrics().density);previous[0]=z.getScrollX();previous[1]=slider.getValue();});
        SystemClock.sleep(350);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{BreakdownActivity a=activity[0];ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);Slider slider=a.findViewById(R.id.breakdownDateSlider);assertTrue("view follows right drag",z.getScrollX()>previous[0]);assertTrue("date advances",slider.getValue()>previous[1]);assertTrue("handle stays onscreen",handleX(z,slider)>=0&&handleX(z,slider)<=z.getWidth());dragEvent(z,MotionEvent.ACTION_MOVE,12*a.getResources().getDisplayMetrics().density);previous[0]=z.getScrollX();previous[1]=slider.getValue();});
        SystemClock.sleep(350);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{BreakdownActivity a=activity[0];ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);Slider slider=a.findViewById(R.id.breakdownDateSlider);assertTrue("view follows left drag",z.getScrollX()<previous[0]);assertTrue("date reverses",slider.getValue()<previous[1]);assertTrue("handle stays onscreen",handleX(z,slider)>=0&&handleX(z,slider)<=z.getWidth());dragEvent(z,MotionEvent.ACTION_UP,12*a.getResources().getDisplayMetrics().density);previous[0]=z.getScrollX();previous[1]=slider.getValue();});
        SystemClock.sleep(200);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{BreakdownActivity a=activity[0];ZoomTimelineScrollView z=a.findViewById(R.id.timelineZoomScroll);Slider slider=a.findViewById(R.id.breakdownDateSlider);assertEquals(previous[0],z.getScrollX(),0);assertEquals(previous[1],slider.getValue(),0);});
    }}
    Object overviewField(WrappedTimelineOverview v,String name){try{java.lang.reflect.Field f=WrappedTimelineOverview.class.getDeclaredField(name);f.setAccessible(true);return f.get(v);}catch(Exception e){throw new AssertionError(e);}}
    List<Slider> overviewRows(WrappedTimelineOverview v){List<Slider> rows=new ArrayList<>();for(int i=0;i<v.getChildCount();i++)if(v.getChildAt(i) instanceof Slider)rows.add((Slider)v.getChildAt(i));rows.sort(java.util.Comparator.comparingInt(View::getTop));return rows;}




    TextView bubble(WrappedTimelineOverview overview,String title){for(int i=0;i<overview.getChildCount();i++){View v=overview.getChildAt(i);if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(title))return (TextView)v;}throw new AssertionError("Missing bubble: "+title);}

    TextView detailBubble(TimelineMilestonesLayout timeline,String title){
        for(int i=1;i<timeline.getChildCount();i++){View v=timeline.getChildAt(i);
            if(v instanceof TextView&&((TextView)v).getText().toString().startsWith(title))return (TextView)v;}
        throw new AssertionError("Missing bubble: "+title);
    }
    @Test public void defaultZoomUsesDailySpacingAndCustodyBubblesJump(){try(ActivityScenario<BreakdownActivity>s=open(entry(40))){ready(s);InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{
        Slider slider=a.findViewById(R.id.breakdownDateSlider);
        assertEquals(4*a.getResources().getDisplayMetrics().density,slider.getTrackWidth()/slider.getValueTo(),.02);
        TimelineMilestonesLayout timeline=a.findViewById(R.id.breakdownTimeline);
        detailBubble(timeline,a.getString(R.string.breakdown_marker_arrest)).performClick();assertEquals(0,slider.getValue(),0);
        detailBubble(timeline,a.getString(R.string.breakdown_marker_prison)).performClick();assertEquals(31,slider.getValue(),0);
        detailBubble(timeline,a.getString(R.string.breakdown_today)).performClick();
        assertEquals(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(1999,12,1),LocalDate.now()),slider.getValue(),0);
        slider.setValue(100);
        long now=SystemClock.uptimeMillis();float day=slider.getTrackWidth()/slider.getValueTo();
        for(int offset:new int[]{0,4,5}){
            MotionEvent e=MotionEvent.obtain(now,now+offset*30,offset==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,
                    slider.getTrackSidePadding()+(100+offset)*day,slider.getHeight()/2f,0);
            slider.dispatchTouchEvent(e);e.recycle();
            if(offset>0)assertEquals(100+offset,slider.getValue(),0);
        }
        MotionEvent up=MotionEvent.obtain(now,now+200,MotionEvent.ACTION_UP,slider.getTrackSidePadding()+105*day,slider.getHeight()/2f,0);
        slider.dispatchTouchEvent(up);up.recycle();
        assertTrue(slider.getTop()>=40*a.getResources().getDisplayMetrics().density);
        assertTrue(timeline.getHeight()-slider.getBottom()>=40*a.getResources().getDisplayMetrics().density);
    });}}
    @Test public void fitAllSeparatesBubblesAboveAndBelowWithoutOverlap(){try(ActivityScenario<BreakdownActivity>s=open(entry(40))){ready(s);s.onActivity(a->((ZoomTimelineScrollView)a.findViewById(R.id.timelineZoomScroll)).zoomBy(.00001f,0));InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->{
        TimelineMilestonesLayout timeline=a.findViewById(R.id.breakdownTimeline);Slider slider=a.findViewById(R.id.breakdownDateSlider);
        boolean above=false,below=false;List<Rect> boxes=new ArrayList<>();
        for(int i=1;i<timeline.getChildCount();i++){View v=timeline.getChildAt(i);above|=v.getBottom()<=slider.getTop();below|=v.getTop()>=slider.getBottom();
            boxes.add(new Rect(v.getLeft(),v.getTop(),v.getRight(),v.getBottom()));
            TextView label=(TextView)v;assertEquals(10*a.getResources().getDisplayMetrics().scaledDensity,label.getTextSize(),.1);assertFalse(label.getIncludeFontPadding());
        }
        assertTrue(above&&below);for(int i=0;i<boxes.size();i++)for(int k=i+1;k<boxes.size();k++)assertFalse(Rect.intersects(boxes.get(i),boxes.get(k)));
        View v=a.findViewById(R.id.timelineZoomScroll);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);
    });InstrumentationRegistry.getInstrumentation().waitForIdleSync();s.onActivity(a->saveRender(a,"single-timeline-fit.png"));}}
    void saveRender(BreakdownActivity a,String name){View root=a.getWindow().getDecorView();android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(bitmap));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(Exception e){throw new AssertionError(e);}finally{bitmap.recycle();}}
}
