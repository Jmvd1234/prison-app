package com.example.myapplication;

import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

/** A reserved, labelled banner slot; never overlays content or loads off screen. */
final class AdBanner {
    private final AppCompatActivity activity;
    private final FrameLayout host;
    private final String unit;
    private final LinearLayout container;
    private final FrameLayout content;
    private AdView ad;
    private int width;
    private boolean destroyed, failed;
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::maybeLoad;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::maybeLoad;
    AdBanner(AppCompatActivity activity, FrameLayout host, String unit) {
        this.activity = activity; this.host = host; this.unit = unit;
        container = new LinearLayout(activity); container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL); container.setPadding(dp(12),dp(20),dp(12),dp(20));
        TextView label = new TextView(activity); label.setText(BuildConfig.ADS_TEST_MODE ? "Advertisement · Test ad" : "Advertisement");
        label.setTextSize(10); label.setTextColor(activity.getColor(R.color.laya_muted)); label.setPadding(0,0,0,dp(6));
        container.addView(label);
        content = new FrameLayout(activity); content.setMinimumHeight(dp(50));
        container.addView(content,new LinearLayout.LayoutParams(-1,-2));
        host.addView(container,new FrameLayout.LayoutParams(-1,-2)); host.setVisibility(View.VISIBLE);
        host.getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        host.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        host.post(this::maybeLoad);
    }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    void maybeLoad() {
        if (destroyed || failed || !AdRuntime.canRequest(activity) || !activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) return;
        Rect visible = new Rect();
        if (!host.isShown() || !host.getGlobalVisibleRect(visible) || visible.height() <= 0) return;
        int available = (int)((host.getWidth() - dp(24)) / activity.getResources().getDisplayMetrics().density);
        if (available < 250 || (ad != null && Math.abs(available-width)<2)) return;
        if (ad != null) { ad.destroy(); content.removeAllViews(); }
        width = available;
        AdSize size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width);
        content.setMinimumHeight(size.getHeightInPixels(activity));
        ad = new AdView(activity); final AdView loading = ad;
        content.addView(ad,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        ad.loadAd(new BannerAdRequest.Builder(unit,size).build(),new AdLoadCallback<BannerAd>() {
            @Override public void onAdLoaded(BannerAd banner) { /* SDK renders into its reserved container. */ }
            @Override public void onAdFailedToLoad(LoadAdError error) {
                activity.runOnUiThread(() -> {
                    if (destroyed || ad != loading) return;
                    failed = true; loading.destroy(); ad = null; content.removeAllViews(); host.setVisibility(View.GONE);
                });
            }
        });
    }
    void retryOnResume() {
        if (failed) { failed = false; host.setVisibility(View.VISIBLE); }
        host.post(this::maybeLoad);
    }
    void destroy() {
        destroyed = true;
        if(host.getViewTreeObserver().isAlive()) {
            host.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
            host.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        }
        if(ad != null) { ad.destroy(); ad = null; }
        host.removeAllViews(); host.setVisibility(View.GONE);
    }
}
