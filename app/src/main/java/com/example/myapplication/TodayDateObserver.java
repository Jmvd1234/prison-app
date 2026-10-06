package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.ContextCompat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/** Watches the phone's local date only while its screen is visible. No database writes. */
final class TodayDateObserver {
    private final Context context;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocalDate lastDate;
    private boolean active;
    private final Runnable midnight = this::check;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { check(); }
    };

    TodayDateObserver(Context context, Runnable changed) {
        this.context = context;
        this.changed = changed;
    }

    void start() {
        if (active) return;
        active = true;
        lastDate = LocalDate.now();
        IntentFilter filter = new IntentFilter(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        schedule();
    }

    void stop() {
        if (!active) return;
        active = false;
        handler.removeCallbacks(midnight);
        context.unregisterReceiver(receiver);
    }

    private void check() {
        if (!active) return;
        LocalDate today = LocalDate.now();
        if (!today.equals(lastDate)) {
            lastDate = today;
            changed.run();
        }
        schedule();
    }

    private void schedule() {
        handler.removeCallbacks(midnight);
        ZonedDateTime now = ZonedDateTime.now();
        long delay = Duration.between(now, now.toLocalDate().plusDays(1)
                .atStartOfDay(now.getZone())).toMillis();
        handler.postDelayed(midnight, Math.max(1000L, delay + 100));
    }
}
