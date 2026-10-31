package com.example.myapplication;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class TimelineTutorialTest {
    Context context;
    @Before public void setup(){context=ApplicationProvider.getApplicationContext();context.getSharedPreferences("timeline_help",0).edit().clear().commit();}
    @Test public void closeDoesNotAcknowledgeAndGotItOnlyAcknowledgesItsProfile(){
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->{
                TimelineTutorialDialog d=TimelineTutorialDialog.create(91);d.showNow(a.getSupportFragmentManager(),TimelineTutorialDialog.TAG);
                d.requireDialog().findViewById(R.id.timelineTutorialClose).performClick();a.getSupportFragmentManager().executePendingTransactions();
                assertFalse(TimelineTutorialDialog.acknowledged(a,91));
                d=TimelineTutorialDialog.create(91);d.showNow(a.getSupportFragmentManager(),TimelineTutorialDialog.TAG);
                d.requireDialog().findViewById(R.id.timelineTutorialGotIt).performClick();a.getSupportFragmentManager().executePendingTransactions();
                assertTrue(TimelineTutorialDialog.acknowledged(a,91));
                assertFalse(TimelineTutorialDialog.acknowledged(a,92));
            });
        }
        assertTrue(TimelineTutorialDialog.acknowledged(context,91));
    }
    @Test public void rotationKeepsOneDialogAndDoesNotAcknowledge(){
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->TimelineTutorialDialog.create(91).showNow(a.getSupportFragmentManager(),TimelineTutorialDialog.TAG));
            scenario.recreate();
            scenario.onActivity(a->{
                assertNotNull(a.getSupportFragmentManager().findFragmentByTag(TimelineTutorialDialog.TAG));
                assertEquals(1,a.getSupportFragmentManager().getFragments().size());
                assertFalse(TimelineTutorialDialog.acknowledged(a,91));
            });
        }
    }
    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void bundledGifDecodesAndAnimates() throws Exception {
        android.graphics.drawable.Drawable image=android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(context.getResources(),R.raw.timeline_demo));
        assertTrue(image instanceof android.graphics.drawable.AnimatedImageDrawable);
        assertTrue(image.getIntrinsicWidth()>0);
        ((android.graphics.drawable.AnimatedImageDrawable)image).start();
        assertTrue(((android.graphics.drawable.AnimatedImageDrawable)image).isRunning());
        ((android.graphics.drawable.AnimatedImageDrawable)image).stop();
    }
    @Test public void capturePopupWhenRequested() throws Exception {
        String folder=System.getProperty("laya.test.screenshots");Assume.assumeTrue(folder!=null);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->{
                TimelineTutorialDialog d=TimelineTutorialDialog.create(91);d.showNow(a.getSupportFragmentManager(),TimelineTutorialDialog.TAG);
                android.view.View content=d.requireDialog().findViewById(R.id.timelineTutorialGotIt);
                while(!(content instanceof android.widget.ScrollView))content=(android.view.View)content.getParent();
                content.measure(android.view.View.MeasureSpec.makeMeasureSpec(320,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(700,android.view.View.MeasureSpec.AT_MOST));
                content.layout(0,0,320,content.getMeasuredHeight());
                android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(320,content.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas=new android.graphics.Canvas(b);canvas.drawColor(android.graphics.Color.WHITE);content.draw(canvas);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"timeline-popup.png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException e){throw new RuntimeException(e);}b.recycle();
            });
        }
    }
    @Test @Config(qualifiers="land-mdpi") public void landscapePopupCanScrollToAcknowledgement(){
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            scenario.onActivity(a->{
                TimelineTutorialDialog d=TimelineTutorialDialog.create(91);d.showNow(a.getSupportFragmentManager(),TimelineTutorialDialog.TAG);
                assertNotNull(d.requireDialog().findViewById(R.id.timelineTutorialGotIt));
                android.view.View content=d.requireDialog().findViewById(R.id.timelineTutorialGotIt);
                while(!(content instanceof android.widget.ScrollView)&&content.getParent() instanceof android.view.View)content=(android.view.View)content.getParent();
                assertTrue(content instanceof android.widget.ScrollView);
                content.measure(android.view.View.MeasureSpec.makeMeasureSpec(480,android.view.View.MeasureSpec.EXACTLY),android.view.View.MeasureSpec.makeMeasureSpec(240,android.view.View.MeasureSpec.EXACTLY));
                content.layout(0,0,480,240);
                assertTrue(content.canScrollVertically(1));
                ((android.widget.ScrollView)content).scrollTo(0,10000);
                android.view.View button=d.requireDialog().findViewById(R.id.timelineTutorialGotIt);
                android.graphics.Rect rect=new android.graphics.Rect(0,0,button.getWidth(),button.getHeight());
                ((android.view.ViewGroup)content).offsetDescendantRectToMyCoords(button,rect);
                rect.offset(0,-content.getScrollY());
                assertTrue(rect.bottom<=content.getHeight());
                assertTrue(rect.top>=0);
            });
        }
    }
}
