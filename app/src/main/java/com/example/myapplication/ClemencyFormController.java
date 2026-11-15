package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.db.Profile;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** Shared by new/edit entry; the selection is explicit and independent of other triggers. */
final class ClemencyFormController {
    private final AppCompatActivity activity;
    private final MaterialSwitch convicted;
    private final View details;
    private final TextView choice;
    private ClemencyCategory category = ClemencyCategory.NONE;

    ClemencyFormController(AppCompatActivity activity) {
        this.activity = activity;
        convicted = activity.findViewById(R.id.convictedSwitch);
        details = activity.findViewById(R.id.clemencyDetails);
        choice = activity.findViewById(R.id.clemencyCategory);
        convicted.setSaveEnabled(false); choice.setSaveEnabled(false);
        convicted.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) category = ClemencyCategory.NONE;
            refresh();
        });
        activity.getSupportFragmentManager().setFragmentResultListener(ClemencyCategoryDialog.RESULT, activity, (key, result) -> {
            if (convicted.isChecked()) category = ClemencyCategory.parse(result.getString("category"));
            refresh();
        });
        choice.setOnClickListener(v -> {
            if (!convicted.isChecked() || activity.getSupportFragmentManager().isStateSaved()
                    || activity.getSupportFragmentManager().findFragmentByTag(ClemencyCategoryDialog.TAG) != null) return;
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.trigger_warning_title)
                    .setMessage(R.string.trigger_warning_clemency)
                    .setNegativeButton(R.string.trigger_warning_cancel, null)
                    .setPositiveButton(R.string.trigger_warning_confirm, (dialog, which) -> {
                        if (convicted.isChecked() && !activity.getSupportFragmentManager().isStateSaved())
                            ClemencyCategoryDialog.create(category).show(activity.getSupportFragmentManager(), ClemencyCategoryDialog.TAG);
                    }).show();
        });
        refresh();
    }

    void load(Profile profile, Bundle state) {
        boolean enabled = state != null ? state.getBoolean("convicted") : profile != null && profile.convicted;
        convicted.setChecked(enabled);
        category = enabled ? ClemencyCategory.parse(state != null ? state.getString("clemencyCategory")
                : profile == null ? null : profile.clemencyCategory) : ClemencyCategory.NONE;
        refresh();
    }
    void save(Bundle state) {
        state.putBoolean("convicted", convicted.isChecked());
        state.putString("clemencyCategory", category.name());
    }
    void write(Profile profile) {
        profile.convicted = convicted.isChecked();
        profile.clemencyCategory = profile.convicted ? category.name() : ClemencyCategory.NONE.name();
    }
    private void refresh() {
        details.setVisibility(convicted.isChecked() ? View.VISIBLE : View.GONE);
        choice.setText(activity.getResources().getStringArray(R.array.clemency_categories)[category.ordinal()]);
        choice.setContentDescription(activity.getString(R.string.clemency_category_title) + ": " + choice.getText());
    }
}
