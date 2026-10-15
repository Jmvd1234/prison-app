package com.example.myapplication;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileDao;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps unsaved checkbox edits and in-flight saves across rotation. */
public final class DeductionDatesViewModel extends AndroidViewModel {
    static final class State {
        final Profile profile;
        final List<DeductionMonth> draft;
        final boolean busy;
        final int error, saves;
        State(Profile profile, List<DeductionMonth> draft, boolean busy, int error, int saves) {
            this.profile = profile;
            this.draft = Collections.unmodifiableList(new ArrayList<>(draft));
            this.busy = busy; this.error = error; this.saves = saves;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, Collections.emptyList(), true, 0, 0));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ProfileDao dao;
    private boolean initialized, cleared;
    private int profileID;
    private long owner;
    public DeductionDatesViewModel(@NonNull Application app) { super(app); dao = AppDatabase.getInstance(app).ProfileDao(); }
    LiveData<State> state() { return state; }
    State current() { return state.getValue(); }

    void initialize(int profileID, long owner, List<DeductionMonth> restoredDraft) {
        if (initialized) return;
        initialized = true; this.profileID = profileID; this.owner = owner;
        worker.execute(() -> {
            try {
                Profile profile = dao.getProfileByProfileID(profileID);
                if (profile == null || profile.userID != owner) {
                    post(new State(null, Collections.emptyList(), false, R.string.breakdown_profile_missing, 0));
                    return;
                }
                post(new State(profile, restoredDraft == null ? profile.deductionMonths : restoredDraft, false, 0, 0));
            } catch (RuntimeException error) {
                post(new State(null, Collections.emptyList(), false, R.string.breakdown_load_failed, 0));
            }
        });
    }

    void change(List<DeductionMonth> rows) {
        State old = current();
        if (old.busy || old.profile == null) return;
        List<DeductionMonth> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(DeductionMonth::date));
        state.setValue(new State(old.profile, sorted, false, 0, old.saves));
    }

    void save() {
        State before = current();
        if (before.busy || before.profile == null) return;
        state.setValue(new State(before.profile, before.draft, true, 0, before.saves));
        worker.execute(() -> {
            try {
                long active = getApplication().getSharedPreferences("user_session", 0).getLong("currentUserID", -1);
                if (active != owner) throw new IllegalStateException();
                Profile saved = dao.saveDeductionMonths(profileID, owner, before.draft);
                post(new State(saved, saved.deductionMonths, false, 0, before.saves + 1));
            } catch (RuntimeException error) {
                post(new State(before.profile, before.draft, false, R.string.deduction_save_failed, before.saves));
            }
        });
    }

    private void post(State next) { main.post(() -> { if (!cleared) state.setValue(next); }); }
    @Override protected void onCleared() { cleared = true; worker.shutdown(); }
}
