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
    private static final String ARG_MINIMUM_DAYS = "minimum_days";
    private static final String ARG_MAXIMUM_DAYS = "maximum_days";
    private static final String ARG_LEA = "lea";
    private static final String ARG_JAIL = "jail";
    private static final String ARG_PRISON = "prison";
    private static final String ARG_TOTAL = "total";
    private static final String ARG_STAL_DAYS = "stal_days";
    private static final String ARG_STAL_PERCENT = "stal_percent";
    private static final String ARG_DEDUCTIONS = "deduction_days";

    public static Bundle createArguments(String minimum, String maximum,
            SentenceCalculator.Result result) {
        SentenceCalculator.CustodyBreakdown breakdown = result.custodyBreakdown;
        Bundle arguments = new Bundle();
        arguments.putString(ARG_MINIMUM, minimum);
        arguments.putString(ARG_MAXIMUM, maximum);
        arguments.putLong(ARG_MINIMUM_DAYS, result.minRemainingDays);
        arguments.putLong(ARG_MAXIMUM_DAYS, result.maxRemainingDays);
        arguments.putLong(ARG_STAL_DAYS, result.stalDays);
        arguments.putInt(ARG_STAL_PERCENT, result.stalPercent);
        arguments.putLong(ARG_DEDUCTIONS, result.appliedDeductionDays);
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
        values.putLong("net_gcta", stage.netGctaDays);
        values.putLong("net_tastm", stage.netTastmDays);
        return values;
    }

    public static void bind(View content, Bundle arguments) {
        bindBalance(content, R.id.minimumSentenceValue, arguments.getString(ARG_MINIMUM), arguments.getLong(ARG_MINIMUM_DAYS));
        bindBalance(content, R.id.maximumSentenceValue, arguments.getString(ARG_MAXIMUM), arguments.getLong(ARG_MAXIMUM_DAYS));
        NumberFormat numbers = NumberFormat.getIntegerInstance();
        Context context = content.getContext();
        ((TextView) content.findViewById(R.id.breakdownDeductionValue)).setText(context.getString(
                R.string.breakdown_credit_days, numbers.format(arguments.getLong(ARG_DEDUCTIONS))));
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

    private static void bindBalance(View content, int id, String text, long remainingDays) {
        TextView balance = content.findViewById(id);
        balance.setText(text);
        // XML state selectors restore the normal colors when the slider moves back before zero.
        balance.setActivated(remainingDays < 0);
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
        String value = context.getString(R.string.breakdown_unrecorded);
        String description = context.getString(R.string.breakdown_not_recorded);
        if (recorded) {
            String total = numbers.format(row.getLong(key));
            if (key.equals("days")) {
                value = description = total;
            } else {
                // Bundles restored from an older app version may only contain gross credits.
                String net = numbers.format(row.getLong("net_" + key, row.getLong(key)));
                value = context.getString(R.string.breakdown_credit_ratio, net, total);
                description = context.getString(R.string.breakdown_credit_ratio_description, net, total);
            }
        }
        TextView cell = content.findViewById(id);
        cell.setText(value);
        cell.setContentDescription(context.getString(R.string.breakdown_cell_description,
                context.getString(rowLabel), context.getString(columnLabel), description));
    }
}
