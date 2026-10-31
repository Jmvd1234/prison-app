package com.example.myapplication;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.slider.Slider;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Non-draggable, fixed ten-calendar-year rows styled with the detail slider's native tracks. */
public final class WrappedTimelineOverview extends ViewGroup {
    private static final class Row {Slider slider;int start,end,capacity,center;Boolean current;int tint;}
    private static final class Bubble {TextView view;LocalDate date,target;int title,chargeNumber;String customTitle;boolean today;int day,row,left,top,lane,color;boolean reached;}
    private final List<Row> rows=new ArrayList<>();
    private final List<Bubble> bubbles=new ArrayList<>();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curve=new Path();
    private SentenceProjection.Range range;
    private Slider style;
    private Consumer<LocalDate> listener;
    private TextView touchedBubble;
    private int selected,tailDay,maximumDay,redDay,side;
    private Bubble capBubble;
    private SequentialSentencePlan sequence;
    public void setSequence(SequentialSentencePlan plan){
        sequence=plan;
        bubbles.get(1).chargeNumber=bubbles.get(2).chargeNumber=plan==null?1:plan.active().number;
        if(plan==null)return;
        for(Bubble b:bubbles)if(b.target!=null&&b.target.isBefore(plan.active().start)){
            b.view.setClickable(false);b.view.setFocusable(false);
        }
        for(int i=1;i<plan.stages.size();i++){
            SequentialSentencePlan.Stage next=plan.stages.get(i);
            addBubble(next.previousMaximum?R.color.service_previous_maximum:R.color.service_previous_minimum);
            Bubble b=bubbles.get(bubbles.size()-1);b.date=b.target=next.start;
            b.customTitle=getResources().getString(next.previousCustom?R.string.service_custom_end:next.previousMaximum?R.string.service_maximum:R.string.service_minimum);b.chargeNumber=i;
            b.view.setClickable(false);b.view.setFocusable(false);
        }
        addBubble(R.color.laya_label);Bubble cap=bubbles.get(bubbles.size()-1);
        capBubble=cap;cap.view.setVisibility(GONE);cap.date=cap.target=plan.capDate;cap.customTitle=getResources().getString(R.string.service_cap);
        cap.view.setClickable(false);cap.view.setFocusable(false);
    }

