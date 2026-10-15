package com.example.myapplication;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Popup shell; table contents are shared through view_sentence_breakdown.xml. */
public class ProfileBreakdownDialogFragment extends DialogFragment {

    public static final String TAG = "profile_breakdown";

    // Keep the save-result API while sharing rendering with BreakdownActivity.
    public static Bundle createArguments(String minimum, String maximum,
            SentenceCalculator.Result result) {
        return BreakdownViewBinder.createArguments(minimum, maximum, result);
    }

    public static ProfileBreakdownDialogFragment newInstance(Bundle arguments) {
        ProfileBreakdownDialogFragment fragment = new ProfileBreakdownDialogFragment();
        fragment.setArguments(new Bundle(arguments));
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        // Keep the standard rounded dialog shape while matching the app's LAYA palette.
        if (builder.getBackground() instanceof MaterialShapeDrawable) {
            ((MaterialShapeDrawable) builder.getBackground()).setFillColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.laya_surface)));
        }
        View content = getLayoutInflater().cloneInContext(builder.getContext())
                .inflate(R.layout.dialog_profile_breakdown, null);

        BreakdownViewBinder.bind(content, requireArguments());
        content.findViewById(R.id.closeBreakdownButton).setOnClickListener(v -> dismiss());

        return builder.setView(content).create();
    }

}
