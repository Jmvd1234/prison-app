package com.example.myapplication;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageButton;

/** Measures the home action's proportions before layout, keeping the page stable. */
public class HomeActionButton extends AppCompatImageButton {
    public HomeActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (BuildConfig.FREE_EDITION) {
            float density = getResources().getDisplayMetrics().density;
            setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            setPadding(Math.round(40*density),Math.round(24*density),Math.round(40*density),Math.round(24*density));
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        float density = getResources().getDisplayMetrics().density;
        int shadow = Math.round(32 * density);
        int height = Math.round(Math.max(1, getMeasuredWidth() - shadow) * (BuildConfig.FREE_EDITION ? 1.0f : 1.5f)) + shadow;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        height = Math.min(height, Math.round((BuildConfig.FREE_EDITION ? (landscape ? 112 : 190) : (landscape ? 180 : 300)) * density));
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
}
