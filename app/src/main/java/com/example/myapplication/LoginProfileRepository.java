package com.example.myapplication;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.User;
import com.example.myapplication.db.UserDao;
import java.security.GeneralSecurityException;
import java.util.List;

/** Login profiles own the existing User IDs; custody entries keep their original owner. */
final class LoginProfileRepository {
    static final String GUEST_NAME = "Guest";
    private final AppDatabase db;
    private final UserDao users;

    static final class InvalidInput extends RuntimeException {
        final boolean passwordField;
        final int message;
        InvalidInput(boolean passwordField, int message) { this.passwordField = passwordField; this.message = message; }
    }

    LoginProfileRepository(AppDatabase db) { this.db = db; users = db.UserDao(); }

    List<User> load() {
        return db.runInTransaction(() -> {
            // Creating this row once gives Guest a stable owner ID across app launches.
            if (users.getGuest() == null) {
                User guest = new User();
                guest.username = GUEST_NAME;
                guest.isGuest = true;
                users.insertUser(guest);
            }
            return users.getLoginProfiles();
        });
    }

    User create(String enteredName, boolean passwordRequired, String password)
            throws GeneralSecurityException {
        String name = enteredName.trim();
        if (name.isEmpty()) throw new InvalidInput(false, R.string.login_name_required);
        if (name.length() > 40) throw new InvalidInput(false, R.string.login_name_too_long);
        if (name.equalsIgnoreCase(GUEST_NAME)) throw new InvalidInput(false, R.string.login_guest_name_reserved);
        if (passwordRequired && (password == null || password.isEmpty()))
            throw new InvalidInput(true, R.string.login_password_required);
        if (passwordRequired && password.length() > 256)
            throw new InvalidInput(true, R.string.login_password_too_long);
        User user = new User();
        user.username = name;
        // Hidden password drafts are ignored when the toggle is off.
        if (passwordRequired) user.passwordHash = ProfilePassword.hash(password);
        return db.runInTransaction(() -> {
            if (users.countLoginNames(name) != 0) throw new InvalidInput(false, R.string.login_name_taken);
            user.userID = Math.toIntExact(users.insertUser(user));
            return user;
        });
    }

    /** Delete the owner and all cascading rows atomically; return photos to check after commit. */
    List<String> delete(int id, String password) {
        return db.runInTransaction(() -> {
            User user = users.getUserByID(id);
            if (user == null) throw new InvalidInput(false, R.string.login_profile_missing);
            if (user.isGuest) throw new InvalidInput(false, R.string.login_delete_guest_blocked);
            if (user.requiresPassword()) {
                if (password == null || password.isEmpty())
                    throw new InvalidInput(true, R.string.login_password_required);
                if (!passwordMatches(user, password))
                    throw new InvalidInput(true, R.string.login_password_incorrect);
            }
            List<String> photos = db.ProfileDao().getPhotoPathsByUserID(id);
            users.deleteUser(user);
            return photos;
        });
    }

    private static boolean passwordMatches(User user, String password) throws GeneralSecurityException {
        return user.passwordHash != null && !user.passwordHash.isEmpty()
                ? ProfilePassword.matches(password, user.passwordHash)
                : ProfilePassword.matchesLegacy(password, user.password);
    }

    User unlock(int id, String password) throws GeneralSecurityException {
        User user = users.getUserByID(id);
        if (user == null) throw new InvalidInput(false, R.string.login_profile_missing);
        if (!user.requiresPassword()) return user;
        boolean hashed = user.passwordHash != null && !user.passwordHash.isEmpty();
        boolean valid = passwordMatches(user, password);
        if (!valid) throw new InvalidInput(true, R.string.login_password_incorrect);
        if (!hashed || ProfilePassword.needsUpdate(user.passwordHash)) {
            // Older profiles pay the PBKDF2 cost only on their first successful login.
            user.passwordHash = ProfilePassword.hash(password);
            users.upgradePassword(user.userID, user.passwordHash);
            user.password = null;
        }
        return user;
    }
}
