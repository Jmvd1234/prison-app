package com.example.myapplication;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps database/password work off the UI thread and survives screen rotation. */
public final class LoginProfileViewModel extends AndroidViewModel {
    public static final class Item {
        public final int id;
        public final String name;
        public final boolean guest, locked;
        Item(User user) { id = user.userID; name = user.username; guest = user.isGuest; locked = user.requiresPassword(); }
    }
    public static final class State {
        public final List<Item> profiles;
        public final boolean busy, loaded;
        public final int error;
        public final boolean passwordError;
        public final String errorTarget;
        public final Item opened;
        public final int createdID, deletedID;
        State(List<Item> profiles, boolean busy, boolean loaded, int error, boolean passwordError,
                String errorTarget, Item opened, int createdID, int deletedID) {
            this.profiles = profiles; this.busy = busy; this.loaded = loaded; this.error = error;
            this.passwordError = passwordError; this.errorTarget = errorTarget;
            this.opened = opened; this.createdID = createdID; this.deletedID = deletedID;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(
            Collections.emptyList(), false, false, 0, false, "", null, 0, 0));
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LoginProfileRepository repository;
    private boolean cleared;

    public LoginProfileViewModel(@NonNull Application app) {
        super(app);
        repository = new LoginProfileRepository(AppDatabase.getInstance(app));
    }
    public LiveData<State> state() { return state; }
    public State current() { return state.getValue(); }

    public void refresh() {
        if (current().busy || current().opened != null || current().createdID != 0 || current().deletedID != 0) return;
        run("list", () -> new Result(repository.load(), null, 0, 0));
    }
    public void create(String name, boolean passwordRequired, String password) {
        run("create", () -> {
            User user = repository.create(name, passwordRequired, password);
            return new Result(repository.load(), null, user.userID, 0);
        });
    }
    public void unlock(int id, String password) {
        run("unlock:" + id, () -> new Result(null, repository.unlock(id, password), 0, 0));
    }
    public void delete(int id, String password) {
        run("delete:" + id, () -> {
            List<String> photos = repository.delete(id, password);
            // Follow the photo editor's reference-checked cleanup on its serialized IO queue.
            ProfilePhotoStore.IO.execute(() -> {
                for (String path : photos) {
                    try { ProfilePhotoStore.deleteIfUnused(getApplication(), path); }
                    catch (RuntimeException ignored) { /* DB deletion already succeeded. */ }
                }
            });
            return new Result(repository.load(), null, 0, id);
        });
    }
    public void clearError() {
        State old = current();
        if (!old.busy) state.setValue(new State(old.profiles, false, old.loaded, 0, false, "", old.opened, old.createdID, old.deletedID));
    }
    public void consumeResult() {
        State old = current();
        state.setValue(new State(old.profiles, old.busy, old.loaded, old.error,
                old.passwordError, old.errorTarget, null, 0, 0));
    }

    private interface Work { Result execute() throws Exception; }
    private static final class Result {
        final List<User> profiles; final User opened; final int createdID, deletedID;
        Result(List<User> profiles, User opened, int createdID, int deletedID) {
            this.profiles = profiles; this.opened = opened; this.createdID = createdID; this.deletedID = deletedID;
        }
    }
    private void run(String target, Work work) {
        State before = current();
        if (before.busy) return;
        state.setValue(new State(before.profiles, true, before.loaded, 0, false, target, null, 0, 0));
        executor.execute(() -> {
            try {
                Result result = work.execute();
                List<Item> items = before.profiles;
                if (result.profiles != null) {
                    items = new ArrayList<>();
                    for (User user : result.profiles) items.add(new Item(user));
                    items = Collections.unmodifiableList(items);
                }
                List<Item> finalItems = items;
                main.post(() -> {
                    if (!cleared) state.setValue(new State(finalItems, false, true, 0, false, "",
                            result.opened == null ? null : new Item(result.opened), result.createdID, result.deletedID));
                });
            } catch (Exception failure) {
                int message = target.startsWith("delete:") ? R.string.login_delete_failed : R.string.login_operation_failed;
                boolean passwordField = false;
                if (failure instanceof LoginProfileRepository.InvalidInput) {
                    message = ((LoginProfileRepository.InvalidInput) failure).message;
                    passwordField = ((LoginProfileRepository.InvalidInput) failure).passwordField;
                }
                final int error = message;
                final boolean passwordError = passwordField;
                main.post(() -> {
                    if (!cleared) state.setValue(new State(before.profiles, false, before.loaded,
                            error, passwordError, target, null, 0, 0));
                });
            }
        });
    }
    @Override protected void onCleared() { cleared = true; executor.shutdown(); }
}
