package com.example.myapplication;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Editable XML month/year popup, matching LAYA's mobile Add Month form. */
public final class AddDeductionMonthDialog extends DialogFragment {
    static final String TAG = "add_deduction_month", RESULT = "deduction_month_added";
    private Spinner month;
    private EditText year;
    private TextView preview, error;
    private View add;
    private LocalDate start;
    private ArrayList<String> existing;

    static AddDeductionMonthDialog create(LocalDate start, List<DeductionMonth> rows) {
        AddDeductionMonthDialog dialog = new AddDeductionMonthDialog();
        Bundle args = new Bundle(); args.putString("start", start.toString());
        ArrayList<String> keys = new ArrayList<>();
        for (DeductionMonth row : rows) keys.add(row.date().toString());
        args.putStringArrayList("existing", keys); dialog.setArguments(args); return dialog;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        start = LocalDate.parse(requireArguments().getString("start"));
        existing = requireArguments().getStringArrayList("existing");
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        View content = getLayoutInflater().cloneInContext(builder.getContext()).inflate(R.layout.dialog_deduction_month, null);
        month = content.findViewById(R.id.deductionDialogMonth);
        year = content.findViewById(R.id.deductionDialogYear);
        preview = content.findViewById(R.id.deductionDialogPreview);
        error = content.findViewById(R.id.deductionDialogError);
        add = content.findViewById(R.id.deductionDialogAdd);
        if (savedInstanceState != null) {
            month.setSelection(savedInstanceState.getInt("month"));
            year.setText(savedInstanceState.getString("year", ""));
        }
        content.findViewById(R.id.deductionDialogClose).setOnClickListener(v -> dismiss());
        content.findViewById(R.id.deductionDialogCancel).setOnClickListener(v -> dismiss());
        add.setOnClickListener(v -> {
            if (!validate()) return;
            Bundle result = new Bundle(); result.putInt("month", month.getSelectedItemPosition());
            result.putInt("year", Integer.parseInt(year.getText().toString()));
            getParentFragmentManager().setFragmentResult(RESULT, result); dismiss();
        });
        month.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int i, long id) { validate(); }
            @Override public void onNothingSelected(AdapterView<?> p) { validate(); }
        });
        year.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { validate(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        validate();
        return builder.setView(content).create();
    }

    private boolean validate() {
        add.setEnabled(false); preview.setVisibility(View.GONE); error.setVisibility(View.GONE);
        int selected = month.getSelectedItemPosition();
        String value = year.getText().toString();
        if (selected == 0 || value.isEmpty()) return false;
        int number;
        try { number = Integer.parseInt(value); }
        catch (NumberFormatException invalid) { return invalid(R.string.deduction_year_invalid); }
        if (number < 1 || number > 9999) return invalid(R.string.deduction_year_invalid);
        YearMonth date = YearMonth.of(number, selected);
        long position = ChronoUnit.MONTHS.between(YearMonth.from(start), date) + 1;
        if (position < 1) return invalid(R.string.deduction_before_start);
        if (position > 2400) return invalid(R.string.deduction_range_invalid);
        if (existing.contains(date.toString())) return invalid(R.string.deduction_duplicate);
        int tier = position <= 24 ? 1 : position <= 60 ? 2 : position <= 120 ? 3 : 4;
        preview.setText(getString(R.string.deduction_preview,
                date.format(DateTimeFormatter.ofPattern("MMM uuuu", Locale.US)), position, tier, SentenceCalculator.gctaRate(position)));
        preview.setVisibility(View.VISIBLE); add.setEnabled(true); return true;
    }
    private boolean invalid(int message) { error.setText(message); error.setVisibility(View.VISIBLE); return false; }

    @Override public void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("month", month.getSelectedItemPosition()); out.putString("year", year.getText().toString());
        super.onSaveInstanceState(out);
    }
}
