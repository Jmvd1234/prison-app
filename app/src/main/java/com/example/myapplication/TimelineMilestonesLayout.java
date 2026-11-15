package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.slider.Slider;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed custody/zero markers, with release labels that move as earned credits change. */
public final class TimelineMilestonesLayout extends ViewGroup {
    public interface OnDateClick { void select(LocalDate date); }

    private static final class Label {
        TextView view;
        LocalDate date, displayedDate;
        boolean minimum, maximum, reached, today;
        float fraction, labelFraction, x, labelAnchorX;
        int left, top, lane, customColor;

        boolean isZero() { return minimum || maximum; }
    }

    private final List<Label> labels = new ArrayList<>();
    private final List<Label> historyLabels = new ArrayList<>();
    private List<SequentialSentencePlan.Stage> chargeStages = Collections.emptyList();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path connector = new Path();
    private final int neutral, surface, minimumColor, maximumColor, halo;
    private LocalDate selectedDate;
    private SentenceProjection.Range range;
    private Label minimumLabel, maximumLabel;
    private boolean intermediateCharge;
    private int chargeNumber=1;
    private LocalDate overdueStart;
    private Label limitLabel;
    private int sliderTop;
    public void setOverdueStart(LocalDate date){overdueStart=date;invalidate();}
    public void setChargeContext(SequentialSentencePlan plan){
        chargeNumber=plan==null?1:plan.active().number;
        for(Label label:historyLabels){labels.remove(label);removeView(label.view);}
        historyLabels.clear();
        if(limitLabel!=null){labels.remove(limitLabel);removeView(limitLabel.view);}
        chargeStages=plan==null?Collections.emptyList():plan.stages;
        limitLabel=null;
        if(plan==null){requestLayout();invalidate();return;}
        limitLabel=new Label();limitLabel.date=limitLabel.displayedDate=plan.capDate;
        limitLabel.fraction=limitLabel.labelFraction=range.positionOf(plan.capDate);
        limitLabel.customColor=neutral;
        limitLabel.view=(TextView)LayoutInflater.from(getContext()).inflate(R.layout.view_timeline_milestone,this,false);
        limitLabel.view.setText(plan.limitReachedText()+"\n"+plan.capDate.format(SentenceCalculator.DISPLAY_DATE));
        limitLabel.view.setTextColor(neutral);
        limitLabel.view.setClickable(false);limitLabel.view.setFocusable(false);
        for(int i=1;i<plan.stages.size();i++){
            SequentialSentencePlan.Stage next=plan.stages.get(i);
            SequentialSentencePlan.Stage previous=plan.stages.get(i-1);
            boolean delayed=next.previousCustom&&next.start.isAfter(previous.maximum);
            // Keep the calculated maximum visible alongside a later custom handoff.
            if(delayed)addHistoryLabel(previous.maximum,R.string.service_maximum,i,R.color.service_previous_maximum);
            addHistoryLabel(next.start,next.previousCustom?R.string.service_custom_end:next.previousMaximum?R.string.service_maximum:R.string.service_minimum,
                    i,next.previousMaximum||delayed?R.color.service_previous_maximum:R.color.service_previous_minimum);
        }
        requestLayout();invalidate();
    }
    private void addHistoryLabel(LocalDate date,int title,int number,int color){
        Label label=new Label();label.date=label.displayedDate=date;label.reached=true;
        label.fraction=label.labelFraction=range.positionOf(date);
        label.customColor=ContextCompat.getColor(getContext(),color);
        label.view=(TextView)LayoutInflater.from(getContext()).inflate(R.layout.view_timeline_milestone,this,false);
        label.view.setText(getContext().getString(title)+"\n"+date.format(SentenceCalculator.DISPLAY_DATE)+"\n"+getContext().getString(R.string.service_number,number));
        label.view.setTextColor(label.customColor);label.view.setClickable(false);label.view.setFocusable(false);
        labels.add(label);historyLabels.add(label);addView(label.view);
    }
    public void setLimitRelevant(boolean visible){
        if(limitLabel==null||visible==labels.contains(limitLabel))return;
        if(visible){labels.add(limitLabel);addView(limitLabel.view);}
        else{labels.remove(limitLabel);removeView(limitLabel.view);}
        requestLayout();invalidate();
    }
    public void setIntermediateCharge(boolean value){intermediateCharge=value;}


