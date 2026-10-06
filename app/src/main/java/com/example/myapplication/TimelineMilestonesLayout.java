package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Region;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Places tappable XML labels at exact slider stops, stacking nearby labels to avoid overlap. */
public final class TimelineMilestonesLayout extends ViewGroup {
    public interface OnDateClick { void select(LocalDate date); }

    private static final class Label {
        TextView view;
        float fraction, x;
        int left, top, lane;
    }

    private final List<Label> labels = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path connector = new Path();
    private final int green, surface;

    public TimelineMilestonesLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        green = ContextCompat.getColor(context, R.color.breakdown_button_green);
        surface = ContextCompat.getColor(context, R.color.breakdown_surface);
    }

    public void setMilestones(SentenceProjection.Range range, OnDateClick listener) {
        for (Label label : labels) removeView(label.view);
        labels.clear();
        Map<LocalDate, String> grouped = new LinkedHashMap<>();
        for (SentenceProjection.Milestone point : range.milestones) {
            String title = getContext().getString(titleResource(point.kind));
            String previous = grouped.get(point.date);
            grouped.put(point.date, previous == null ? title : previous + " / " + title);
        }
        for (Map.Entry<LocalDate, String> entry : grouped.entrySet()) {
            Label label = new Label();
            label.fraction = range.dates.size() <= 1 ? 0
                    : (float) range.dates.indexOf(entry.getKey()) / (range.dates.size() - 1);
            label.view = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.view_timeline_milestone, this, false);
            label.view.setText(getContext().getString(R.string.breakdown_milestone_label,
                    entry.getValue(), entry.getKey().format(SentenceCalculator.DISPLAY_DATE)));
            label.view.setTag(entry.getKey().toString());
            label.view.setOnClickListener(view -> listener.select(entry.getKey()));
            addView(label.view);
            labels.add(label);
        }
        requestLayout();
        invalidate();
    }

    private int titleResource(SentenceProjection.Kind kind) {
        switch (kind) {
            case LEA_ARREST: return R.string.breakdown_marker_arrest;
            case JAIL_COMMITMENT: return R.string.breakdown_marker_jail;
            case PRISON_COMMITMENT: return R.string.breakdown_marker_prison;
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
            label.view.measure(MeasureSpec.makeMeasureSpec(Math.min(dp(152), width), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            label.left = Math.max(0, Math.min(width - label.view.getMeasuredWidth(),
                    Math.round(label.x - label.view.getMeasuredWidth() / 2f)));
        }
        List<Label> positions = new ArrayList<>(labels);
        positions.sort(Comparator.comparingDouble(label -> label.x));
        List<Integer> laneRight = new ArrayList<>(), laneHeight = new ArrayList<>();
        for (Label label : positions) {
            int lane = 0;
            while (lane < laneRight.size() && label.left < laneRight.get(lane) + dp(8)) lane++;
            if (lane == laneRight.size()) {
                laneRight.add(0);
                laneHeight.add(0);
            }
            label.lane = lane;
            laneRight.set(lane, label.left + label.view.getMeasuredWidth());
            laneHeight.set(lane, Math.max(laneHeight.get(lane), label.view.getMeasuredHeight()));
        }
        int height = slider.getMeasuredHeight() + dp(6);
        for (int lane = 0; lane < laneHeight.size(); lane++) {
            for (Label label : labels) if (label.lane == lane) label.top = height;
            height += laneHeight.get(lane) + dp(4);
        }
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View slider = getChildAt(0);
        slider.layout(0, 0, right - left, slider.getMeasuredHeight());
        for (Label label : labels) label.view.layout(label.left, label.top,
                label.left + label.view.getMeasuredWidth(), label.top + label.view.getMeasuredHeight());
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        Slider slider = (Slider) getChildAt(0);
        float trackY = slider.getHeight() / 2f;
        paint.setColor(green);
        paint.setStrokeWidth(dp(1));
        paint.setStyle(Paint.Style.STROKE);
        canvas.save();
        // Long leaders must not run through another milestone's text when dates cluster.
        // DIFFERENCE clipping also supports this app's API 24 minimum.
        for (Label label : labels) canvas.clipRect(label.view.getLeft() - dp(2),
                label.view.getTop(), label.view.getRight() + dp(2), label.view.getBottom(),
                Region.Op.DIFFERENCE);
        for (Label label : labels) {
            float labelX = label.left + label.view.getWidth() / 2f;
            connector.reset();
            connector.moveTo(label.x, trackY);
            connector.lineTo(label.x, slider.getBottom());
            connector.lineTo(labelX, label.top);
            canvas.drawPath(connector, paint);
        }
        canvas.restore();
        super.dispatchDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        for (Label label : labels) {
            paint.setColor(surface);
            canvas.drawCircle(label.x, trackY, dp(5), paint);
            paint.setColor(green);
            canvas.drawCircle(label.x, trackY, dp(3), paint);
        }
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
