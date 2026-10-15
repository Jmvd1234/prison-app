package com.example.myapplication;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.db.Profile;
import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

/** One picker and photo editor shared by Add Entry and Edit Entry. */
final class ProfilePhotoController {
    interface OnSaved { void finish(Bundle result); }
    private final ProfilePhotoViewModel model;
    private String displayedPath;

    ProfilePhotoController(AppCompatActivity activity, String existingPath, OnSaved onSaved) {
        model = new ViewModelProvider(activity).get(ProfilePhotoViewModel.class);
        model.initialize(existingPath);
        ImageView image = activity.findViewById(R.id.profileImage);
        View remove = activity.findViewById(R.id.removeProfilePhoto);
        View progress = activity.findViewById(R.id.profilePhotoProgress);
        View save = activity.findViewById(R.id.saveEntryButton);
        View root = activity.findViewById(R.id.main);
        ActivityResultLauncher<PickVisualMediaRequest> picker = activity.registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), model::select);
        image.setOnClickListener(view -> {
            try {
                picker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
            } catch (ActivityNotFoundException error) {
                Snackbar.make(root, R.string.profile_photo_picker_unavailable, Snackbar.LENGTH_LONG).show();
            }
        });
        remove.setOnClickListener(view -> model.remove());
        model.state.observe(activity, state -> {
            if (!Objects.equals(displayedPath, state.path)) {
                displayedPath = state.path;
                ProfilePhotoLoader.show(image, state.path, R.drawable.outline_account_box_24);
            }
            boolean hasPhoto = !state.path.isEmpty();
            image.setContentDescription(activity.getString(hasPhoto
                    ? R.string.profile_photo_replace : R.string.profile_photo_choose));
            image.setEnabled(!state.busy);
            remove.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
            remove.setEnabled(!state.busy);
            progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
            save.setEnabled(!state.busy);
        });
        model.error.observe(activity, message -> {
            if (message == null) return;
            Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
            model.error.setValue(null);
        });
        model.completion.observe(activity, result -> {
            if (result != null && !activity.isFinishing()) onSaved.finish(result);
        });
    }

    void save(Profile profile, boolean insert, Bundle result) { model.save(profile, insert, result); }
}
