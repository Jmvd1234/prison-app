package com.example.myapplication;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.text.NumberFormat;

/** Shared display binding for the popup and the full-screen breakdown. */
public final class BreakdownViewBinder {
    private BreakdownViewBinder() { }

    private static final String ARG_MINIMUM = "minimum";
    private static final String ARG_MAXIMUM = "maximum";
    private static final String ARG_LEA = "lea";
    private static final String ARG_JAIL = "jail";
    private static final String ARG_PRISON = "prison";
    private static final String ARG_TOTAL = "total";
    private static final String ARG_STAL_DAYS = "stal_days";
    private static final String ARG_STAL_PERCENT = "stal_percent";

    public static Bundle createArguments(String minimum, String maximum,
            SentenceCalculator.Result result) {
        SentenceCalculator.CustodyBreakdown breakdown = result.custodyBreakdown;
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MINIMUM, minimum);
        arguments.putString(ARG_MAXIMUM, maximum);
        arguments.putLong(ARG_STAL_DAYS, result.stalDays);
        arguments.putInt(ARG_STAL_PERCENT, result.stalPercent);
        arguments.putBundle(ARG_LEA, stageArguments(breakdown.lea));
        arguments.putBundle(ARG_JAIL, stageArguments(breakdown.jail));
        arguments.putBundle(ARG_PRISON, stageArguments(breakdown.prison));
        arguments.putBundle(ARG_TOTAL, stageArguments(breakdown.total));
        return arguments;
    }

    private static Bundle stageArguments(SentenceCalculator.StageCredits stage) {
        Bundle values = new Bundle();
        values.putBoolean("recorded", stage.recorded);
        values.putLong("days", stage.days);
        values.putLong("gcta", stage.gctaDays);
        values.putLong("tastm", stage.tastmDays);
        return values;
    }

    public static void bind(View content, Bundle arguments) {
        ((TextView) content.findViewById(R.id.minimumSentenceValue)).setText(arguments.getString(ARG_MINIMUM));
        ((TextView) content.findViewById(R.id.maximumSentenceValue)).setText(arguments.getString(ARG_MAXIMUM));
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        Context context = content.getContext();
        int percent = arguments.getInt(ARG_STAL_PERCENT);
        ((TextView) content.findViewById(R.id.stalCreditLabel)).setText(percent == 0
                ? context.getString(R.string.breakdown_stal_none)
                : context.getString(R.string.breakdown_stal_percent, percent));
        ((TextView) content.findViewById(R.id.stalCreditValue)).setText(context.getString(
                R.string.breakdown_credit_days, numbers.format(arguments.getLong(ARG_STAL_DAYS))));
        bindRow(content, arguments.getBundle(ARG_LEA), R.string.breakdown_lea,
                R.id.leaDaysValue, R.id.leaGctaValue, R.id.leaTastmValue, numbers);
        bindRow(content, arguments.getBundle(ARG_JAIL), R.string.breakdown_jail,
                R.id.jailDaysValue, R.id.jailGctaValue, R.id.jailTastmValue, numbers);
        bindRow(content, arguments.getBundle(ARG_PRISON), R.string.breakdown_prison,
                R.id.prisonDaysValue, R.id.prisonGctaValue, R.id.prisonTastmValue, numbers);
        bindRow(content, arguments.getBundle(ARG_TOTAL), R.string.breakdown_total,
                R.id.totalDaysValue, R.id.totalGctaValue, R.id.totalTastmValue, numbers);
    }

    private static void bindRow(View content, Bundle row, int rowLabel,
            int daysId, int gctaId, int tastmId, NumberFormat numbers) {
        bindCell(content, daysId, row, "days", rowLabel, R.string.breakdown_days, numbers);
        bindCell(content, gctaId, row, "gcta", rowLabel, R.string.breakdown_gcta, numbers);
        bindCell(content, tastmId, row, "tastm", rowLabel, R.string.breakdown_tastm, numbers);
    }

    private static void bindCell(View content, int id, Bundle row, String key,
            int rowLabel, int columnLabel, NumberFormat numbers) {
        Context context = content.getContext();
        boolean recorded = row != null && row.getBoolean("recorded");
        String value = recorded ? numbers.format(row.getLong(key))
                : context.getString(R.string.breakdown_unrecorded);
        TextView cell = content.findViewById(id);
        cell.setText(value);
        cell.setContentDescription(context.getString(R.string.breakdown_cell_description,
                context.getString(rowLabel), context.getString(columnLabel), recorded
                        ? value : context.getString(R.string.breakdown_not_recorded)));
    }
}
