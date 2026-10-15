package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

/** Local, scrollable profile picker. Password entry and profile creation stay in dialogs. */
public class MainActivity extends AppCompatActivity {
    private LoginProfileViewModel model;
    private LinearLayout profiles;
    private View createButton, loading, emptyHint, retry;
    private TextView status;
    private List<LoginProfileViewModel.Item> rendered;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        profiles = findViewById(R.id.loginProfilesContainer);
        createButton = findViewById(R.id.createLoginProfileButton);
        loading = findViewById(R.id.loginProfilesLoading);
        emptyHint = findViewById(R.id.loginProfilesEmptyHint);
        status = findViewById(R.id.loginProfilesStatus);
        retry = findViewById(R.id.loginProfilesRetry);
        model = new ViewModelProvider(this).get(LoginProfileViewModel.class);
        createButton.setOnClickListener(v -> showDialog(LoginProfileDialogFragment.createProfile()));
        retry.setOnClickListener(v -> model.refresh());
        model.state().observe(this, this::render);
    }

    @Override protected void onStart() {
        super.onStart();
        // Selecting a profile is always a new session. Never reuse the previous owner here.
        getSharedPreferences("user_session", MODE_PRIVATE).edit().remove("currentUserID").apply();
        model.refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        // Results that arrived while paused are consumed only once the picker is visible.
        findViewById(R.id.main).post(() -> consumeResult(model.current()));
    }

    private void render(LoginProfileViewModel.State state) {
        if (rendered != state.profiles) {
            profiles.removeAllViews();
            for (LoginProfileViewModel.Item item : state.profiles) addProfile(item);
            rendered = state.profiles;
        }
        boolean enabled = state.loaded && !state.busy;
        createButton.setEnabled(enabled);
        for (int i = 0; i < profiles.getChildCount(); i++) {
            View card = profiles.getChildAt(i);
            card.setEnabled(enabled);
            card.findViewById(R.id.deleteLoginProfileButton).setEnabled(enabled);
        }
        loading.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        emptyHint.setVisibility(state.loaded && state.profiles.size() == 1 && state.profiles.get(0).guest
                ? View.VISIBLE : View.GONE);
        boolean listError = state.error != 0 && (state.errorTarget.equals("list")
                || !hasProfileDialog());
        status.setVisibility(listError ? View.VISIBLE : View.GONE);
        retry.setVisibility(listError ? View.VISIBLE : View.GONE);
        if (listError) status.setText(state.error);
        consumeResult(state);
    }

    private void addProfile(LoginProfileViewModel.Item item) {
        View card = getLayoutInflater().inflate(R.layout.view_login_profile, profiles, false);
        card.setTag(item.id);
        String name = displayName(item);
        int subtitle = item.guest ? R.string.login_guest_subtitle
                : item.locked ? R.string.login_password_protected : R.string.login_no_password;
        ((TextView) card.findViewById(R.id.loginProfileName)).setText(name);
        ((TextView) card.findViewById(R.id.loginProfileSubtitle)).setText(subtitle);
        ((ImageView) card.findViewById(R.id.loginProfileStatusIcon)).setImageResource(
                item.locked ? R.drawable.ic_profile_lock : R.drawable.ic_profile_enter);
        card.setContentDescription(name + ", " + getString(subtitle));
        View delete = card.findViewById(R.id.deleteLoginProfileButton);
        delete.setVisibility(item.guest ? View.GONE : View.VISIBLE);
        delete.setContentDescription(getString(R.string.login_delete_description, name));
        delete.setOnClickListener(v -> {
            if (item.guest || model.current().busy || getSupportFragmentManager().isStateSaved() || hasProfileDialog()) return;
            model.clearError();
            DeleteLoginProfileDialogFragment.create(item.id, name, item.locked)
                    .showNow(getSupportFragmentManager(), DeleteLoginProfileDialogFragment.TAG);
        });
        card.setOnClickListener(v -> {
            if (model.current().busy || hasProfileDialog()) return;
            if (item.locked) showDialog(LoginProfileDialogFragment.unlockProfile(item.id, name));
            else model.unlock(item.id, null);
        });
        profiles.addView(card);
    }

    private String displayName(LoginProfileViewModel.Item item) {
        if (item.guest) return getString(R.string.login_guest);
        return item.name == null || item.name.trim().isEmpty() ? getString(R.string.login_unnamed) : item.name;
    }

    private void showDialog(LoginProfileDialogFragment dialog) {
        if (model.current().busy || getSupportFragmentManager().isStateSaved()
                || hasProfileDialog()) return;
        model.clearError();
        dialog.showNow(getSupportFragmentManager(), LoginProfileDialogFragment.TAG);
    }

    private boolean hasProfileDialog() {
        return getSupportFragmentManager().findFragmentByTag(LoginProfileDialogFragment.TAG) != null
                || getSupportFragmentManager().findFragmentByTag(DeleteLoginProfileDialogFragment.TAG) != null;
    }

    private void consumeResult(LoginProfileViewModel.State state) {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                || getSupportFragmentManager().isStateSaved()) return;
        if (state.opened == null && state.createdID == 0 && state.deletedID == 0) return;
        model.consumeResult();
        Fragment popup = getSupportFragmentManager().findFragmentByTag(LoginProfileDialogFragment.TAG);
        if (popup instanceof DialogFragment) ((DialogFragment) popup).dismiss();
        Fragment deletion = getSupportFragmentManager().findFragmentByTag(DeleteLoginProfileDialogFragment.TAG);
        if (deletion instanceof DialogFragment) ((DialogFragment) deletion).dismiss();
        if (state.opened != null) {
            getSharedPreferences("user_session", MODE_PRIVATE).edit()
                    .putLong("currentUserID", state.opened.id).apply();
            startActivity(new Intent(this, HomeActivity.class)
                    .putExtra(HomeActivity.EXTRA_LOGIN_NAME, displayName(state.opened)));
        } else if (state.deletedID != 0) {
            Snackbar.make(findViewById(R.id.main), R.string.login_deleted, Snackbar.LENGTH_SHORT).show();
        } else {
            Snackbar.make(findViewById(R.id.main), R.string.login_created, Snackbar.LENGTH_SHORT).show();
            View card = profiles.findViewWithTag(state.createdID);
            if (card != null) card.post(() -> {
                ScrollView scroll = findViewById(R.id.main);
                android.graphics.Rect bounds = new android.graphics.Rect();
                card.getDrawingRect(bounds);
                scroll.offsetDescendantRectToMyCoords(card, bounds);
                scroll.smoothScrollBy(0, bounds.top - scroll.getHeight() / 3);
            });
        }
    }
}
