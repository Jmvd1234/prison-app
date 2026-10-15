package com.example.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

/** A tap-accessible info icon. Text and placement are editable in layout XML. */
public final class InfoTooltip extends AppCompatImageButton {
    private final CharSequence message;
    private final CharSequence title;
    private PopupWindow popup;

    public InfoTooltip(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.InfoTooltip);
        title = values.getText(R.styleable.InfoTooltip_helpTitle);
        message = values.getText(R.styleable.InfoTooltip_helpText);
        values.recycle();
        setImageResource(R.drawable.ic_tooltip_info);
        setImageTintList(ContextCompat.getColorStateList(context, R.color.laya_muted));
        setPadding(dp(15), dp(15), dp(15), dp(15));
        setScaleType(ScaleType.FIT_CENTER);
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        setBackgroundResource(ripple.resourceId);
        setContentDescription(context.getString(R.string.tooltip_help, title));
        setFocusable(true);
        setOnClickListener(v -> showHelp());
        setOnLongClickListener(v -> { showHelp(); return true; });
    }

    private void showHelp() {
        if (!isAttachedToWindow() || message == null || message.length() == 0) return;
        if (popup != null) { popup.dismiss(); return; }
        View content = LayoutInflater.from(getContext()).inflate(R.layout.view_tooltip_popup, null);
        ((TextView) content.findViewById(R.id.tooltipMessage)).setText(message);
        ViewCompat.setAccessibilityPaneTitle(content, title);

        // Fit narrow windows and large text; long explanations can scroll inside the bubble.
        Rect frame = new Rect();
        getWindowVisibleDisplayFrame(frame);
        int[] position = new int[2];
        getLocationOnScreen(position);
        int width = Math.max(1, Math.min(dp(300), frame.width() - dp(24)));
        int availableAbove = position[1] - frame.top - dp(8);
        int availableBelow = frame.bottom - position[1] - getHeight() - dp(8);
        int maxHeight = Math.max(1, Math.max(availableAbove, availableBelow));
        content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        popup = new PopupWindow(content, width, content.getMeasuredHeight(), true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(6));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnDismissListener(() -> popup = null);
        // PopupWindow chooses above/below and clips to the visible window at screen edges.
        popup.showAsDropDown(this, 0, dp(4), Gravity.END);
    }

    @Override protected void onDetachedFromWindow() {
        if (popup != null) popup.dismiss();
        super.onDetachedFromWindow();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
