package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.ProfileDao;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.UserMessagingPlatform;
import java.util.ArrayList;
import java.util.List;

final class AdScreen implements DefaultLifecycleObserver {
    private final AppCompatActivity activity;
    private final ProfileDao dao;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<AdBanner> banners = new ArrayList<>();
    private AlertDialog offer, loading;
    private boolean preparing, ready, destroyed, loadingReward;
    private int request;
    private MaterialButton privacyOptions;
    private Runnable deferredReward, afterResume;
    static AdScreen get(AppCompatActivity activity) {
        return (AdScreen) activity.findViewById(android.R.id.content).getTag(R.id.freeAdScreen);
    }
    AdScreen(AppCompatActivity activity) {
        this.activity = activity; dao = AppDatabase.getInstance(activity).ProfileDao();
        activity.findViewById(android.R.id.content).setTag(R.id.freeAdScreen,this);
        View slots = activity.findViewById(R.id.freeSlotsPanel);
        if (slots != null) {
            slots.setVisibility(View.VISIBLE);
            if (activity instanceof DashboardActivity && activity.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                ((LinearLayout) slots).setOrientation(LinearLayout.VERTICAL);
                View status = activity.findViewById(R.id.freeSlotStatus);
                status.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
            }
            activity.findViewById(R.id.freeUnlockSlot).setOnClickListener(v -> offerUnlock(null));
            refreshSlots();
        }
        dao.observeSavedProfileCount().observe(activity, count -> refreshSlots());
        dao.observeSlotRewardCount().observe(activity, count -> refreshSlots());
        setupPrivacy(); activity.getLifecycle().addObserver(this);
    }
    private boolean active() { return !destroyed && !activity.isFinishing() && !activity.isDestroyed(); }
    private boolean resumed() { return active() && activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED); }
    @Override public void onResume(@NonNull LifecycleOwner owner) {
        refreshSlots();
        if (afterResume != null) { Runnable action = afterResume; afterResume = null; main.post(action); }
        if (ready) for (AdBanner banner : banners) banner.retryOnResume();
        else prepare(null);
    }
    private void prepare(Runnable after) {
        if (after != null) deferredReward = after;
        if (ready && AdRuntime.canRequest(activity)) { runDeferred(); return; }
        if (preparing) return;
        preparing = true;
        AdRuntime.prepare(activity, success -> main.post(() -> {
            preparing = false;
            if (!active()) return;
            ready = success; updatePrivacy();
            if (success) { attachBanners(); runDeferred(); }
            else if (deferredReward != null) {
                deferredReward = null; failLoad("Ads are unavailable right now. Check your connection and try again. Your saved profiles still work offline.");
            }
        }));
    }
    private void runDeferred() { Runnable action = deferredReward; deferredReward = null; if(action != null) action.run(); }
    private void attachBanners() {
        if (!banners.isEmpty()) return;
        int id = activity instanceof HomeActivity ? R.string.admob_banner_home : activity instanceof DashboardActivity
            ? R.string.admob_banner_dashboard : activity instanceof BreakdownActivity ? R.string.admob_banner_breakdown : R.string.admob_banner_entry;
        for (int slot : new int[]{R.id.freeBannerPrimary,R.id.freeBannerSecondary}) {
            FrameLayout host = activity.findViewById(slot);
            if (host != null) banners.add(new AdBanner(activity,host,activity.getString(id)));
        }
    }
    private void refreshSlots() {
        TextView status = activity.findViewById(R.id.freeSlotStatus);
        if (status != null) status.setText(activity.getString(R.string.free_slot_status,dao.countSavedProfiles(),dao.profileSlotCapacity()));
    }
    void offerUnlock(Runnable afterReward) {
        if (!resumed() || loadingReward || AdRuntime.rewardShowing || (offer != null && offer.isShowing())) return;
        offer = new MaterialAlertDialogBuilder(activity).setTitle("Unlock one profile slot")
            .setMessage("Complete one rewarded ad to permanently unlock one additional profile slot on this device. The slot stays available when you delete a profile.\n\nClearing app data or uninstalling resets unlocked slots. Profile backups do not transfer slots.")
            .setNegativeButton("Not now",null)
            .setPositiveButton("Watch ad",(dialog,which) -> loadReward(afterReward)).create();
        offer.setOnDismissListener(d -> offer = null); offer.show();
    }
    private void loadReward(Runnable afterReward) {
        if (!resumed() || loadingReward || AdRuntime.rewardShowing) return;
        loadingReward = true; final int token = ++request;
        loading = new MaterialAlertDialogBuilder(activity).setTitle("Loading rewarded ad…")
            .setMessage("You can cancel without changing your saved profiles.")
            .setNegativeButton(android.R.string.cancel,(d,w) -> cancelLoad()).create();
        loading.setOnCancelListener(d -> cancelLoad()); loading.show();
        main.postDelayed(() -> {
            if (token == request && loadingReward) failLoad("The ad is taking too long to load. Please try again later.");
        },30000);
        prepare(() -> {
            if (token != request || !loadingReward || !resumed()) { cancelLoad(); return; }
            RewardedAd.load(new AdRequest.Builder(activity.getString(R.string.admob_rewarded_id)).build(),new AdLoadCallback<RewardedAd>() {
                @Override public void onAdLoaded(RewardedAd ad) {
                    main.post(() -> {
                        if (token != request || !loadingReward || !resumed() || AdRuntime.rewardShowing) return;
                        loadingReward = false; dismissLoading(); showReward(ad,afterReward);
                    });
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    main.post(() -> { if(token == request && loadingReward) failLoad("No ad is available right now. Check your connection and try again later."); });
                }
            });
        });
    }
    private void showReward(RewardedAd ad, Runnable afterReward) {
        AdRuntime.rewardShowing = true;
        SlotRewardSession reward = new SlotRewardSession(dao);
        ad.setAdEventCallback(new RewardedAdEventCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                AdRuntime.rewardShowing = false;
                main.post(() -> {
                    if (!active()) return;
                    refreshSlots();
                    if (reward.earned()) {
                        Toast.makeText(activity,"One profile slot unlocked.",Toast.LENGTH_LONG).show();
                        if (afterReward != null) { if (resumed()) afterReward.run(); else afterResume = afterReward; }
                    } else Toast.makeText(activity,"Ad closed before a reward was earned. No slot was added.",Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onAdFailedToShowFullScreenContent(FullScreenContentError error) {
                AdRuntime.rewardShowing = false;
                main.post(() -> { message("The ad could not be shown. Please try again later."); });
            }
        });
        try {
            ad.show(activity, item -> {
                // Only this SDK-earned callback grants capacity, never closing or loading an ad.
                try { reward.onEarnedReward(); }
                catch (RuntimeException e) { main.post(() -> message("The earned slot could not be saved. Check that the device has free storage.")); }
            });
        } catch (RuntimeException e) { AdRuntime.rewardShowing = false; message("The ad could not be shown. Please try again later."); }
    }
    private void failLoad(String text) { cancelLoad(); message(text); }
    private void cancelLoad() { request++; loadingReward = false; deferredReward = null; dismissLoading(); }
    private void dismissLoading() { if (loading != null) { loading.dismiss(); loading = null; } }
    private void message(String text) {
        if (resumed()) new MaterialAlertDialogBuilder(activity).setMessage(text).setPositiveButton(android.R.string.ok,null).show();
    }
    private void setupPrivacy() {
        FrameLayout footer = activity.findViewById(R.id.freePrivacyFooter); if (footer == null) return;
        footer.setVisibility(View.VISIBLE);
        LinearLayout column = new LinearLayout(activity); column.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12*activity.getResources().getDisplayMetrics().density); column.setPadding(padding,padding,padding,padding);
        TextView text = new TextView(activity); text.setText(activity.getString(R.string.login_local_note)
            + (BuildConfig.ADS_TEST_MODE ? "\n\nTest ads are enabled. They do not earn revenue." : ""));
        text.setTextSize(12); text.setTextColor(activity.getColor(R.color.laya_muted)); column.addView(text);
        MaterialButton info = new MaterialButton(activity,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        info.setText("Privacy & ads"); info.setAllCaps(false); column.addView(info);
        info.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity).setTitle("Privacy & ads")
            .setMessage("Case details and photos stay in local app storage unless you export them. Laya does not send names, charges, dates or calculations in ad requests.\n\nGoogle’s advertising SDK may process IP addresses, device identifiers, ad interactions and diagnostics. Ads need an internet connection; calculations and saved profiles work offline.\n\nUnlocked slots are stored on this installation and are not included in profile exports.")
            .setPositiveButton(android.R.string.ok,null)
            .setNeutralButton("Google privacy",(d,w) -> activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://policies.google.com/technologies/ads")))).show());
        privacyOptions = new MaterialButton(activity,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        privacyOptions.setText("Ad privacy choices"); privacyOptions.setAllCaps(false); privacyOptions.setVisibility(View.GONE);column.addView(privacyOptions);
        privacyOptions.setOnClickListener(v -> UserMessagingPlatform.showPrivacyOptionsForm(activity,error -> {
            for (AdBanner banner : banners) banner.destroy(); banners.clear(); ready = false;
            // Recheck permission before loading any more ads after choices change.
            if (UserMessagingPlatform.getConsentInformation(activity).canRequestAds()) prepare(null);
            updatePrivacy();
            if(error != null) message("Privacy choices are unavailable right now. Please try again later.");
        }));
        TextView attribution = new TextView(activity);
        attribution.setText(R.string.login_attribution); attribution.setTextSize(11);
        attribution.setTextColor(activity.getColor(R.color.laya_muted)); attribution.setGravity(android.view.Gravity.CENTER);
        attribution.setPadding(0,padding,0,0); column.addView(attribution,new LinearLayout.LayoutParams(-1,-2));
        footer.addView(column); updatePrivacy();
    }
    private void updatePrivacy() {
        if (privacyOptions != null) privacyOptions.setVisibility(!BuildConfig.ADS_TEST_MODE && UserMessagingPlatform.getConsentInformation(activity)
            .getPrivacyOptionsRequirementStatus() == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED ? View.VISIBLE : View.GONE);
    }
    @Override public void onDestroy(@NonNull LifecycleOwner owner) {
        destroyed = true; cancelLoad();
        if (offer != null) { offer.dismiss(); offer = null; }
        for (AdBanner banner : banners) banner.destroy(); banners.clear();
        main.removeCallbacksAndMessages(null);
    }
}
