package com.example.myapplication;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

/** One editable XML form, presented in create-profile or unlock-profile mode. */
public class LoginProfileDialogFragment extends DialogFragment {
    public static final String TAG = "login_profile_dialog";
    private static final String ARG_ID = "user_id", ARG_NAME = "user_name";
    private LoginProfileViewModel model;
    private EditText username, password;
    private MaterialSwitch requirePassword;
    private View passwordFields, progress;
    private TextView error;
    private boolean creating;
    private int userID;

    public static LoginProfileDialogFragment createProfile() { return new LoginProfileDialogFragment(); }
    public static LoginProfileDialogFragment unlockProfile(int id, String name) {
        LoginProfileDialogFragment fragment = new LoginProfileDialogFragment();
        Bundle args = new Bundle(); args.putInt(ARG_ID, id); args.putString(ARG_NAME, name);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        creating = getArguments() == null || !getArguments().containsKey(ARG_ID);
        userID = creating ? -1 : requireArguments().getInt(ARG_ID);
        model = new ViewModelProvider(requireActivity()).get(LoginProfileViewModel.class);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View content = getLayoutInflater().cloneInContext(builder.getContext()).inflate(R.layout.dialog_login_profile, null);
        username = content.findViewById(R.id.loginProfileUsername);
        password = content.findViewById(R.id.loginProfilePassword);
        requirePassword = content.findViewById(R.id.loginRequirePassword);
        passwordFields = content.findViewById(R.id.loginPasswordFields);
        progress = content.findViewById(R.id.loginDialogProgress);
        error = content.findViewById(R.id.loginDialogError);
        content.findViewById(R.id.loginUsernameFields).setVisibility(creating ? View.VISIBLE : View.GONE);
        requirePassword.setVisibility(creating ? View.VISIBLE : View.GONE);
        requirePassword.setChecked(savedInstanceState != null && savedInstanceState.getBoolean("requires_password"));
        if (savedInstanceState != null) username.setText(savedInstanceState.getString("username", ""));
        updatePasswordVisibility();
        requirePassword.setOnCheckedChangeListener((button, checked) -> {
            updatePasswordVisibility();
            clearValidationError();
        });
        TextWatcher edits = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { clearValidationError(); }
            @Override public void afterTextChanged(Editable s) { }
        };
        username.addTextChangedListener(edits);
        password.addTextChangedListener(edits);
        password.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); return true; }
            return false;
        });
        username.setOnEditorActionListener((v, action, event) -> {
            if (!requirePassword.isChecked() && action == EditorInfo.IME_ACTION_DONE) { submit(); return true; }
            return false;
        });
        builder.setTitle(creating ? getString(R.string.login_create_title)
                : getString(R.string.login_unlock_title, requireArguments().getString(ARG_NAME)))
                .setView(content)
                .setNegativeButton(R.string.login_cancel, null)
                .setPositiveButton(creating ? R.string.login_create_action : R.string.login_unlock_action, null);
        model.state().observe(this, this::render);
        return builder.create();
    }

    @Override public void onStart() {
        super.onStart();
        ((AlertDialog) requireDialog()).getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> submit());
        render(model.current());
    }

    private void updatePasswordVisibility() {
        boolean visible = !creating || requirePassword.isChecked();
        passwordFields.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) { password.setText(""); password.setError(null); }
        username.setImeOptions(visible ? EditorInfo.IME_ACTION_NEXT : EditorInfo.IME_ACTION_DONE);
    }

    private void submit() {
        if (model.current().busy) return;
        username.setError(null); password.setError(null);
        if (creating) model.create(username.getText().toString(), requirePassword.isChecked(), password.getText().toString());
        else model.unlock(userID, password.getText().toString());
    }

    private void clearValidationError() {
        username.setError(null);
        password.setError(null);
        if (model.current().error != 0) model.clearError();
    }

    private void render(LoginProfileViewModel.State state) {
        if (username == null) return;
        username.setEnabled(!state.busy); password.setEnabled(!state.busy); requirePassword.setEnabled(!state.busy);
        progress.setVisibility(state.busy ? View.VISIBLE : View.GONE);
        setCancelable(!state.busy);
        AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null && dialog.getButton(DialogInterface.BUTTON_POSITIVE) != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!state.busy);
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(!state.busy);
        }
        String target = creating ? "create" : "unlock:" + userID;
        boolean failed = state.error != 0 && state.errorTarget.equals(target);
        error.setVisibility(failed ? View.VISIBLE : View.GONE);
        if (failed) {
            error.setText(state.error);
            if (state.passwordError) password.setError(getString(state.error));
            else if (creating && state.error != R.string.login_operation_failed) username.setError(getString(state.error));
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("requires_password", requirePassword.isChecked());
        outState.putString("username", username.getText().toString());
        // Password inputs are deliberately excluded from saved instance state.
    }
}