    public WrappedTimelineOverview(Context c,AttributeSet attrs){super(c,attrs);setWillNotDraw(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private int color(int id){return ContextCompat.getColor(getContext(),id);}
    private int day(LocalDate date){return Math.max(0,(int)ChronoUnit.DAYS.between(range.start,date));}
    public void configure(SentenceProjection.Range range,Slider style,Consumer<LocalDate> listener){
        this.listener=listener;
        this.range=range;this.style=style;sequence=null;capBubble=null;removeAllViews();rows.clear();bubbles.clear();
        addBubble(R.color.laya_label);addBubble(R.color.release_minimum_marker);addBubble(R.color.release_maximum_marker);
        for(SentenceProjection.Milestone milestone:range.milestones){
            int title;
            switch(milestone.kind){
                case LEA_ARREST:title=R.string.breakdown_marker_arrest;break;
                case JAIL_COMMITMENT:title=R.string.breakdown_marker_jail;break;
                case PRISON_COMMITMENT:title=R.string.breakdown_marker_prison;break;
                case TODAY:title=R.string.breakdown_today;break;
                default:continue;
            }
            addBubble(R.color.laya_label);Bubble b=bubbles.get(bubbles.size()-1);b.date=b.target=milestone.date;b.title=title;b.today=milestone.kind==SentenceProjection.Kind.TODAY;
        }
    }
    private void addBubble(int color){
        Bubble b=new Bubble();b.color=color;b.view=new TextView(getContext());b.view.setTextSize(10);b.view.setTextColor(color(color));b.view.setIncludeFontPadding(false);b.view.setPadding(dp(4),dp(2),dp(4),dp(2));
        GradientDrawable bg=new GradientDrawable();bg.setColor(color(R.color.laya_surface));bg.setCornerRadius(dp(5));bg.setStroke(dp(1),color(R.color.laya_border));b.view.setBackground(bg);
        b.view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);b.view.setFocusable(true);
        b.view.setOnClickListener(v->{if(isEnabled()&&listener!=null&&b.target!=null)listener.accept(b.today?LocalDate.now():b.target);});
        bubbles.add(b);addView(b.view);
    }
    private Row addRow(int index){
        Row row=new Row();Slider s=new Slider(getContext());row.slider=s;
        s.setSaveEnabled(false);s.setStepSize(1);s.setTickVisible(false);s.setFocusable(false);s.setClickable(false);s.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        s.setTrackHeight(style.getTrackHeight());s.setTrackInactiveTintList(style.getTrackInactiveTintList());s.setTrackCornerSize(style.getTrackCornerSize());s.setTrackInsideCornerSize(style.getTrackInsideCornerSize());s.setTrackStopIndicatorSize(0);
        s.setThumbWidth(style.getThumbWidth());s.setThumbHeight(style.getThumbHeight());s.setThumbStrokeWidth(style.getThumbStrokeWidth());s.setThumbStrokeColor(style.getThumbStrokeColor());
        s.setLayoutDirection(index%2==0?View.LAYOUT_DIRECTION_LTR:View.LAYOUT_DIRECTION_RTL);rows.add(row);addView(s,0);return row;
    }
    public void setPreview(LocalDate date,SentenceProjection.ReleaseDates estimates){
        if(range==null)return;
        selected=day(date);maximumDay=day(estimates.maximum);tailDay=Math.max(selected,Math.max(maximumDay,day(estimates.minimum)));
        redDay=maximumDay;
        if(sequence!=null){
            boolean relevant=sequence.limitRelevant(date);
            capBubble.view.setVisibility(relevant?VISIBLE:GONE);
            if(relevant)tailDay=Math.max(tailDay,day(sequence.capDate));
            LocalDate red=sequence.overdueStart(date);
            redDay=red==null?Integer.MAX_VALUE:day(red);
        }
        int count=0;
        for(int index=0;;index++){
            LocalDate begin=range.start.plusYears(index*10L),end=range.start.plusYears((index+1)*10L);int start=day(begin);
            if(start>=tailDay&&index>0)break;
            Row row=index<rows.size()?rows.get(index):addRow(index);row.start=start;row.end=Math.min(tailDay,day(end));row.capacity=(int)ChronoUnit.DAYS.between(begin,end);
            float valueTo=Math.max(1,row.end-row.start);if(row.slider.getValueTo()!=valueTo){row.slider.setValue(0);row.slider.setValueTo(valueTo);}
            row.slider.setValue(Math.max(0,Math.min(row.end-row.start,selected-row.start)));
            boolean current=selected>=row.start&&(selected<row.end||row.end==tailDay);
            if(row.current==null||row.current!=current){row.slider.setThumbTintList(current?style.getThumbTintList():ColorStateList.valueOf(Color.TRANSPARENT));row.slider.setThumbTrackGapSize(current?style.getThumbTrackGapSize():0);row.current=current;}
            int tint=row.start>=redDay&&selected>redDay?color(R.color.laya_danger):style.getTrackActiveTintList().getDefaultColor();if(row.tint!=tint){row.slider.setTrackActiveTintList(ColorStateList.valueOf(tint));row.tint=tint;}
            count++;
        }
        while(rows.size()>count)removeView(rows.remove(rows.size()-1).slider);
        updateBubble(bubbles.get(0),R.string.overview_observation,date,true);
        updateBubble(bubbles.get(1),sequence!=null&&!sequence.finalCharge()?R.string.service_minimum:R.string.overview_minimum,estimates.minimum,estimates.minimumReached);
        updateBubble(bubbles.get(2),sequence!=null&&!sequence.finalCharge()?R.string.service_maximum:R.string.overview_maximum,estimates.maximum,estimates.maximumReached);
        bubbles.get(0).target=date;bubbles.get(1).target=range.minimumCompletion;bubbles.get(2).target=range.maximumCompletion;
        for(int i=3;i<bubbles.size();i++){Bubble b=bubbles.get(i);updateBubble(b,b.title,b.date,true);}
        for(Bubble b:bubbles)b.view.setContentDescription(b.view.getText()+". "+(day(b.date)>tailDay?getResources().getString(R.string.overview_beyond_tail)+" ":"")+(b.view.isClickable()?getResources().getString(R.string.overview_jump_to,b.target.format(SentenceCalculator.DISPLAY_DATE)):""));
        setContentDescription(getResources().getString(R.string.overview_description,date.format(SentenceCalculator.DISPLAY_DATE),estimates.minimum.format(SentenceCalculator.DISPLAY_DATE),estimates.maximum.format(SentenceCalculator.DISPLAY_DATE)));
        requestLayout();invalidate();
    }
    private void updateBubble(Bubble b,int title,LocalDate date,boolean reached){b.date=date;b.day=Math.min(tailDay,day(date));b.reached=reached;b.row=rows.size()-1;for(int i=0;i<rows.size();i++)if(b.day<rows.get(i).end){b.row=i;break;}b.view.setText((b.customTitle!=null?b.customTitle:getResources().getString(title))+(day(date)>tailDay?" →":"")+"\n"+date.format(SentenceCalculator.DISPLAY_DATE)+(b.chargeNumber>0?"\n"+getResources().getString(R.string.service_number,b.chargeNumber):""));}
    private float xAt(Row row,int index,int day,int width){int padding=row.slider.getTrackSidePadding();float full=Math.max(1,width-2*side-2*padding);float distance=full*(day-row.start)/row.capacity;return index%2==0?side+padding+distance:width-side-padding-distance;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec);side=dp(16);int cursor=0;
        for(int index=0;index<rows.size();index++){
            Row row=rows.get(index);int padding=row.slider.getTrackSidePadding();int full=Math.max(1,width-2*side-2*padding);int track=Math.max(dp(1),Math.round(full*(row.end-row.start)/(float)row.capacity));
            row.slider.measure(MeasureSpec.makeMeasureSpec(track+2*padding,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
            List<Bubble> here=new ArrayList<>();for(Bubble b:bubbles)if(b.view.getVisibility()==VISIBLE&&b.row==index){b.view.measure(MeasureSpec.makeMeasureSpec(Math.min(dp(120),width-dp(8)),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));b.left=Math.max(dp(4),Math.min(width-b.view.getMeasuredWidth()-dp(4),Math.round(xAt(row,index,b.day,width)-b.view.getMeasuredWidth()/2f)));here.add(b);}
            here.sort(Comparator.comparingInt(b->b.left));List<Integer> rights=new ArrayList<>();int height=0;
            for(Bubble b:here){int lane=0;while(lane<rights.size()&&b.left<rights.get(lane)+dp(4))lane++;if(lane==rights.size())rights.add(0);rights.set(lane,b.left+b.view.getMeasuredWidth());b.lane=lane;height=Math.max(height,b.view.getMeasuredHeight());}
            int gap=style.getTrackHeight()/2+dp(6),above=Math.max(row.slider.getMeasuredHeight()/2,rights.isEmpty()?0:gap+((rights.size()+1)/2)*(height+dp(4))),below=Math.max(row.slider.getMeasuredHeight()/2,rights.size()<2?0:gap+(rights.size()/2)*(height+dp(4)));
            row.center=cursor+above;
            for(Bubble b:here)b.top=b.lane%2==0?row.center-gap-(b.lane/2)*(height+dp(4))-b.view.getMeasuredHeight():row.center+gap+(b.lane/2)*(height+dp(4));
            cursor=row.center+below+dp(8);
        }
        setMeasuredDimension(width,resolveSize(cursor,heightSpec));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        for(int i=0;i<rows.size();i++){Row row=rows.get(i);Slider s=row.slider;int left=i%2==0?side:getWidth()-side-s.getMeasuredWidth(),top=row.center-s.getMeasuredHeight()/2;s.layout(left,top,left+s.getMeasuredWidth(),top+s.getMeasuredHeight());}
        for(Bubble bubble:bubbles)bubble.view.layout(bubble.left,bubble.top,bubble.left+bubble.view.getMeasuredWidth(),bubble.top+bubble.view.getMeasuredHeight());
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(style==null)return;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(style.getTrackHeight());paint.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i+1<rows.size();i++){Row a=rows.get(i),b=rows.get(i+1);float x=xAt(a,i,a.end,getWidth()),outer=x+(i%2==0?dp(24):-dp(24));paint.setColor(selected>a.end&&a.end>=redDay?color(R.color.laya_danger):(selected>=a.end?style.getTrackActiveTintList():style.getTrackInactiveTintList()).getDefaultColor());curve.reset();curve.moveTo(x,a.center);curve.cubicTo(outer,a.center,outer,b.center,x,b.center);canvas.drawPath(curve,paint);}
    }
    @Override protected void dispatchDraw(Canvas canvas){
        super.dispatchDraw(canvas);if(range==null||rows.isEmpty())return;
        // Only the row straddling maximum release needs a partial red overlay.
        if(selected>redDay)for(int i=0;i<rows.size();i++){Row row=rows.get(i);if(redDay<row.start||redDay>=row.end)continue;float a=xAt(row,i,redDay,getWidth()),b=xAt(row,i,Math.min(selected,row.end),getWidth());if(selected<=row.end)b+=(i%2==0?-1:1)*(style.getThumbTrackGapSize()+style.getThumbWidth()/2f);if(i%2==0?b>a:b<a){paint.setStyle(Paint.Style.FILL);paint.setColor(color(R.color.laya_danger));float half=style.getTrackHeight()/2f,corner=Math.min(half,Math.max(0,style.getTrackCornerSize()));canvas.drawRoundRect(Math.min(a,b),row.center-half,Math.max(a,b),row.center+half,corner,corner,paint);}}
        if(sequence!=null)for(int stage=1;stage<sequence.stages.size();stage++){
            SequentialSentencePlan.Stage next=sequence.stages.get(stage),previous=sequence.stages.get(stage-1);
            int begin=day(previous.start),finish=day(next.start);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(style.getTrackHeight());paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setColor(color(next.previousMaximum?R.color.service_previous_maximum:R.color.service_previous_minimum));
            for(int i=0;i<rows.size();i++){Row row=rows.get(i);int a=Math.max(begin,row.start),b=Math.min(finish,row.end);if(b>a)canvas.drawLine(xAt(row,i,a,getWidth()),row.center,xAt(row,i,b,getWidth()),row.center,paint);}
        }
        for(int i=0;i<bubbles.size();i++){Bubble b=bubbles.get(i);if(b.view.getVisibility()!=VISIBLE)continue;Row row=rows.get(b.row);float x=xAt(row,b.row,b.day,getWidth()),y=row.center;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(color(b.color));canvas.drawLine(x,y,Math.max(b.left,Math.min(b.left+b.view.getWidth(),x)),b.top<y?b.top+b.view.getHeight():b.top,paint);
            if(i==0)continue;
            paint.setStyle(Paint.Style.FILL);paint.setColor(color(R.color.release_marker_halo));canvas.drawCircle(x,y,dp(i<=2?10:5),paint);paint.setColor(color(b.color));paint.setStyle(b.reached?Paint.Style.FILL:Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawCircle(x,y,dp(i<=2?8:3),paint);
        }
    }
    // Only bubbles receive taps; track gestures remain available to the parent scroll view.
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(!isEnabled())return false;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            touchedBubble=null;
            for(Bubble b:bubbles)if(b.view.getVisibility()==VISIBLE&&event.getX()>=b.view.getLeft()&&event.getX()<=b.view.getRight()&&event.getY()>=b.view.getTop()&&event.getY()<=b.view.getBottom()){touchedBubble=b.view;break;}
        }
        if(touchedBubble==null)return false;
        MotionEvent local=MotionEvent.obtain(event);local.offsetLocation(-touchedBubble.getLeft(),-touchedBubble.getTop());boolean handled=touchedBubble.dispatchTouchEvent(local);local.recycle();
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)touchedBubble=null;
        return handled;
    }
    @Override protected LayoutParams generateDefaultLayoutParams(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);}
}
