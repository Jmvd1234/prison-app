package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Animatable;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import java.io.InputStream;

/** Bundled offline GIF; framework animation on modern Android, Movie on Android 7–8. */
@SuppressWarnings("deprecation")
public class TimelineDemoView extends View {
    private Drawable animation;
    private Movie movie;
    private boolean playing;
    private long started;
    public TimelineDemoView(Context context,AttributeSet attrs) {
        super(context,attrs);
        if(Build.VERSION.SDK_INT>=28){
            try {
                animation=android.graphics.ImageDecoder.decodeDrawable(
                        android.graphics.ImageDecoder.createSource(getResources(),R.raw.timeline_demo));
                animation.setCallback(this);
            } catch(java.io.IOException ignored) { }
        }
        if(animation==null){
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            try(InputStream stream=getResources().openRawResource(R.raw.timeline_demo)) {
                movie=Movie.decodeStream(stream);
            } catch(java.io.IOException ignored) { }
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void play(){
        playing=true;started=SystemClock.uptimeMillis();
        if(animation instanceof Animatable)((Animatable)animation).start();
        invalidate();
    }
    public void pause(){
        playing=false;
        if(animation instanceof Animatable)((Animatable)animation).stop();
    }
    @Override protected boolean verifyDrawable(Drawable drawable){return drawable==animation||super.verifyDrawable(drawable);}
    @Override protected void onDetachedFromWindow(){pause();super.onDetachedFromWindow();}
    @Override protected void onMeasure(int w,int h) {
        int width=MeasureSpec.getSize(w);
        float ratio=animation!=null?animation.getIntrinsicHeight()/(float)animation.getIntrinsicWidth():movie!=null?movie.height()/(float)movie.width():228f/320;
        setMeasuredDimension(resolveSize(width,w),resolveSize(Math.round(width*ratio),h));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(animation!=null){animation.setBounds(0,0,getWidth(),getHeight());animation.draw(canvas);return;}
        if(movie==null)return;
        int time=playing?(int)((SystemClock.uptimeMillis()-started)%Math.max(1,movie.duration())):0;
        movie.setTime(time);
        float scale=getWidth()/(float)movie.width();
        canvas.save();canvas.scale(scale,scale);movie.draw(canvas,0,0);canvas.restore();
        if(playing&&isShown())postInvalidateDelayed(80);
    }
}
