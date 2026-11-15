package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ApplicationProvider;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import com.google.android.material.slider.Slider;
import java.time.LocalDate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LateCustomChargeViewTest {
    private Profile profile(){
        Profile p=new Profile();p.commitmentDate=p.leaArrestDate="2020-01-01";
        p.disqualifiedForCPI=p.disqualifiedForTimeAllowances=true;
        for(int[] terms:new int[][]{{100,200},{30,80}}){
            ProfileCharge c=new ProfileCharge();c.minDays=terms[0];c.maxDays=terms[1];p.charges.add(c);
        }
        return p;
    }
    @Test public void timelineShowsDelayAndBothBoundariesThenClearsThemOnUndo()throws Exception{
        Context context=new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),R.style.Theme_MyApplication);
        View root=LayoutInflater.from(context).inflate(R.layout.activity_breakdown,null);
        TimelineMilestonesLayout timeline=root.findViewById(R.id.breakdownTimeline);
        ((ViewGroup)timeline.getParent()).removeView(timeline);
        Slider slider=timeline.findViewById(R.id.breakdownDateSlider);
        Profile p=profile();SequentialSentencePlan first=new SequentialSentencePlan(p);
        LocalDate boundary=first.active().maximum.plusDays(45);
        p.serviceChoices=first.appendCustomDate(boundary,first.active().maximum);
        SequentialSentencePlan next=new SequentialSentencePlan(p);
        LocalDate observation=boundary.plusDays(10);
        SentenceProjection.Range range=next.range(first.start,true);
        slider.setValueTo(range.dates.size()-1);slider.setStepSize(1);
        slider.setValue(range.dates.indexOf(observation));
        timeline.setMilestones(range,d->{});timeline.setChargeContext(next);
        timeline.setPreview(observation,SentenceProjection.releaseDates(range,observation,next.calculate(observation)));
        timeline.setOverdueStart(next.overdueStart(observation));
        assertEquals(1,count(timeline,"Custom end date\n"+boundary.format(SentenceCalculator.DISPLAY_DATE)+"\nCharge #1"));
        assertEquals(1,count(timeline,"Max. time served\n"+first.active().maximum.format(SentenceCalculator.DISPLAY_DATE)+"\nCharge #1"));
        for(int width:new int[]{360,720}){
            Bitmap bitmap=draw(timeline,width);
            int y=slider.getTop()+slider.getHeight()/2;
            LocalDate delayMiddle=first.active().maximum.plusDays(22);
            int x=Math.round(slider.getTrackSidePadding()+range.positionOf(delayMiddle)*slider.getTrackWidth());
            assertEquals(ContextCompat.getColor(context,R.color.laya_danger),bitmap.getPixel(x,y));
            int after=Math.round(slider.getTrackSidePadding()+range.positionOf(boundary.plusDays(4))*slider.getTrackWidth());
            assertNotEquals(ContextCompat.getColor(context,R.color.laya_danger),bitmap.getPixel(after,y));
            save(bitmap,"late-custom-"+width+".png");bitmap.recycle();
        }
        // Undo and rebind on the same view must remove both the old label and red interval.
        timeline.setMilestones(range,d->{});timeline.setChargeContext(first);
        timeline.setPreview(observation,SentenceProjection.releaseDates(range,observation,first.calculate(observation)));
        timeline.setOverdueStart(first.overdueStart(observation));
        Bitmap undone=draw(timeline,720);
        int x=Math.round(slider.getTrackSidePadding()+range.positionOf(first.active().maximum.plusDays(22))*slider.getTrackWidth());
        assertNotEquals(ContextCompat.getColor(context,R.color.laya_danger),undone.getPixel(x,slider.getTop()+slider.getHeight()/2));
        assertEquals(0,count(timeline,"Custom end date\n"+boundary.format(SentenceCalculator.DISPLAY_DATE)+"\nCharge #1"));
        undone.recycle();
    }
    private int count(TimelineMilestonesLayout view,String text){
        int count=0;for(int i=0;i<view.getChildCount();i++){
            View child=view.getChildAt(i);if(child instanceof TextView&&text.contentEquals(((TextView)child).getText()))count++;
        }return count;
    }
    private Bitmap draw(View view,int width){
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        view.layout(0,0,width,view.getMeasuredHeight());
        Bitmap bitmap=Bitmap.createBitmap(width,view.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);view.draw(canvas);return bitmap;
    }
    private void save(Bitmap bitmap,String name)throws Exception{
        String folder=System.getProperty("laya.test.screenshots");if(folder==null)return;
        java.io.File file=new java.io.File(folder,name);file.getParentFile().mkdirs();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    }
}
