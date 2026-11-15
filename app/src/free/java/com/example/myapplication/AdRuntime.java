package com.example.myapplication;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** All callbacks return on the main thread; no case data enters an ad request. */
final class AdRuntime {
    static boolean requestsEnabled = true; // Package-private seam for offline unit tests only.
    static volatile boolean rewardShowing;
    private static boolean initializing, initialized;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Consumer<Boolean>> pending = new ArrayList<>();
    static void prepare(Activity activity, Consumer<Boolean> ready) {
        if (!requestsEnabled) { ready.accept(false); return; }
        // Demo IDs cannot serve paid ads and do not have a configured consent message.
        if (BuildConfig.ADS_TEST_MODE) { initialize(activity.getApplicationContext(), ready); return; }
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(activity);
        info.requestConsentInfoUpdate(activity, new ConsentRequestParameters.Builder().build(),
            () -> {
                if (activity.isDestroyed() || activity.isFinishing()) { ready.accept(false); return; }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                    if (info.canRequestAds()) initialize(activity.getApplicationContext(), ready);
                    else ready.accept(false);
                });
            }, error -> {
                if (info.canRequestAds()) initialize(activity.getApplicationContext(), ready);
                else ready.accept(false);
            });
    }
    private static void initialize(Context context, Consumer<Boolean> callback) {
        if (initialized) { callback.accept(true); return; }
        pending.add(callback);
        if (initializing) return;
        initializing = true;
        new Thread(() -> {
            boolean success;
            try {
                MobileAds.initialize(context, new InitializationConfig.Builder(context.getString(R.string.admob_app_id)).build());
                success = true;
            } catch (RuntimeException e) { success = false; }
            final boolean result = success;
            MAIN.post(() -> {
                initializing = false; initialized = result;
                List<Consumer<Boolean>> callbacks = new ArrayList<>(pending); pending.clear();
                for (Consumer<Boolean> consumer : callbacks) consumer.accept(result);
            });
        }, "laya-ads-init").start();
    }
    static boolean canRequest(Context context) {
        return requestsEnabled && initialized && (BuildConfig.ADS_TEST_MODE || UserMessagingPlatform.getConsentInformation(context).canRequestAds());
    }
}
