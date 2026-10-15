package com.example.myapplication;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;

/** Keeps a confirmed deletion running across rotation without holding an Activity. */
public final class EntryDeletionViewModel extends AndroidViewModel {
    final MutableLiveData<Boolean> result = new MutableLiveData<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    public EntryDeletionViewModel(Application application) { super(application); }

    boolean isBusy() { return busy; }

    void delete(int profileID, long userID) {
        if (busy) return;
        busy = true;
        ProfilePhotoStore.IO.execute(() -> {
            Profile deleted;
            try {
                AppDatabase db = AppDatabase.getInstance(getApplication());
                ProfileDao dao = db.ProfileDao();
                deleted = db.runInTransaction(() -> {
                    Profile profile = dao.getProfileByProfileID(profileID);
                    if (userID < 0 || profile == null || profile.userID != userID) {
                        throw new IllegalStateException("Entry no longer available.");
                    }
                    // Existing foreign keys also delete this entry's charges and deduction months.
                    dao.deleteUser(profile);
                    return profile;
                });
            } catch (RuntimeException failure) {
                complete(false);
                return;
            }
            // Reference checking protects photos still used by another entry.
            // Cleanup is best effort; it must not turn a successful DB deletion into a failure.
            try { ProfilePhotoStore.deleteIfUnused(getApplication(), deleted.image_path); }
            catch (RuntimeException ignored) { }
            complete(true);
        });
    }

    private void complete(boolean success) {
        main.post(() -> {
            busy = false;
            result.setValue(success);
        });
    }
}
