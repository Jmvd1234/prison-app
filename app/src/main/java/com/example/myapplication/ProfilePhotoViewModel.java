package com.example.myapplication;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;

import java.io.IOException;
import java.util.Objects;

/** Retains the photo draft and in-flight work across rotation; edits commit with Save. */
public final class ProfilePhotoViewModel extends AndroidViewModel {
    private static final String PHOTO_PATH = "profile_photo_draft";
    static final class State {
        final String path;
        final boolean busy;
        State(String path, boolean busy) { this.path = path; this.busy = busy; }
    }

    final MutableLiveData<State> state = new MutableLiveData<>();
    final MutableLiveData<Integer> error = new MutableLiveData<>();
    final MutableLiveData<Bundle> completion = new MutableLiveData<>();
    private final SavedStateHandle savedState;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String savedPath;
    private boolean cleared;

    public ProfilePhotoViewModel(Application app, SavedStateHandle savedState) {
        super(app);
        this.savedState = savedState;
    }

    void initialize(String existingPath) {
        if (state.getValue() != null) return;
        savedPath = existingPath == null ? "" : existingPath;
        String path = savedState.contains(PHOTO_PATH) ? savedState.get(PHOTO_PATH) : savedPath;
        publish(path == null ? "" : path, false);
    }

    private void publish(String path, boolean busy) {
        savedState.set(PHOTO_PATH, path);
        state.setValue(new State(path, busy));
    }

    void select(Uri uri) {
        State current = state.getValue();
        if (uri == null || current == null || current.busy) return; // Picker cancellation preserves the draft.
        publish(current.path, true);
        ProfilePhotoStore.IO.execute(() -> {
            try {
                String imported = ProfilePhotoStore.importPhoto(getApplication(), uri);
                main.post(() -> {
                    if (cleared) { discard(imported); return; }
                    if (!Objects.equals(current.path, savedPath)) discard(current.path);
                    publish(imported, false);
                });
            } catch (IOException | RuntimeException failure) {
                main.post(() -> {
                    if (cleared) return;
                    publish(current.path, false);
                    error.setValue(R.string.profile_photo_failed);
                });
            }
        });
    }

    void remove() {
        State current = state.getValue();
        if (current == null || current.busy) return;
        if (!Objects.equals(current.path, savedPath)) discard(current.path);
        publish("", false);
    }

    void save(Profile profile, boolean insert, Bundle result) {
        State current = state.getValue();
        if (current == null || current.busy) return;
        profile.image_path = current.path;
        String oldPath = savedPath;
        publish(current.path, true);
        ProfilePhotoStore.IO.execute(() -> {
            try {
                ProfileDao dao = AppDatabase.getInstance(getApplication()).ProfileDao();
                if (insert) dao.insertProfile(profile);
                else dao.updateUser(profile);
            } catch (RuntimeException failure) {
                main.post(() -> {
                    if (cleared) return;
                    publish(current.path, false);
                    error.setValue(R.string.profile_save_failed);
                });
                return;
            }
            if (!Objects.equals(oldPath, current.path)) discard(oldPath);
            main.post(() -> {
                savedPath = current.path;
                if (!cleared) completion.setValue(result == null ? new Bundle() : result);
            });
        });
    }

    private void discard(String path) {
        if (path == null || path.isEmpty()) return;
        ProfilePhotoStore.IO.execute(() -> {
            // Cleanup is best effort and never turns a successful DB save into an error.
            try { ProfilePhotoStore.deleteIfUnused(getApplication(), path); }
            catch (RuntimeException ignored) { }
        });
    }

    @Override protected void onCleared() {
        cleared = true;
        State current = state.getValue();
        if (current != null) discard(current.path);
        // IO is serialized: a pending DB save completes before this reference-checked cleanup.
    }
}
