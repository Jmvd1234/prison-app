package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.myapplication.db.Profile;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Shared custody dates and website-style triggers for Add and Edit Entry. */
public final class TriggerFormController {
    private static final String KEY = "sentence_triggers_v1";
    private static final String JAIL_COMMITMENT = "Jail Commitment Date";
    private static final String PRISON_TRANSFER = "Prison Commitment Date";
    private final AppCompatActivity activity;
    private final MaterialSwitch notJail, cpi, ta;
    private final EditText commitment, prisonTransfer;
    private final View clearPrisonTransfer;
    private final TextView prisonTransferLabel;
    private final TextView commitmentLabel;
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
        prisonTransfer = required(R.id.inputPrisonTransferDate);
        clearPrisonTransfer = required(R.id.clearPrisonTransferDate);
        prisonTransferLabel = required(R.id.prisonTransferLabel);
        clearPrisonTransfer.setOnClickListener(v -> {
            prisonTransfer.setText("");
            prisonTransfer.setError(null);
        });
        stalInput = activity.findViewById(R.id.inputSTAL);
        container.removeAllViews();
        container.setVisibility(View.GONE);
        // Restore these together from our Bundle, not independently from View state.
        for (View view : new View[]{notJail, cpi, ta, commitment, prisonTransfer}) {
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
        commitment.setOnClickListener(v -> pickDate(commitment, JAIL_COMMITMENT));
        prisonTransfer.setOnClickListener(v -> pickDate(prisonTransfer, PRISON_TRANSFER));
    }

    private void confirm(MaterialSwitch target, boolean forCpi) {
        binding = true;
        target.setChecked(false);
        binding = false;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.trigger_warning_title)
                .setMessage(forCpi ? R.string.trigger_warning_cpi : R.string.trigger_warning_time_allowances)
                .setNegativeButton(R.string.trigger_warning_cancel, (dialog, which) -> refreshState())
                .setPositiveButton(R.string.trigger_warning_confirm, (dialog, which) -> {
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
            commitment.setText(ProfileCalculationMapper.jailInputDate(p));
            prisonTransfer.setText(ProfileCalculationMapper.prisonInputDate(p));
        }
        Bundle state = savedInstanceState == null ? null : savedInstanceState.getBundle(KEY);
        if (state != null) {
            notJail.setChecked(state.getBoolean("notJail"));
            cpi.setChecked(state.getBoolean("cpi"));
            ta.setChecked(state.getBoolean("ta"));
            String oldDateKey = prisonMode() ? "prison" : "jail";
            String savedCommitment = safe(state.getString("commitment", state.getString(oldDateKey)));
            if (state.getBoolean("separateCustodyDates") || !prisonMode()) {
                commitment.setText(savedCommitment);
                prisonTransfer.setText(safe(state.getString("prisonTransfer")));
            } else {
                // Move an old relabelled-field draft into its actual prison input.
                commitment.setText("");
                prisonTransfer.setText(savedCommitment);
            }
        }
        binding = false;
        refreshState();
        rebindPicker(commitment, JAIL_COMMITMENT);
        rebindPicker(prisonTransfer, PRISON_TRANSFER);
        rebindPicker(prisonTransfer, "Prison Commitment Date, if any");
    }

    private boolean prisonMode() {
        return SentenceCalculator.usesPrisonCommitment(notJail.isChecked(), cpi.isChecked());
    }

    private void refreshState() {
        binding = true;
        if (cpi.isChecked()) ta.setChecked(true);
        ta.setEnabled(!cpi.isChecked());
        boolean jailEnabled = !notJail.isChecked();
        String jailLabel = cpi.isChecked() && jailEnabled
                ? activity.getString(R.string.jail_commitment_optional) : JAIL_COMMITMENT;
        commitmentLabel.setText(jailLabel);
        commitment.setContentDescription(jailLabel);
        commitment.setEnabled(jailEnabled);
        commitmentLabel.setAlpha(jailEnabled ? 1f : 0.5f);
        commitment.setAlpha(jailEnabled ? 1f : 0.5f);
        if (!jailEnabled) commitment.setError(null);
        // CPI keeps jail optional even though it also forces the TA switch on.
        prisonTransfer.setEnabled(true);
        clearPrisonTransfer.setEnabled(true);
        prisonTransferLabel.setAlpha(1f);
        prisonTransfer.setAlpha(1f);
        String prisonLabel = prisonMode() ? PRISON_TRANSFER
                : activity.getString(R.string.prison_transfer_label);
        prisonTransferLabel.setText(prisonLabel);
        prisonTransfer.setContentDescription(prisonLabel);
        if (stalInput != null) stalInput.setEnabled(true);
        binding = false;
    }

