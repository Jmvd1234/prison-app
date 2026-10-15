package com.example.myapplication;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Two separate confirmations; only the final dialog can submit a deletion. */
public final class DeleteLoginProfileDialogFragment extends DialogFragment {
    public static final String TAG = "delete_login_profile";
    private static final String ID = "id", NAME = "name", LOCKED = "locked", FINAL = "final";
    private LoginProfileViewModel model;
    private EditText password;
    private TextView error;
    private View progress;

    public static DeleteLoginProfileDialogFragment create(int id, String name, boolean locked) {
        DeleteLoginProfileDialogFragment dialog = new DeleteLoginProfileDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ID, id); args.putString(NAME, name); args.putBoolean(LOCKED, locked);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(LoginProfileViewModel.class);
        boolean finalStep = requireArguments().getBoolean(FINAL);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View content = getLayoutInflater().cloneInContext(builder.getContext())
                .inflate(R.layout.dialog_delete_login_profile, null);
        ((TextView) content.findViewById(R.id.deleteLoginMessage)).setText(getString(finalStep
                ? R.string.login_delete_final_message : R.string.login_delete_first_message,
                requireArguments().getString(NAME)));
        password = content.findViewById(R.id.deleteLoginPassword);
        error = content.findViewById(R.id.deleteLoginError);
        progress = content.findViewById(R.id.deleteLoginProgress);
        content.findViewById(R.id.deleteLoginPasswordFields).setVisibility(finalStep
                && requireArguments().getBoolean(LOCKED) ? View.VISIBLE : View.GONE);
        password.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                password.setError(null);
                if (model.current().error != 0) model.clearError();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        builder.setTitle(finalStep ? getString(R.string.login_delete_final_title)
                        : getString(R.string.login_delete_first_title, requireArguments().getString(NAME)))
                .setView(content)
                .setNegativeButton(R.string.login_cancel, null)
                .setPositiveButton(finalStep ? R.string.login_delete_action : R.string.login_delete_continue, null);
        model.state().observe(this, this::render);
        return builder.create();
    }

    @Override public void onStart() {
        super.onStart();
        AlertDialog dialog = (AlertDialog) requireDialog();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (model.current().busy || getParentFragmentManager().isStateSaved()) return;
            if (requireArguments().getBoolean(FINAL)) {
                model.delete(requireArguments().getInt(ID), password.getText().toString());
            } else {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                DeleteLoginProfileDialogFragment next = new DeleteLoginProfileDialogFragment();
                Bundle args = new Bundle(requireArguments()); args.putBoolean(FINAL, true);
                next.setArguments(args);
                next.show(getParentFragmentManager().beginTransaction().remove(this), TAG);
            }
        });
        if (requireArguments().getBoolean(FINAL))
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(requireContext().getColor(R.color.laya_danger));
        render(model.current());
    }

    private void render(LoginProfileViewModel.State state) {
        if (password == null) return;
        password.setEnabled(!state.busy);
        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        setCancelable(!state.busy);
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null && dialog.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!state.busy);
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(!state.busy);
        }
        boolean failed = state.error != 0 && state.errorTarget.equals("delete:" + requireArguments().getInt(ID));
        error.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (failed) {
            error.setText(state.error);
            if (state.passwordError) password.setError(getString(state.error));
        }
    }
}