    public TimelineMilestonesLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        neutral = ContextCompat.getColor(context, R.color.laya_label);
        surface = ContextCompat.getColor(context, R.color.laya_surface);
        minimumColor = ContextCompat.getColor(context, R.color.release_minimum_marker);
        maximumColor = ContextCompat.getColor(context, R.color.release_maximum_marker);
        halo = ContextCompat.getColor(context, R.color.release_marker_halo);
    }

    public void setMilestones(SentenceProjection.Range range, OnDateClick listener) {
        this.range = range;
        for (Label label : labels) removeView(label.view);
        labels.clear();
        historyLabels.clear();chargeStages=Collections.emptyList();limitLabel=null;
        minimumLabel = maximumLabel = null;
        List<List<SentenceProjection.Milestone>> groups = new ArrayList<>();
        // Keep coincident custody dates in separate bubbles as well.
        for (SentenceProjection.Milestone point : range.milestones) {
            boolean duplicate=false;
            for(List<SentenceProjection.Milestone> group:groups)
                if(group.get(0).kind==point.kind&&group.get(0).date.equals(point.date))duplicate=true;
            if(!duplicate)groups.add(Collections.singletonList(point));
        }
        for (List<SentenceProjection.Milestone> group : groups) {
            Label label = new Label();
            label.date = group.get(0).date;
            label.displayedDate = label.date;
            SpannableStringBuilder title = new SpannableStringBuilder();
            for (SentenceProjection.Milestone point : group) {
                if (title.length() > 0) title.append(" / ");
                int start = title.length();
                title.append(getContext().getString(titleResource(point.kind)));
                boolean minimum = point.kind == SentenceProjection.Kind.MINIMUM_ZERO;
                boolean maximum = point.kind == SentenceProjection.Kind.MAXIMUM_ZERO;
                label.today |= point.kind == SentenceProjection.Kind.TODAY;
                label.minimum |= minimum;
                label.maximum |= maximum;
                if(point.kind==SentenceProjection.Kind.CLEMENCY_REVIEW)
                    label.customColor=ContextCompat.getColor(getContext(),R.color.clemency_marker);
                title.setSpan(new ForegroundColorSpan(markerColor(label)),
                        start, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if(label.date.isBefore(range.start))title.append(" ←");
            title.append('\n').append(label.date.format(SentenceCalculator.DISPLAY_DATE));
            label.fraction = label.labelFraction = range.positionOf(label.date);
            label.view = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.view_timeline_milestone, this, false);
            label.view.setText(title);
            label.view.setTextColor(markerColor(label));
            label.view.setTag(label.date.toString());
            label.view.setOnClickListener(view -> listener.select(label.today ? LocalDate.now() : label.date));
            if(label.date.isBefore(range.start)){label.view.setClickable(false);label.view.setFocusable(false);}
            if(group.get(0).kind==SentenceProjection.Kind.CLEMENCY_REVIEW)label.view.setId(R.id.timelineClemencyLabel);
            if (label.minimum) {
                minimumLabel = label;
                label.view.setId(R.id.timelineMinimumReleaseLabel);
            } else if (label.maximum) {
                maximumLabel = label;
                label.view.setId(R.id.timelineMaximumReleaseLabel);
            } else if (label.today) {
                label.view.setId(R.id.timelineTodayLabel);
            }
            addView(label.view);
            labels.add(label);
        }
        requestLayout();
        invalidate();
    }

    public void setPreview(LocalDate date, SentenceProjection.ReleaseDates estimates) {
        selectedDate = date;
        for (Label label : labels) label.view.setSelected(label.date.equals(date));
        updateReleaseLabel(minimumLabel, estimates.minimum, estimates.minimumReached);
        updateReleaseLabel(maximumLabel, estimates.maximum, estimates.maximumReached);
        invalidate();
    }

    private void updateReleaseLabel(Label label, LocalDate estimate, boolean reached) {
        if (label == null || range == null) return;
        label.view.setTextColor(markerColor(label));
        if (label.reached != reached) {
            label.reached = reached;
            requestLayout();
        }
        int titleId = label.minimum ? R.string.overview_minimum : R.string.overview_maximum;
        String title = getContext().getString(intermediateCharge ? (label.minimum ? R.string.service_minimum : R.string.service_maximum) : titleId);
        String formattedDate = estimate.format(SentenceCalculator.DISPLAY_DATE);
        if (estimate.isAfter(range.end)) {
            formattedDate = getContext().getString(R.string.breakdown_marker_beyond_range, formattedDate);
        }
        SpannableStringBuilder text = new SpannableStringBuilder(title);
        text.setSpan(new ForegroundColorSpan(markerColor(label)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append('\n').append(formattedDate).append('\n').append(getContext().getString(R.string.service_number,chargeNumber));
        if (!text.toString().contentEquals(label.view.getText())) label.view.setText(text);
        label.view.setContentDescription(getContext().getString(R.string.breakdown_release_marker_action,
                title, formattedDate, label.date.format(SentenceCalculator.DISPLAY_DATE)));
        float fraction = range.positionOf(estimate);
        if (fraction != label.labelFraction) {
            label.labelFraction = fraction;
            requestLayout();
        }
        label.displayedDate = estimate;
    }

    private int markerColor(Label label) {
        if(label.customColor!=0)return label.customColor;
        return label.maximum ? maximumColor : label.minimum ? minimumColor : neutral;
    }

    private int titleResource(SentenceProjection.Kind kind) {
        switch (kind) {
            case LEA_ARREST: return R.string.breakdown_marker_arrest;
            case JAIL_COMMITMENT: return R.string.breakdown_marker_jail;
            case PRISON_COMMITMENT: return R.string.breakdown_marker_prison;
            case TODAY: return R.string.breakdown_today;
            case CLEMENCY_REVIEW: return R.string.clemency_marker;
            case MINIMUM_ZERO: return R.string.breakdown_marker_minimum;
            default: return R.string.breakdown_marker_maximum;
        }
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        Slider slider = (Slider) getChildAt(0);
        slider.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int side = slider.getTrackSidePadding();
        for (Label label : labels) {
            float fraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 - label.fraction : label.fraction;
            label.x = side + fraction * (width - 2 * side);
            float labelFraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 - label.labelFraction : label.labelFraction;
            label.labelAnchorX = side + labelFraction * (width - 2 * side);
            label.view.measure(MeasureSpec.makeMeasureSpec(Math.min(dp(120), width), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            label.left = Math.max(0, Math.min(width - label.view.getMeasuredWidth(),
                    Math.round(label.labelAnchorX - label.view.getMeasuredWidth() / 2f)));
        }
        // Nearby markers retain their exact date positions on the same track.
        List<Label> positions = new ArrayList<>(labels);
        positions.sort(Comparator.comparingDouble(label -> label.labelAnchorX));
        // Lanes alternate above/below; both sides always reserve one full label.
        List<Integer> laneRight = new ArrayList<>(), laneHeight = new ArrayList<>();
        int reserved=dp(40);
        for(Label label:labels)reserved=Math.max(reserved,label.view.getMeasuredHeight());
        laneRight.add(-dp(8));laneRight.add(-dp(8));
        laneHeight.add(reserved);laneHeight.add(reserved);
        for (Label label : positions) {
            int lane = 0;
            while (lane < laneRight.size() && label.left < laneRight.get(lane) + dp(8)) lane++;
            if (lane == laneRight.size()) {laneRight.add(-dp(8));laneHeight.add(reserved);}
            label.lane = lane;
            laneRight.set(lane, label.left + label.view.getMeasuredWidth());
        }
        int above=dp(6),below=dp(6);
        for(int lane=0;lane<laneHeight.size();lane++){
            if(lane%2==0)above+=laneHeight.get(lane)+dp(4);
            else below+=laneHeight.get(lane)+dp(4);
        }
        sliderTop=above;
        int upper=above-dp(6),lower=above+slider.getMeasuredHeight()+dp(6);
        for(int lane=0;lane<laneHeight.size();lane++){
            for(Label label:labels)if(label.lane==lane)
                label.top=lane%2==0?upper-label.view.getMeasuredHeight():lower;
            if(lane%2==0)upper-=laneHeight.get(lane)+dp(4);else lower+=laneHeight.get(lane)+dp(4);
        }
        int height=above+slider.getMeasuredHeight()+below;
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View slider = getChildAt(0);
        slider.layout(0, sliderTop, right - left, sliderTop+slider.getMeasuredHeight());
        for (Label label : labels) label.view.layout(label.left, label.top,
                label.left + label.view.getMeasuredWidth(), label.top + label.view.getMeasuredHeight());
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        Slider slider = (Slider) getChildAt(0);
        float trackY = slider.getTop()+slider.getHeight() / 2f;
        paint.setStrokeWidth(dp(1));
        paint.setStyle(Paint.Style.STROKE);
        canvas.save();
        // Long leaders must not run through another milestone's text when dates cluster.
        // DIFFERENCE clipping also supports this app's API 24 minimum.
        for (Label label : labels) canvas.clipRect(label.view.getLeft() - dp(2),
                label.view.getTop(), label.view.getRight() + dp(2), label.view.getBottom(),
                Region.Op.DIFFERENCE);
        for (Label label : labels) {
            paint.setColor(markerColor(label));
            float labelX = label.left + label.view.getWidth() / 2f;
            connector.reset();
            connector.moveTo(label.labelAnchorX, trackY);
            boolean above=label.lane%2==0;
            connector.lineTo(label.labelAnchorX, above?slider.getTop():slider.getBottom());
            connector.lineTo(labelX, above?label.top+label.view.getHeight():label.top);
            canvas.drawPath(connector, paint);
        }
        canvas.restore();
        super.dispatchDraw(canvas);
        for(int i=1;i<chargeStages.size();i++){
            SequentialSentencePlan.Stage next=chargeStages.get(i),previous=chargeStages.get(i-1);
            if(next.previousCustom&&next.start.isAfter(previous.maximum))
                drawOverdueSegment(canvas,slider,trackY,previous.maximum,next.start);
        }
        if(overdueStart!=null)drawOverdueSegment(canvas,slider,trackY,overdueStart,selectedDate);
        paint.setStyle(Paint.Style.FILL);
        for (Label label : labels) {
            if (!label.isZero()) {
                paint.setColor(surface);
                canvas.drawCircle(label.x, trackY, dp(5), paint);
                paint.setColor(markerColor(label));
                canvas.drawCircle(label.x, trackY, dp(3), paint);
                continue;
            }
            if (!label.reached) continue;
            boolean sharedZero = minimumLabel != null && maximumLabel != null
                    && minimumLabel.reached && maximumLabel.reached
                    && minimumLabel.date.equals(maximumLabel.date);
            if (sharedZero && label.maximum) continue;
            float y = trackY;
            int radius = dp(label.date.equals(selectedDate) ? 10 : 8);
            paint.setColor(markerColor(label));
            canvas.drawLine(label.x, trackY, label.x, y, paint);
            paint.setColor(halo);
            canvas.drawCircle(label.x, y, radius + dp(2), paint);
            paint.setColor(markerColor(label));
            canvas.drawCircle(label.x, y, radius, paint);
            if (sharedZero) {
                paint.setColor(maximumColor);
                canvas.drawCircle(label.x, y, radius, paint);
                paint.setColor(minimumColor);
                canvas.drawArc(label.x - radius, y - radius, label.x + radius, y + radius,
                        90, 180, true, paint);
            }
            if (label.date.equals(selectedDate)) {
                paint.setColor(halo);
                canvas.drawCircle(label.x, y, dp(3), paint);
            }
        }
        // Estimates stay hollow until the selected observation reaches their zero date,
        // even when an estimate already equals that date (e.g. without allowances).
        for (Label label : labels) {
            if (!label.isZero() || label.reached) continue;
            boolean sharedEstimate = minimumLabel != null && maximumLabel != null
                    && !minimumLabel.reached && !maximumLabel.reached
                    && minimumLabel.displayedDate.equals(maximumLabel.displayedDate);
            if (sharedEstimate && label.maximum) continue;
            float x = label.labelAnchorX;
            float y = trackY - dp(14);
            int radius = dp(5);
            paint.setStrokeWidth(dp(1));
            paint.setColor(markerColor(label));
            canvas.drawLine(x, trackY, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(halo);
            canvas.drawCircle(x, y, radius + dp(1), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(sharedEstimate ? maximumColor : markerColor(label));
            canvas.drawCircle(x, y, radius, paint);
            if (sharedEstimate) {
                paint.setColor(minimumColor);
                canvas.drawArc(x - radius, y - radius, x + radius, y + radius,
                        90, 180, false, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawOverdueSegment(Canvas canvas,Slider slider,float trackY,LocalDate from,LocalDate to){
        if(range==null||selectedDate==null||to==null)return;
        LocalDate end=selectedDate.isBefore(to)?selectedDate:to;
        if(!end.isAfter(from))return;
        boolean rtl=getLayoutDirection()==LAYOUT_DIRECTION_RTL;
        float a=range.positionOf(from),b=range.positionOf(end);
        a=slider.getTrackSidePadding()+(rtl?1-a:a)*slider.getTrackWidth();
        b=slider.getTrackSidePadding()+(rtl?1-b:b)*slider.getTrackWidth();
        float direction=rtl?-1:1;
        if(end.equals(selectedDate))b-=direction*(slider.getThumbWidth()/2f+slider.getThumbTrackGapSize());
        if((b-a)*direction<=0)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(ContextCompat.getColor(getContext(),R.color.laya_danger));
        canvas.drawRect(Math.min(a,b),trackY-slider.getTrackHeight()/2f,Math.max(a,b),trackY+slider.getTrackHeight()/2f,paint);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
