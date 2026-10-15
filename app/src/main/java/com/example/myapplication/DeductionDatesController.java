package com.example.myapplication;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.myapplication.db.Profile;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Native counterpart of LAYA's mobile cards, search, pagination and summary. */
final class DeductionDatesController {
    private static final int PAGE_SIZE = 3;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM uuuu", Locale.US);
    private final AppCompatActivity host;
    private final DeductionDatesViewModel model;
    private final View panel;
    private final LinearLayout list;
    private final EditText search;
    private final Consumer<Profile> onSavedProfile;
    private Profile renderedProfile;
    private int page;
    private LocalDate observation = LocalDate.now();

    DeductionDatesController(AppCompatActivity host, int profileID, long owner, Bundle restored,
            Consumer<Profile> onSavedProfile, Consumer<String> onError) {
        this.host = host; this.onSavedProfile = onSavedProfile;
        panel = host.findViewById(R.id.deductionDatesPanel);
        list = panel.findViewById(R.id.deductionMonthsList);
        search = panel.findViewById(R.id.deductionSearch);
        model = new ViewModelProvider(host).get(DeductionDatesViewModel.class);
        if (restored != null) {
            search.setText(restored.getString("deduction_search", "")); page = restored.getInt("deduction_page", 0);
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { page = 0; render(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        panel.findViewById(R.id.deductionClearSearch).setOnClickListener(v -> search.setText(""));
        panel.findViewById(R.id.deductionPrevious).setOnClickListener(v -> { page--; render(); });
        panel.findViewById(R.id.deductionNext).setOnClickListener(v -> { page++; render(); });
        panel.findViewById(R.id.deductionRecalculate).setOnClickListener(v -> model.save());
        panel.findViewById(R.id.deductionAddMonth).setOnClickListener(v -> {
            DeductionDatesViewModel.State state = model.current();
            if (state.profile == null || state.busy || host.getSupportFragmentManager().isStateSaved()
                    || host.getSupportFragmentManager().findFragmentByTag(AddDeductionMonthDialog.TAG) != null) return;
            LocalDate start = DeductionMonth.allowanceStart(ProfileCalculationMapper.from(state.profile));
            if (start != null) AddDeductionMonthDialog.create(start, state.draft)
                    .show(host.getSupportFragmentManager(), AddDeductionMonthDialog.TAG);
        });
        host.getSupportFragmentManager().setFragmentResultListener(AddDeductionMonthDialog.RESULT, host, (key, result) -> {
            List<DeductionMonth> rows = new ArrayList<>(model.current().draft);
            DeductionMonth added = new DeductionMonth(result.getInt("year"), result.getInt("month"), true, true);
            for (DeductionMonth row : rows) if (row.date().equals(added.date())) return;
            rows.add(added); page = 0; search.setText(""); model.change(rows);
        });
        model.state().observe(host, state -> {
            if (host.getSharedPreferences("user_session", 0).getLong("currentUserID", -1) != owner) {
                panel.setVisibility(View.GONE); onError.accept(host.getString(R.string.breakdown_sign_in)); return;
            }
            if (state.profile == null) {
                if (state.error != 0) onError.accept(host.getString(state.error));
                return;
            }
            if (state.profile != renderedProfile) {
                renderedProfile = state.profile; onSavedProfile.accept(state.profile);
            }
            try {
                panel.setVisibility(View.VISIBLE); render();
            } catch (IllegalArgumentException invalidProfile) {
                panel.setVisibility(View.GONE);
                onError.accept(host.getString(R.string.breakdown_calculation_failed, invalidProfile.getMessage()));
            }
        });
        model.initialize(profileID, owner, decode(restored));
    }

    void setObservationDate(LocalDate observation) { this.observation = observation; updateSummary(); }

    private void render() {
        DeductionDatesViewModel.State state = model.current();
        if (state.profile == null) return;
        SentenceCalculator.Input input = ProfileCalculationMapper.from(state.profile, observation);
        LocalDate start = DeductionMonth.allowanceStart(input);
        enabled(R.id.deductionAddMonth, !state.busy && start != null);
        enabled(R.id.deductionRecalculate, !state.busy);
        ((TextView) panel.findViewById(R.id.deductionRecalculate)).setText(state.busy ? R.string.deduction_saving : R.string.deduction_recalculate);
        TextView eligibility = panel.findViewById(R.id.deductionEligibility);
        eligibility.setVisibility(start == null || state.profile.deductionDays > 0 ? View.VISIBLE : View.GONE);
        eligibility.setText(start == null ? host.getString(R.string.deduction_no_allowances)
                : host.getString(R.string.deduction_manual_overridden, state.profile.deductionDays));
        String term = search.getText().toString().trim().toLowerCase(Locale.US);
        panel.findViewById(R.id.deductionClearSearch).setVisibility(term.isEmpty() ? View.GONE : View.VISIBLE);
        List<DeductionMonth> filtered = new ArrayList<>();
        for (DeductionMonth row : state.draft) {
            if (term.isEmpty() || row.date().format(MONTH).toLowerCase(Locale.US).startsWith(term)
                    || Integer.toString(row.year).contains(term)) filtered.add(row);
        }
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        int from = page * PAGE_SIZE, to = Math.min(from + PAGE_SIZE, filtered.size());
        list.removeAllViews();
        for (int i = from; i < to; i++) addCard(filtered.get(i), input, state.busy);
        TextView empty = panel.findViewById(R.id.deductionEmpty);
        empty.setText(state.draft.isEmpty() ? R.string.deduction_none : R.string.deduction_no_matches);
        empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        panel.findViewById(R.id.deductionPagination).setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        text(R.id.deductionShowing, term.isEmpty() ? host.getString(R.string.deduction_showing, from + 1, to, filtered.size())
                : host.getString(R.string.deduction_filtered, from + 1, to, filtered.size(), state.draft.size()));
        text(R.id.deductionPage, host.getString(R.string.deduction_page, page + 1, pages));
        enabled(R.id.deductionPrevious, page > 0);
        enabled(R.id.deductionNext, page + 1 < pages);
        TextView status = panel.findViewById(R.id.deductionSaveStatus);
        int message = state.error != 0 ? state.error : !state.draft.equals(state.profile.deductionMonths)
                ? R.string.deduction_pending : state.saves > 0 ? R.string.deduction_saved : 0;
        status.setVisibility(message == 0 ? View.GONE : View.VISIBLE);
        if (message != 0) status.setText(message);
        status.setTextColor(host.getColor(state.error != 0 ? R.color.laya_danger : R.color.laya_label));
        updateSummary();
    }

    private void addCard(DeductionMonth row, SentenceCalculator.Input input, boolean busy) {
        View card = host.getLayoutInflater().inflate(R.layout.view_deduction_month, list, false);
        card.setTag(row.date().toString());
        DeductionMonth.Details details = row.details(input);
        String label = row.date().format(MONTH);
        ((TextView) card.findViewById(R.id.deductionMonthName)).setText(label);
        ((TextView) card.findViewById(R.id.deductionMonthTier)).setText(details.earnedOn == null
                ? host.getString(R.string.deduction_ineligible) : host.getString(R.string.deduction_tier, details.tier, details.position));
        TextView earned = card.findViewById(R.id.deductionMonthEarned);
        earned.setVisibility(details.earnedOn == null ? View.GONE : View.VISIBLE);
        if (details.earnedOn != null) earned.setText(host.getString(R.string.deduction_earned_on, details.earnedOn.format(SentenceCalculator.DISPLAY_DATE)));
        ((TextView) card.findViewById(R.id.deductionGctaDays)).setText(days(details.gctaDays));
        ((TextView) card.findViewById(R.id.deductionTastmDays)).setText(days(details.tastmDays));
        ((TextView) card.findViewById(R.id.deductionMonthTotal)).setText(days(details.selected(row)));
        CheckBox gcta = card.findViewById(R.id.deductionGcta), tastm = card.findViewById(R.id.deductionTastm);
        gcta.setChecked(row.gcta); tastm.setChecked(row.tastm);
        gcta.setEnabled(!busy); tastm.setEnabled(!busy);
        gcta.setContentDescription(host.getString(R.string.deduction_gcta_check, label));
        tastm.setContentDescription(host.getString(R.string.deduction_tastm_check, label));
        gcta.setOnCheckedChangeListener((button, checked) -> replace(row, new DeductionMonth(row.year, row.month, checked, row.tastm)));
        tastm.setOnCheckedChangeListener((button, checked) -> replace(row, new DeductionMonth(row.year, row.month, row.gcta, checked)));
        View remove = card.findViewById(R.id.deductionRemoveMonth);
        remove.setEnabled(!busy); remove.setContentDescription(host.getString(R.string.deduction_remove, label));
        remove.setOnClickListener(v -> replace(row, null));
        list.addView(card);
    }

    private void replace(DeductionMonth row, DeductionMonth replacement) {
        List<DeductionMonth> rows = new ArrayList<>(model.current().draft);
        int index = rows.indexOf(row);
        if (index < 0) return;
        if (replacement == null) rows.remove(index); else rows.set(index, replacement);
        model.change(rows);
    }

    private void updateSummary() {
        DeductionDatesViewModel.State state = model.current();
        if (state.profile == null) return;
        SentenceCalculator.Input input = ProfileCalculationMapper.from(state.profile, observation);
        input.deductionMonths = state.draft;
        DeductionMonth.Totals amounts = DeductionMonth.totals(input, observation);
        text(R.id.deductionSelectedDays, days(amounts.selected));
        text(R.id.deductionSelectedCount, host.getString(R.string.deduction_count, state.draft.size()));
        long applied = state.draft.isEmpty() ? state.profile.deductionDays : amounts.applied();
        text(R.id.deductionAppliedDays, host.getString(R.string.deduction_applied, NumberFormat.getIntegerInstance().format(applied)));
    }
    private String days(long days) { return host.getString(R.string.breakdown_credit_days, NumberFormat.getIntegerInstance().format(days)); }
    private void text(int id, String value) { ((TextView) panel.findViewById(id)).setText(value); }
    private void enabled(int id, boolean enabled) {
        View control = panel.findViewById(id); control.setEnabled(enabled); control.setAlpha(enabled ? 1f : 0.35f);
    }

    void saveState(Bundle out) {
        out.putString("deduction_search", search.getText().toString()); out.putInt("deduction_page", page);
        if (model.current().profile == null) return;
        List<DeductionMonth> rows = model.current().draft;
        int[] values = new int[rows.size() * 4];
        for (int i = 0; i < rows.size(); i++) {
            DeductionMonth row = rows.get(i);
            values[i * 4] = row.year; values[i * 4 + 1] = row.month;
            values[i * 4 + 2] = row.gcta ? 1 : 0; values[i * 4 + 3] = row.tastm ? 1 : 0;
        }
        out.putIntArray("deduction_draft", values);
    }
    private static List<DeductionMonth> decode(Bundle saved) {
        if (saved == null || !saved.containsKey("deduction_draft")) return null;
        int[] values = saved.getIntArray("deduction_draft");
        List<DeductionMonth> rows = new ArrayList<>();
        if (values != null) for (int i = 0; i + 3 < values.length; i += 4)
            rows.add(new DeductionMonth(values[i], values[i + 1], values[i + 2] != 0, values[i + 3] != 0));
        return rows;
    }
}
