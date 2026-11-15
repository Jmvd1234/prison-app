package com.example.myapplication;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.User;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Free edition entry points. The full edition supplies an SDK-free implementation. */
public final class Edition {
    static long localOwner(android.content.Context context) {
        AppDatabase db = AppDatabase.getInstance(context);
        return db.runInTransaction(() -> {
            User user = db.UserDao().getGuest();
            if (user == null) {
                user = new User(); user.username = "Laya"; user.isGuest = true;
                user.userID = Math.toIntExact(db.UserDao().insertUser(user));
            }
            return (long) user.userID;
        });
    }
    public static boolean routeLaunch(MainActivity activity) {
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER); root.setBackgroundColor(activity.getColor(R.color.laya_background));
        root.addView(new ProgressBar(activity));
        TextView label = new TextView(activity); label.setText("Opening Laya Free…"); label.setPadding(24,24,24,24);
        label.setTextColor(activity.getColor(R.color.laya_label)); root.addView(label);
        activity.setContentView(root);
        ProfilePhotoStore.IO.execute(() -> {
            try {
                long owner = localOwner(activity.getApplicationContext());
                activity.getSharedPreferences("user_session", 0).edit().putLong("currentUserID", owner).commit();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    activity.startActivity(new Intent(activity, HomeActivity.class)); activity.finish();
                });
            } catch (RuntimeException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    root.getChildAt(0).setVisibility(View.GONE);
                    label.setText("Unable to open local storage. Close and reopen Laya Free.");
                });
            }
        });
        return true;
    }
    public static void attach(AppCompatActivity activity) { new AdScreen(activity); }
    public static void newEntry(AppCompatActivity activity, Runnable action) {
        if (AppDatabase.getInstance(activity).ProfileDao().availableProfileSlots() > 0) run(activity, action);
        else AdScreen.get(activity).offerUnlock(() -> run(activity, action));
    }
    private static void run(AppCompatActivity activity, Runnable action) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try { action.run(); }
        catch (IllegalArgumentException e) {
            new MaterialAlertDialogBuilder(activity).setMessage(e.getMessage() == null ? "Check the entered numbers and dates." : e.getMessage())
                .setPositiveButton(android.R.string.ok, null).show();
        }
    }
    public static void unlock(AppCompatActivity activity) { AdScreen.get(activity).offerUnlock(null); }
}
