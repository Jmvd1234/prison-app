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
        float markerOffset;
        int left, top, lane;

        boolean isZero() { return minimum || maximum; }
    }

    private final List<Label> labels = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path connector = new Path();
    private final int neutral, surface, minimumColor, maximumColor, halo;
    private LocalDate selectedDate;
    private SentenceProjection.Range range;
    private Label minimumLabel, maximumLabel;

    public TimelineMilestonesLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        neutral = ContextCompat.getColor(context, R.color.laya_primary);
        surface = ContextCompat.getColor(context, R.color.laya_surface);
        minimumColor = ContextCompat.getColor(context, R.color.release_minimum_marker);
        maximumColor = ContextCompat.getColor(context, R.color.release_maximum_marker);
        halo = ContextCompat.getColor(context, R.color.release_marker_halo);
    }

    public void setMilestones(SentenceProjection.Range range, OnDateClick listener) {
        this.range = range;
        for (Label label : labels) removeView(label.view);
        labels.clear();
        minimumLabel = maximumLabel = null;
        Map<LocalDate, List<SentenceProjection.Milestone>> grouped = new LinkedHashMap<>();
        for (SentenceProjection.Milestone point : range.milestones) {
            if (point.kind != SentenceProjection.Kind.MINIMUM_ZERO && point.kind != SentenceProjection.Kind.MAXIMUM_ZERO) {
                grouped.computeIfAbsent(point.date, date -> new ArrayList<>()).add(point);
            }
        }
        List<List<SentenceProjection.Milestone>> groups = new ArrayList<>(grouped.values());
        // Two penalties can first cross zero on the same credit day while their earlier
        // estimates differ. Give each release its own independently moving label.
        for (SentenceProjection.Milestone point : range.milestones) {
            if (point.kind == SentenceProjection.Kind.MINIMUM_ZERO || point.kind == SentenceProjection.Kind.MAXIMUM_ZERO) {
                groups.add(Collections.singletonList(point));
            }
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
                title.setSpan(new ForegroundColorSpan(minimum ? minimumColor : maximum ? maximumColor : neutral),
                        start, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            title.append('\n').append(label.date.format(SentenceCalculator.DISPLAY_DATE));
            label.fraction = label.labelFraction = range.positionOf(label.date);
            label.view = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.view_timeline_milestone, this, false);
            label.view.setText(title);
            label.view.setTag(label.date.toString());
            label.view.setOnClickListener(view -> listener.select(label.today ? LocalDate.now() : label.date));
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
        if (label.reached != reached) {
            label.reached = reached;
            requestLayout();
        }
        int titleId = label.minimum
                ? (reached ? R.string.breakdown_marker_minimum : R.string.breakdown_marker_minimum_estimate)
                : (reached ? R.string.breakdown_marker_maximum : R.string.breakdown_marker_maximum_estimate);
        String title = getContext().getString(titleId);
        String formattedDate = estimate.format(SentenceCalculator.DISPLAY_DATE);
        if (estimate.isAfter(range.end)) {
            formattedDate = getContext().getString(R.string.breakdown_marker_beyond_range, formattedDate);
        }
        SpannableStringBuilder text = new SpannableStringBuilder(title);
        text.setSpan(new ForegroundColorSpan(markerColor(label)), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append('\n').append(formattedDate);
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
        return label.maximum ? maximumColor : label.minimum ? minimumColor : neutral;
    }

    private int titleResource(SentenceProjection.Kind kind) {
        switch (kind) {
            case LEA_ARREST: return R.string.breakdown_marker_arrest;
            case JAIL_COMMITMENT: return R.string.breakdown_marker_jail;
            case PRISON_COMMITMENT: return R.string.breakdown_marker_prison;
            case TODAY: return R.string.breakdown_today;
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
            label.markerOffset = 0;
            float fraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 - label.fraction : label.fraction;
            label.x = side + fraction * (width - 2 * side);
            float labelFraction = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? 1 - label.labelFraction : label.labelFraction;
            label.labelAnchorX = side + labelFraction * (width - 2 * side);
            label.view.measure(MeasureSpec.makeMeasureSpec(Math.min(dp(152), width), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            label.left = Math.max(0, Math.min(width - label.view.getMeasuredWidth(),
                    Math.round(label.labelAnchorX - label.view.getMeasuredWidth() / 2f)));
        }
        // Nearby min/max dates keep their exact horizontal stops, but stagger their
        // larger circles vertically. A coincident date instead uses a two-color circle.
        for (Label first : labels) for (Label second : labels) {
            if (first != second && first.isZero() && second.isZero()
                    && first.reached && second.reached
                    && !first.date.equals(second.date)
                    && Math.abs(first.x - second.x) < dp(28)) {
                first.markerOffset = first.minimum ? -dp(12) : dp(12);
            }
        }
        List<Label> positions = new ArrayList<>(labels);
        positions.sort(Comparator.comparingDouble(label -> label.labelAnchorX));
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
            connector.lineTo(label.labelAnchorX, slider.getBottom());
            connector.lineTo(labelX, label.top);
            canvas.drawPath(connector, paint);
        }
        canvas.restore();
        super.dispatchDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        for (Label label : labels) {
            if (!label.isZero()) {
                paint.setColor(surface);
                canvas.drawCircle(label.x, trackY, dp(5), paint);
                paint.setColor(neutral);
                canvas.drawCircle(label.x, trackY, dp(3), paint);
                continue;
            }
            if (!label.reached) continue;
            boolean sharedZero = minimumLabel != null && maximumLabel != null
                    && minimumLabel.reached && maximumLabel.reached
                    && minimumLabel.date.equals(maximumLabel.date);
            if (sharedZero && label.maximum) continue;
            float y = trackY + label.markerOffset;
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
            if (!sharedEstimate && label.maximum && minimumLabel != null
                    && !minimumLabel.reached
                    && Math.abs(minimumLabel.labelAnchorX - x) < dp(16)) y = trackY + dp(14);
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

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
