package com.example.myapplication;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.db.Profile;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Shared website-style triggers; exactly one optional conviction field. */
public final class TriggerFormController {
    private static final String KEY = "sentence_triggers_v1";
    private static final String CONVICTION = "Date of conviction, if any";
    private final AppCompatActivity activity;
    private final MaterialSwitch notJail, cpi, ta;
    private final EditText commitment, conviction;
    private final TextView commitmentLabel;
    private final LinearLayout details;
    private final View stalInput;
    private boolean binding = true;

    public TriggerFormController(AppCompatActivity activity, LinearLayout container) {
        if (container == null) throw new IllegalArgumentException("Missing triggerDetailsContainer.");
        this.activity = activity;
        notJail = required(R.id.committedSwitch);
        cpi = required(R.id.disqualifiedCPISwitch);
        ta = required(R.id.disqualifiedTASwitch);
        commitment = required(R.id.inputCommitmentDate);
        commitmentLabel = required(R.id.commitmentDateLabel);
        stalInput = activity.findViewById(R.id.inputSTAL);
        details = container;
        details.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(activity);
        label.setText(CONVICTION);
        details.addView(label);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        conviction = new EditText(activity);
        conviction.setHint("Select date");
        conviction.setContentDescription(CONVICTION);
        conviction.setInputType(InputType.TYPE_NULL);
        conviction.setFocusable(false);
        conviction.setClickable(true);
        row.addView(conviction, new LinearLayout.LayoutParams(0, -2, 1));
        Button clear = new Button(activity);
        clear.setText("Clear");
        clear.setContentDescription("Clear conviction date");
        clear.setOnClickListener(v -> conviction.setText(""));
        row.addView(clear);
        details.addView(row);
        details.setVisibility(View.GONE);
        // Restore these together from our Bundle, not independently from View state.
        for (View view : new View[]{notJail, cpi, ta, commitment, conviction}) {
            view.setSaveEnabled(false);
        }
        notJail.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) refreshState();
        });
        cpi.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked) confirm(cpi, true); else refreshState();
        });
        ta.setOnCheckedChangeListener((button, checked) -> {
            if (binding) return;
            if (checked && !cpi.isChecked()) confirm(ta, false); else refreshState();
        });
        commitment.setOnClickListener(v -> pickDate(commitment, commitmentLabel.getText().toString()));
        conviction.setOnClickListener(v -> pickDate(conviction, CONVICTION));
    }

    private void confirm(MaterialSwitch target, boolean forCpi) {
        binding = true;
        target.setChecked(false);
        binding = false;
        String message = forCpi
                ? "Confirm CPI disqualification from the court record before continuing. "
                    + "This also selects Time Allowance disqualification and changes the date field "
                    + "to Prison Commitment Date."
                : "Confirm time-allowance disqualification from the court record before continuing. "
                    + "If there is a conviction, enter its date in the optional field.";
        new MaterialAlertDialogBuilder(activity)
                .setTitle(forCpi ? "Confirm CPI disqualification" : "Confirm Time Allowance disqualification")
                .setMessage(message)
                .setNegativeButton("Cancel", (dialog, which) -> refreshState())
                .setPositiveButton("Confirmed", (dialog, which) -> {
                    binding = true;
                    target.setChecked(true);
                    binding = false;
                    refreshState();
                })
                .setOnCancelListener(dialog -> refreshState())
                .show();
    }

    /** Call after ordinary form fields are populated. */
    public void load(Profile p, Bundle savedInstanceState) {
        binding = true;
        if (p != null) {
            notJail.setChecked(p.notCommittedToJail);
            cpi.setChecked(p.disqualifiedForCPI);
            ta.setChecked(p.disqualifiedForTimeAllowances);
            commitment.setText(ProfileCalculationMapper.commitmentDate(p));
            conviction.setText(safe(p.convictionDate));
        }
        Bundle state = savedInstanceState == null ? null : savedInstanceState.getBundle(KEY);
        if (state != null) {
            notJail.setChecked(state.getBoolean("notJail"));
            cpi.setChecked(state.getBoolean("cpi"));
            ta.setChecked(state.getBoolean("ta"));
            String oldDateKey = prisonMode() ? "prison" : "jail";
            commitment.setText(safe(state.getString("commitment", state.getString(oldDateKey))));
            conviction.setText(safe(state.getString("conviction", state.getString("finality"))));
        }
        binding = false;
        refreshState();
        rebindPicker(commitment, "Jail Commitment Date");
        rebindPicker(commitment, "Prison Commitment Date");
        rebindPicker(conviction, CONVICTION);
    }

    private boolean prisonMode() {
        return SentenceCalculator.usesPrisonCommitment(notJail.isChecked(), cpi.isChecked());
    }

    private void refreshState() {
        binding = true;
        if (cpi.isChecked()) ta.setChecked(true);
        ta.setEnabled(!cpi.isChecked());
        String label = prisonMode() ? "Prison Commitment Date" : "Jail Commitment Date";
        commitmentLabel.setText(label);
        commitment.setContentDescription(label);
        // Relabel the single field without replacing its entered value, as on the website.
        details.setVisibility(SentenceCalculator.usesConvictionDate(cpi.isChecked(), ta.isChecked())
                ? View.VISIBLE : View.GONE);
        if (stalInput != null) stalInput.setEnabled(true);
        binding = false;
    }

    public void writeToProfile(Profile p) {
        SentenceCalculator.requiredDate(text(commitment), commitmentLabel.getText().toString());
        if (SentenceCalculator.usesConvictionDate(cpi.isChecked(), ta.isChecked())) {
            SentenceCalculator.optionalDate(text(conviction), CONVICTION);
        }
        p.notCommittedToJail = notJail.isChecked();
        p.disqualifiedForCPI = cpi.isChecked();
        p.disqualifiedForTimeAllowances = cpi.isChecked() || ta.isChecked();
        p.commitmentDate = text(commitment);
        if (prisonMode()) p.prisonCommitmentDate = p.commitmentDate;
        else p.jailCommitmentDate = p.commitmentDate;
        // Preserve the optional draft while hidden; the mapper ignores it in other modes.
        p.convictionDate = text(conviction).isEmpty() ? null : text(conviction);
        // Retired metadata stays untouched in Room; no schema change or destructive reset.
    }

    public void saveInstanceState(Bundle outState) {
        Bundle state = new Bundle();
        state.putBoolean("notJail", notJail.isChecked());
        state.putBoolean("cpi", cpi.isChecked());
        state.putBoolean("ta", ta.isChecked());
        state.putString("commitment", text(commitment));
        state.putString("conviction", text(conviction));
        outState.putBundle(KEY, state);
    }

    private void pickDate(EditText target, String title) {
        String tag = "TRIGGER_DATE_" + title;
        if (activity.getSupportFragmentManager().isStateSaved()
                || activity.getSupportFragmentManager().findFragmentByTag(tag) != null) return;
        long selection = MaterialDatePicker.todayInUtcMilliseconds();
        try {
            LocalDate current = SentenceCalculator.optionalDate(text(target), title);
            if (current != null) selection = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (IllegalArgumentException ignored) { }
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(title).setSelection(selection).build();
        connectPicker(picker, target);
        picker.show(activity.getSupportFragmentManager(), tag);
    }

    private void connectPicker(MaterialDatePicker<Long> picker, EditText target) {
        picker.clearOnPositiveButtonClickListeners();
        picker.addOnPositiveButtonClickListener(millis -> {
            LocalDate selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
            target.setText(selected.format(SentenceCalculator.DISPLAY_DATE));
            target.setError(null);
        });
    }

    @SuppressWarnings("unchecked")
    private void rebindPicker(EditText target, String title) {
        androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentByTag("TRIGGER_DATE_" + title);
        if (fragment instanceof MaterialDatePicker<?>) {
            connectPicker((MaterialDatePicker<Long>) fragment, target);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T required(int id) {
        View view = activity.findViewById(id);
        if (view == null) throw new IllegalStateException("Missing required form view: " + id);
        return (T) view;
    }
    private static String text(EditText view) { return view.getText().toString().trim(); }
    private static String safe(String value) { return value == null ? "" : value; }
}
