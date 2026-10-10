package com.example.myapplication;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.SavedStateHandle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.myapplication.db.AppDatabase;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.User;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29) // Fixtures use scoped MediaStore insertion without broad permissions.
public class ProfilePhotoTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Uri> fixtures = new ArrayList<>();
    private final List<String> importedPaths = new ArrayList<>();
    private Context context;
    private AppDatabase db;
    private User user;
    private SharedPreferences session;
    private boolean hadSession;
    private long previousUser;
    private static final int TEAL = Color.rgb(36, 147, 166), PURPLE = Color.rgb(111, 73, 164);

    @Before public void setup() {
        context = instrumentation.getTargetContext();
        db = AppDatabase.getInstance(context);
        user = new User();
        user.username = "photo-test-" + UUID.randomUUID();
        user.password = "synthetic-test";
        user.userID = (int) db.UserDao().insertUser(user);
        session = context.getSharedPreferences("user_session", Context.MODE_PRIVATE);
        hadSession = session.contains("currentUserID");
        previousUser = session.getLong("currentUserID", -1);
        assertTrue(session.edit().putLong("currentUserID", user.userID).commit());
    }

    @After public void cleanup() throws Exception {
        for (Profile p : db.ProfileDao().getProfilesByUserID(user.userID)) importedPaths.add(p.image_path);
        db.UserDao().deleteUser(user);
        ProfilePhotoStore.IO.submit(() -> {
            for (String path : importedPaths) ProfilePhotoStore.deleteIfUnused(context, path);
        }).get(10, TimeUnit.SECONDS);
        for (Uri uri : fixtures) context.getContentResolver().delete(uri, null, null);
        SharedPreferences.Editor editor = session.edit();
        if (hadSession) editor.putLong("currentUserID", previousUser);
        else editor.remove("currentUserID");
        assertTrue(editor.commit());
    }

    @Test public void addPhotoSurvivesRotationSaveAndReopenAcrossAllScreens() throws Exception {
        Uri source = fixture(TEAL, false, false);
        try (ActivityScenario<newEntryActivity> screen = ActivityScenario.launch(newEntryActivity.class)) {
            choose(screen, source, false);
            awaitPhoto(screen, TEAL);
            screen.onActivity(activity -> activity.findViewById(R.id.saveEntryButton).performClick());
            assertEquals(Lifecycle.State.RESUMED, screen.getState()); // Validation must keep the draft.
            screen.onActivity(activity -> {
                set(activity, R.id.inputFirstName, "Photo");
                set(activity, R.id.inputLastName, "Example");
                set(activity, R.id.inputLEADate, "Jan 10, 2018");
                set(activity, R.id.inputCommitmentDate, "Jun 20, 2018");
                set(activity, R.id.inputMinYears, "10");
                set(activity, R.id.inputMaxYears, "15");
            });
            screen.recreate();
            awaitPhoto(screen, TEAL);
            screenshot("photo-add.png");
            screen.onActivity(activity -> activity.findViewById(R.id.saveEntryButton).performClick());
            await(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Profile saved = db.ProfileDao().getProfilesByUserID(user.userID).get(0);
        assertTrue(ProfilePhotoStore.resolve(context, saved.image_path).isFile());
        context.getContentResolver().delete(source, null, null); // The app owns an independent copy.
        fixtures.remove(source);
        try (ActivityScenario<DashboardActivity> screen = ActivityScenario.launch(DashboardActivity.class)) {
            awaitScreen(screen, activity -> hasColor(rowImage(activity), TEAL));
            screenshot("photo-dashboard.png");
            screen.recreate();
            awaitScreen(screen, activity -> hasColor(rowImage(activity), TEAL));
        }
        try (ActivityScenario<editProfileActivity> screen = edit(saved.profileID)) {
            awaitPhoto(screen, TEAL);
            screenshot("photo-edit.png");
        }
        assertEquals(saved.image_path, db.ProfileDao().getProfileByProfileID(saved.profileID).image_path);
    }

    @Test public void replaceRemoveCancelAndDeleteRespectSavedFiles() throws Exception {
        Profile original = storedProfile(importPhoto(fixture(TEAL, false, false)));
        File oldPhoto = ProfilePhotoStore.resolve(context, original.image_path);
        Uri replacement = fixture(PURPLE, false, false);
        try (ActivityScenario<editProfileActivity> screen = edit(original.profileID)) {
            choose(screen, replacement, false);
            awaitPhoto(screen, PURPLE);
            screen.recreate();
            awaitPhoto(screen, PURPLE);
        } // Cancel replacement.
        assertEquals(original.image_path, db.ProfileDao().getProfileByProfileID(original.profileID).image_path);
        assertTrue(oldPhoto.isFile());
        try (ActivityScenario<editProfileActivity> screen = edit(original.profileID)) {
            screen.onActivity(activity -> activity.findViewById(R.id.removeProfilePhoto).performClick());
            screen.recreate();
            awaitScreen(screen, activity -> activity.findViewById(R.id.removeProfilePhoto).getVisibility() == View.GONE);
        } // Cancel removal.
        assertTrue(oldPhoto.isFile());
        assertEquals(original.image_path, db.ProfileDao().getProfileByProfileID(original.profileID).image_path);
        try (ActivityScenario<editProfileActivity> screen = edit(original.profileID)) {
            choose(screen, replacement, false);
            awaitPhoto(screen, PURPLE);
            screen.onActivity(activity -> activity.findViewById(R.id.saveEntryButton).performClick());
            await(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        Profile replaced = db.ProfileDao().getProfileByProfileID(original.profileID);
        assertNotEquals(original.image_path, replaced.image_path);
        File newPhoto = ProfilePhotoStore.resolve(context, replaced.image_path);
        assertTrue(newPhoto.isFile());
        await(() -> !oldPhoto.exists());
        try (ActivityScenario<editProfileActivity> screen = edit(original.profileID)) {
            awaitPhoto(screen, PURPLE);
            screen.onActivity(activity -> {
                activity.findViewById(R.id.removeProfilePhoto).performClick();
                activity.findViewById(R.id.saveEntryButton).performClick();
            });
            await(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        assertEquals("", db.ProfileDao().getProfileByProfileID(original.profileID).image_path);
        await(() -> !newPhoto.exists());
        try (ActivityScenario<DashboardActivity> screen = ActivityScenario.launch(DashboardActivity.class)) {
            awaitScreen(screen, activity -> rowImage(activity) != null
                    && !(rowImage(activity).getDrawable() instanceof BitmapDrawable));
        }
        Profile toDelete = storedProfile(importPhoto(replacement));
        File deletedPhoto = ProfilePhotoStore.resolve(context, toDelete.image_path);
        try (ActivityScenario<editProfileActivity> screen = edit(toDelete.profileID)) {
            screen.onActivity(activity -> activity.findViewById(R.id.deleteEntryButton).performClick());
            await(() -> screen.getState() == Lifecycle.State.DESTROYED);
        }
        assertNull(db.ProfileDao().getProfileByProfileID(toDelete.profileID));
        await(() -> !deletedPhoto.exists());
    }

    @Test public void cancelledAndInvalidPickerResultsKeepTheCurrentPhoto() throws Exception {
        Profile original = storedProfile(importPhoto(fixture(TEAL, false, false)));
        try (ActivityScenario<editProfileActivity> screen = edit(original.profileID)) {
            awaitPhoto(screen, TEAL);
            choose(screen, null, true);
            awaitPhoto(screen, TEAL);
            choose(screen, fixture(PURPLE, false, true), false);
            awaitScreen(screen, activity -> activity.findViewById(R.id.saveEntryButton).isEnabled());
            awaitPhoto(screen, TEAL);
        }
        assertEquals(original.image_path, db.ProfileDao().getProfileByProfileID(original.profileID).image_path);
        assertTrue(ProfilePhotoStore.resolve(context, original.image_path).exists());
    }

    @Test public void importResizesRotatesAndOnlyCleansUnreferencedPhotos() throws Exception {
        String path = importPhoto(fixture(TEAL, true, false));
        File photo = ProfilePhotoStore.resolve(context, path);
        Bitmap image = BitmapFactory.decodeFile(photo.getAbsolutePath());
        assertEquals(480, image.getWidth());
        assertEquals(768, image.getHeight());
        image.recycle();
        assertTrue(photo.length() < 512 * 1024);
        assertNull(ProfilePhotoStore.resolve(context, "../prison_app_db"));
        Profile a = storedProfile(path), b = storedProfile(path);
        db.ProfileDao().deleteUser(a);
        ProfilePhotoStore.deleteIfUnused(context, path);
        assertTrue(photo.isFile());
        db.ProfileDao().deleteUser(b);
        ProfilePhotoStore.deleteIfUnused(context, path);
        assertFalse(photo.exists());
    }

    @Test public void restoredDraftAndRecycledRowsKeepTheRightPhoto() throws Exception {
        String path = importPhoto(fixture(PURPLE, false, false));
        Profile stored = storedProfile(path);
        instrumentation.runOnMainSync(() -> {
            SavedStateHandle handle = new SavedStateHandle(Collections.singletonMap("profile_photo_draft", ""));
            ProfilePhotoViewModel model = new ProfilePhotoViewModel(
                    (android.app.Application) context.getApplicationContext(), handle);
            model.initialize(path);
            assertEquals("", model.state.getValue().path); // Restored pending removal beats the saved photo.
            model.onCleared();
        });
        try (ActivityScenario<DashboardActivity> screen = ActivityScenario.launch(DashboardActivity.class)) {
            awaitScreen(screen, activity -> hasColor(rowImage(activity), PURPLE));
            screen.onActivity(activity -> {
                ArrayList<profileModel> models = new ArrayList<>();
                models.add(rowModel(stored.profileID, path));
                models.add(rowModel(0, ""));
                Profile_RecyclerViewAdapter adapter = new Profile_RecyclerViewAdapter(activity, models);
                RecyclerView parent = activity.findViewById(R.id.profileRecyclerView);
                Profile_RecyclerViewAdapter.MyViewHolder holder = adapter.onCreateViewHolder(parent, 0);
                ProfilePhotoLoader.evict(path);
                adapter.onBindViewHolder(holder, 0);
                adapter.onBindViewHolder(holder, 1);
                assertFalse(holder.imageView.getDrawable() instanceof BitmapDrawable);
                // Attach the reused row so we can inspect the late async result below.
                activity.findViewById(R.id.main).setTag(holder.imageView);
            });
            SystemClock.sleep(500);
            screen.onActivity(activity -> assertFalse(((ImageView) activity.findViewById(R.id.main).getTag())
                    .getDrawable() instanceof BitmapDrawable));
        }
    }

    @Test public void nativePickerOpensAndBackLeavesThePlaceholder() {
        try (ActivityScenario<newEntryActivity> screen = ActivityScenario.launch(newEntryActivity.class)) {
            screen.onActivity(activity -> activity.findViewById(R.id.profileImage).performClick());
            await(() -> {
                android.view.accessibility.AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
                return root != null && root.getPackageName() != null
                        && !context.getPackageName().contentEquals(root.getPackageName());
            });
            assertTrue(instrumentation.getUiAutomation().performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK));
            await(() -> screen.getState() == Lifecycle.State.RESUMED);
            screen.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.removeProfilePhoto).getVisibility());
                assertTrue(activity.findViewById(R.id.saveEntryButton).isEnabled());
            });
        }
    }

    @Test public void missingPhotoFallsBackAndFailedSaveRetainsDraftForRetry() throws Exception {
        String path = importPhoto(fixture(TEAL, false, false));
        Profile owner = storedProfile(path);
        File photo = ProfilePhotoStore.resolve(context, path);
        assertTrue(photo.delete());
        ProfilePhotoLoader.evict(path);
        try (ActivityScenario<editProfileActivity> screen = edit(owner.profileID)) {
            awaitScreen(screen, activity -> !( ((ImageView) activity.findViewById(R.id.profileImage))
                    .getDrawable() instanceof BitmapDrawable));
            choose(screen, fixture(PURPLE, false, false), false);
            awaitPhoto(screen, PURPLE);
            // Force an update failure without damaging another profile or its photo.
            db.ProfileDao().deleteUser(owner);
            screen.onActivity(activity -> activity.findViewById(R.id.saveEntryButton).performClick());
            awaitPhoto(screen, PURPLE);
            assertEquals(Lifecycle.State.RESUMED, screen.getState());
        }
        assertNull(db.ProfileDao().getProfileByProfileID(owner.profileID));
    }

    private profileModel rowModel(int id, String path) {
        return new profileModel(id, "Photo", "Example", "2018-01-10", "2018-06-20", false,
                "", "", 0, 0, "", path);
    }

    private ActivityScenario<editProfileActivity> edit(int id) {
        return ActivityScenario.launch(new Intent(context, editProfileActivity.class).putExtra("profileID", id));
    }

    private Profile storedProfile(String path) {
        Profile profile = new Profile();
        profile.userID = user.userID;
        profile.firstName = "Photo";
        profile.lastName = "Example";
        profile.leaArrestDate = "2018-01-10";
        profile.commitmentDate = "2018-06-20";
        profile.minYears = 10;
        profile.maxYears = 15;
        profile.image_path = path;
        profile.profileID = (int) db.ProfileDao().insertProfile(profile);
        return profile;
    }

    private String importPhoto(Uri source) throws Exception {
        String path = ProfilePhotoStore.importPhoto(context, source);
        importedPaths.add(path);
        return path;
    }

    private Uri fixture(int color, boolean rotate, boolean corrupt) throws Exception {
        File file = File.createTempFile("photo-fixture-", ".jpg", context.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (corrupt) out.write(new byte[]{1, 2, 3});
                else {
                    Bitmap image = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(image);
                    canvas.drawColor(color);
                    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(800, 330, 145, paint);
                    canvas.drawOval(490, 540, 1110, 1110, paint);
                    assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 95, out));
                    image.recycle();
                }
            }
            if (rotate) {
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                exif.saveAttributes();
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "photo-test-" + UUID.randomUUID() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            assertNotNull(uri);
            fixtures.add(uri);
            try (OutputStream output = context.getContentResolver().openOutputStream(uri);
                 FileInputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, values, null, null);
            return uri;
        } finally { file.delete(); }
    }

    private <A extends Activity> void choose(ActivityScenario<A> screen, Uri uri, boolean cancel) {
        String action = new ActivityResultContracts.PickVisualMedia().createIntent(context,
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()).getAction();
        AtomicBoolean launched = new AtomicBoolean();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (!action.equals(intent.getAction())) return null;
                launched.set(true);
                return new Instrumentation.ActivityResult(cancel ? Activity.RESULT_CANCELED : Activity.RESULT_OK,
                        uri == null ? null : new Intent().setData(uri));
            }
        };
        instrumentation.addMonitor(monitor);
        try { screen.onActivity(activity -> activity.findViewById(R.id.profileImage).performClick()); }
        finally { instrumentation.removeMonitor(monitor); }
        assertTrue("Photo tap must launch Android's picker", launched.get());
        instrumentation.waitForIdleSync();
    }

    private static void set(Activity activity, int id, String value) {
        ((EditText) activity.findViewById(id)).setText(value);
    }

    private ImageView rowImage(DashboardActivity activity) {
        RecyclerView list = activity.findViewById(R.id.profileRecyclerView);
        RecyclerView.ViewHolder row = list.findViewHolderForAdapterPosition(0);
        return row == null ? null : row.itemView.findViewById(R.id.imageView);
    }

    private boolean hasColor(ImageView view, int expected) {
        if (view == null || !(view.getDrawable() instanceof BitmapDrawable)) return false;
        Bitmap bitmap = ((BitmapDrawable) view.getDrawable()).getBitmap();
        int actual = bitmap.getPixel(0, 0);
        return Math.abs(Color.red(actual) - Color.red(expected)) < 15
                && Math.abs(Color.green(actual) - Color.green(expected)) < 15
                && Math.abs(Color.blue(actual) - Color.blue(expected)) < 15;
    }

    private <A extends Activity> void awaitPhoto(ActivityScenario<A> screen, int color) {
        awaitScreen(screen, activity -> hasColor(activity.findViewById(R.id.profileImage), color)
                && activity.findViewById(R.id.saveEntryButton).isEnabled());
    }

    private <A extends Activity> void awaitScreen(ActivityScenario<A> screen, Predicate<A> ready) {
        await(() -> {
            AtomicBoolean result = new AtomicBoolean();
            screen.onActivity(activity -> result.set(ready.test(activity)));
            return result.get();
        });
    }

    private void await(BooleanSupplier ready) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (ready.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for photo state");
    }

    private void screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().waitForIdle(500, 5000);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir("test-screenshots"), name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
