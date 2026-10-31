package com.example.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.HorizontalScrollView;

/** A straight native slider; zoom changes its width, not its date mapping. */
public final class ZoomTimelineScrollView extends HorizontalScrollView {
    private final ScaleGestureDetector detector;
    private float zoom=1f;
    private boolean initializedZoom;
    private int days=1;
    private boolean sliderGesture,pinching;
    private Runnable onZoom;
    private com.google.android.material.slider.Slider gestureSlider;
    private float gestureValue;
    private float dragX,dragY;
    private long dragStarted;
    private boolean draggingTrack;
    private final Runnable followHandle=new Runnable(){
        @Override public void run(){
            if(!draggingTrack||pinching||gestureSlider==null)return;
            float zone=dp(64),edge=dragX<zone?dragX-zone:dragX>getWidth()-zone?dragX-(getWidth()-zone):0;
            if(edge!=0){
                int delta=Math.round(Math.max(-1,Math.min(1,edge/zone))*dp(8));
                int before=getScrollX();scrollBy(delta,0);
                if(getScrollX()!=before){
                    // Re-send the stationary finger at its viewport position after panning.
                    // The native slider then maps the newly revealed part of the track to a date.
                    MotionEvent move=MotionEvent.obtain(dragStarted,android.os.SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,safeDragX(),dragY,0);
                    ZoomTimelineScrollView.super.dispatchTouchEvent(move);move.recycle();
                }
            }
            postOnAnimation(this);
        }
    };
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private float safeDragX(){
        float left=getScrollX()>0?dp(24):0;
        float right=getScrollX()<contentWidth()-getWidth()?getWidth()-dp(24):getWidth();
        return Math.max(left,Math.min(right,dragX));
    }
    private void stopFollowing(){draggingTrack=false;removeCallbacks(followHandle);}

    public ZoomTimelineScrollView(Context context,AttributeSet attrs){
        super(context,attrs);setFillViewport(true);setHorizontalScrollBarEnabled(true);
        detector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){return true;}
            @Override public boolean onScale(ScaleGestureDetector d){
                float anchor=(getScrollX()+d.getFocusX())/Math.max(1f,contentWidth());
                resize(zoom*d.getScaleFactor(),anchor,d.getFocusX());return true;
            }
        });
    }
    public void configure(int days,Runnable listener){this.days=Math.max(1,days);onZoom=listener;post(()->{if(!initializedZoom){zoom=maximumZoom();initializedZoom=true;}resize(zoom,0,0);});}
    public float getZoom(){return zoom;}
    private int contentWidth(){return getChildCount()==0?getWidth():getChildAt(0).getWidth();}
    private float maximumZoom(){return Math.max(1,(days*4f*getResources().getDisplayMetrics().density+trackPadding())/Math.max(1,getWidth()));}
    private int trackPadding(){
        View child=getChildCount()==0?null:getChildAt(0);
        if(child instanceof android.view.ViewGroup){View slider=((android.view.ViewGroup)child).getChildAt(0);
            if(slider instanceof com.google.android.material.slider.Slider)return 2*((com.google.android.material.slider.Slider)slider).getTrackSidePadding();}
        return 0;
    }
    public void zoomBy(float factor,float selectedFraction){resize(zoom*factor,selectedFraction,getWidth()/2f);}
    public void focusDate(float fraction,int visibleDays){resize(Math.max(zoom,days/(float)Math.max(1,visibleDays)),fraction,getWidth()/2f);}
    public void restoreZoom(float value){zoom=Math.max(1,value);initializedZoom=true;}
    private void resize(float requested,float anchor,float focus){
        if(getChildCount()==0||getWidth()==0)return;
        zoom=Math.max(1,Math.min(maximumZoom(),requested));
        View child=getChildAt(0);int width=Math.max(getWidth(),Math.round(getWidth()*zoom));
        android.view.ViewGroup.LayoutParams lp=child.getLayoutParams();lp.width=width;child.setLayoutParams(lp);
        int scroll=Math.max(0,Math.min(width-getWidth(),Math.round(anchor*width-focus)));
        post(()->scrollTo(scroll,0));if(onZoom!=null)onZoom.run();
    }
    public void reveal(float fraction){post(()->post(()->{int x=Math.round(fraction*contentWidth());if(x<getScrollX()+32||x>getScrollX()+getWidth()-32)smoothScrollTo(Math.max(0,x-getWidth()/2),0);}));}
    @Override protected void measureChild(View child,int widthSpec,int heightSpec){measureTimeline(child,widthSpec,heightSpec);}
    @Override protected void measureChildWithMargins(View child,int widthSpec,int widthUsed,int heightSpec,int heightUsed){measureTimeline(child,widthSpec,heightSpec);}
    private void measureTimeline(View child,int widthSpec,int heightSpec){
        int width=Math.max(1,Math.round((MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight())*zoom));
        child.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),getChildMeasureSpec(heightSpec,getPaddingTop()+getPaddingBottom(),child.getLayoutParams().height));
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw)post(()->resize(zoom,.5f,w/2f));}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            stopFollowing();dragStarted=e.getDownTime();
            pinching=false;
            View timeline=getChildCount()>0?getChildAt(0):null;
            View slider=timeline instanceof android.view.ViewGroup?((android.view.ViewGroup)timeline).getChildAt(0):null;
            gestureSlider=slider instanceof com.google.android.material.slider.Slider?(com.google.android.material.slider.Slider)slider:null;
            if(gestureSlider!=null)gestureValue=gestureSlider.getValue();
            sliderGesture=slider!=null&&e.getY()>=slider.getTop()&&e.getY()<=slider.getBottom();
        }
        dragX=e.getX();dragY=e.getY();
        if(e.getPointerCount()>1){
            stopFollowing();
            if(!pinching&&gestureSlider!=null)gestureSlider.setValue(gestureValue);
            super.requestDisallowInterceptTouchEvent(false);pinching=true;getParent().requestDisallowInterceptTouchEvent(true);}
        detector.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&sliderGesture&&!pinching&&!draggingTrack){
            draggingTrack=true;postOnAnimation(followHandle);
        }
        MotionEvent routed=e;
        if(sliderGesture&&!pinching){routed=MotionEvent.obtain(e);routed.offsetLocation(safeDragX()-e.getX(),0);}
        boolean handled=super.dispatchTouchEvent(routed);
        if(routed!=e)routed.recycle();
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){stopFollowing();pinching=false;sliderGesture=false;getParent().requestDisallowInterceptTouchEvent(false);}
        return handled;
    }
    @Override protected void onDetachedFromWindow(){stopFollowing();super.onDetachedFromWindow();}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){if(pinching)return true;if(sliderGesture)return false;return super.onInterceptTouchEvent(e);}
    @Override public boolean onTouchEvent(MotionEvent e){return pinching||super.onTouchEvent(e);}
}
