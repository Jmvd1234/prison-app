package com.example.myapplication;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;

/** Measures the home action's proportions before layout, keeping the page stable. */
public class HomeActionButton extends AppCompatImageButton {
    public HomeActionButton(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        float density = getResources().getDisplayMetrics().density;
        int shadow = Math.round(32 * density);
        int height = Math.round(Math.max(1, getMeasuredWidth() - shadow) * 1.5f) + shadow;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        height = Math.min(height, Math.round((landscape ? 180 : 300) * density));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