    public void writeToProfile(Profile p) {
        String jailText = text(commitment), prisonText = text(prisonTransfer);
        LocalDate jail = notJail.isChecked() ? null : cpi.isChecked()
                ? SentenceCalculator.optionalDate(jailText, JAIL_COMMITMENT)
                : SentenceCalculator.requiredDate(jailText, JAIL_COMMITMENT);
        LocalDate prison = prisonMode()
                ? SentenceCalculator.requiredDate(prisonText, PRISON_TRANSFER)
                : SentenceCalculator.optionalDate(prisonText, PRISON_TRANSFER);
        LocalDate today = LocalDate.now();
        LocalDate arrest = SentenceCalculator.optionalDate(text(required(R.id.inputLEADate)), "LEA arrest date");
        if (arrest != null && arrest.isAfter(today)) throw new IllegalArgumentException("LEA arrest cannot be after today.");
        validateCustodyDate(jail, arrest, today, "Jail commitment");
        validateCustodyDate(prison, arrest, today, "Prison commitment");
        if (jail != null) SentenceCalculator.validatePrisonTransfer(jail, prison, today);

        p.notCommittedToJail = notJail.isChecked();
        p.disqualifiedForCPI = cpi.isChecked();
        p.disqualifiedForTimeAllowances = cpi.isChecked() || ta.isChecked();
        p.separateCustodyDates = true;
        // CPI uses prison as its calculation anchor, even when an earlier jail date is recorded.
        p.commitmentDate = prisonMode() ? prisonText : jailText;
        p.jailCommitmentDate = jailText.isEmpty() ? null : jailText;
        p.prisonCommitmentDate = prisonText.isEmpty() ? null : prisonText;
        p.prisonTransferDate = prisonText.isEmpty() ? null : prisonText;
        p.convictionDate = null; // Retired UI input; TA's allowance clock now uses the prison date.
    }

    private static void validateCustodyDate(LocalDate date, LocalDate arrest, LocalDate today, String label) {
        if (date == null) return;
        if (date.isAfter(today)) throw new IllegalArgumentException(label + " cannot be after today.");
        if (arrest != null && date.isBefore(arrest)) throw new IllegalArgumentException(label + " cannot precede arrest.");
    }

    public void saveInstanceState(Bundle outState) {
        Bundle state = new Bundle();
        state.putBoolean("separateCustodyDates", true);
        state.putBoolean("notJail", notJail.isChecked());
        state.putBoolean("cpi", cpi.isChecked());
        state.putBoolean("ta", ta.isChecked());
        state.putString("commitment", text(commitment));
        state.putString("prisonTransfer", text(prisonTransfer));
        outState.putBundle(KEY, state);
    }

    private void pickDate(EditText target, String title) {
        if (!target.isEnabled()) return;
        String tag = "TRIGGER_DATE_" + title;
        if (activity.getSupportFragmentManager().isStateSaved()
                || activity.getSupportFragmentManager().findFragmentByTag(tag) != null) return;
        long selection = MaterialDatePicker.todayInUtcMilliseconds();
        try {
            LocalDate current = SentenceCalculator.optionalDate(text(target), title);
            if (current != null) selection = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (IllegalArgumentException ignored) { }
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(title).setSelection(selection);
        {
            long today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            List<CalendarConstraints.DateValidator> validators = new ArrayList<>();
            validators.add(DateValidatorPointBackward.before(today));
            try {
                LocalDate earliest = SentenceCalculator.optionalDate(
                        text(required(R.id.inputLEADate)), "LEA arrest date");
                if (target == prisonTransfer && !notJail.isChecked()) {
                    LocalDate jail = SentenceCalculator.optionalDate(text(commitment), JAIL_COMMITMENT);
                    if (jail != null && (earliest == null || jail.plusDays(1).isAfter(earliest))) earliest = jail.plusDays(1);
                }
                if (earliest != null) {
                    validators.add(DateValidatorPointForward.from(earliest.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()));
                }
            } catch (IllegalArgumentException ignored) { }
            builder.setCalendarConstraints(new CalendarConstraints.Builder()
                    .setEnd(today).setValidator(CompositeDateValidator.allOf(validators)).build());
        }
        MaterialDatePicker<Long> picker = builder.build();
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
