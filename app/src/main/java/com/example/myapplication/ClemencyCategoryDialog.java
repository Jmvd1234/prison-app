package com.example.myapplication;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Single-choice, scrollable picker with wrapped legal descriptions and visible periods. */
public final class ClemencyCategoryDialog extends DialogFragment {
    static final String TAG = "clemency_category_picker", RESULT = "clemency_category_selected";
    static ClemencyCategoryDialog create(ClemencyCategory category) {
        ClemencyCategoryDialog dialog = new ClemencyCategoryDialog();
        Bundle args = new Bundle(); args.putString("category", category.name()); dialog.setArguments(args);
        return dialog;
    }
    @NonNull @Override public Dialog onCreateDialog(Bundle state) {
        String[] choices = getResources().getStringArray(R.array.clemency_categories);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), R.layout.view_clemency_choice, choices) {
            @NonNull @Override public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                row.setText(choices[position]); return row;
            }
        };
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clemency_category_title)
                .setSingleChoiceItems(adapter, ClemencyCategory.parse(requireArguments().getString("category")).ordinal(), (dialog, which) -> {
                    Bundle result = new Bundle(); result.putString("category", ClemencyCategory.values()[which].name());
                    getParentFragmentManager().setFragmentResult(RESULT, result); dismiss();
                })
                .setNegativeButton(R.string.trigger_warning_cancel, null).create();
    }
}
