package com.example.myapplication;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import com.example.myapplication.db.Profile;
import com.google.android.material.slider.Slider;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import java.io.*;
import java.time.LocalDate;

/** Optional deterministic capture of the production timeline, without personal data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TimelineDemoCaptureTest {
    @Test public void recordTimelineFrames() {
        String folder=System.getProperty("laya.test.screenshots");
        Assume.assumeTrue(folder!=null);
        File output=new File(folder,"timeline-demo");output.mkdirs();
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(a->{
                View layout=a.getLayoutInflater().inflate(R.layout.activity_breakdown,null);
                ZoomTimelineScrollView scroll=layout.findViewById(R.id.timelineZoomScroll);
                ((ViewGroup)scroll.getParent()).removeView(scroll);
                LinearLayout demo=new LinearLayout(a);demo.setOrientation(LinearLayout.VERTICAL);
                demo.setBackgroundColor(Color.WHITE);demo.setPadding(12,8,12,8);
                TextView caption=new TextView(a);caption.setTextSize(15);caption.setTextColor(Color.rgb(0,126,175));
                demo.addView(caption,new LinearLayout.LayoutParams(-1,32));
                demo.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
                a.setContentView(demo);
                Profile p=new Profile();p.leaArrestDate="2024-01-01";p.commitmentDate="2024-01-15";
                p.minYears=1;p.maxYears=2;
                LocalDate today=LocalDate.of(2024,7,1);
                SentenceProjection.Range range=SentenceProjection.create(p,today);
                TimelineMilestonesLayout timeline=scroll.findViewById(R.id.breakdownTimeline);
                Slider slider=scroll.findViewById(R.id.breakdownDateSlider);
                slider.setValueTo(range.dates.size()-1);slider.setStepSize(1);
                timeline.setMilestones(range,d->{});
                scroll.restoreZoom(1.5f);
                for(int frame=0;frame<100;frame++) {
                    double movement=Math.max(0,Math.min(1,(frame-10)/35.0));
                    LocalDate date=range.start.plusDays(30+Math.round(240*movement));
                    slider.setValue(range.dates.indexOf(date));
                    timeline.setPreview(date,SentenceProjection.releaseDates(range,date,
                            SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p,date))));
                    caption.setText("Observation Date: "+date.format(SentenceCalculator.DISPLAY_DATE));
                    int width=320;
                    for(int pass=0;pass<2;pass++) {
                        demo.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.AT_MOST));
                        demo.layout(0,0,width,demo.getMeasuredHeight());
                    }
                    float pan=frame<55?0:frame<75?(frame-55)/20f:frame<95?(95-frame)/20f:0;
                    scroll.scrollTo(Math.round(pan*(timeline.getWidth()-scroll.getWidth())),0);
                    Bitmap b=Bitmap.createBitmap(width,demo.getHeight()+32,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(b);canvas.drawColor(Color.WHITE);demo.draw(canvas);
                    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTextSize(12);paint.setColor(Color.rgb(0,126,175));
                    canvas.drawText(frame<55?"Drag the blue marker":"Swipe the labels to see more dates",12,b.getHeight()-10,paint);
                    if(frame>=10&&frame<46) {
                        float x=12+slider.getTrackSidePadding()+range.positionOf(date)*(slider.getWidth()-2*slider.getTrackSidePadding())-scroll.getScrollX();
                        float y=scroll.getTop()+slider.getTop()+slider.getHeight()/2f;
                        paint.setColor(0x44009acb);canvas.drawCircle(x,y,19,paint);
                        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xff007eaf);canvas.drawCircle(x,y,19,paint);
                    } else if(frame>=55&&frame<95) {
                        float x=width-65-pan*130,y=scroll.getTop()+18;
                        paint.setColor(0x44009acb);canvas.drawCircle(x,y,16,paint);
                        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0xff007eaf);canvas.drawCircle(x,y,16,paint);
                    }
                    try(FileOutputStream stream=new FileOutputStream(new File(output,String.format("%03d.png",frame)))) {
                        b.compress(Bitmap.CompressFormat.PNG,100,stream);
                    } catch(IOException e) {throw new RuntimeException(e);}
                    b.recycle();
                }
            });
        }
    }
}
