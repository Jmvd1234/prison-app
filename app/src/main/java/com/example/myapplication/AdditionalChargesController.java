package com.example.myapplication;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;

/**
 * Shared Add/Edit charge rows. Edits are drafts until the profile and all charges
 * are saved together; rotation restores the draft instead of reloading saved rows.
 */
final class AdditionalChargesController {
    private static final String STATE_VALUES = "additional_charge_drafts";
    private static final int[] INPUT_IDS = {
            R.id.chargeMinYears, R.id.chargeMinMonths, R.id.chargeMinDays,
            R.id.chargeMaxYears, R.id.chargeMaxMonths, R.id.chargeMaxDays
    };
    private final LinearLayout container;

    AdditionalChargesController(LinearLayout container, View addButton, Bundle savedState) {
        this(container, addButton, null, savedState);
    }

    AdditionalChargesController(LinearLayout container, View addButton, Profile profile, Bundle savedState) {
        this.container = container;
        // The repeated XML IDs are scoped to each row. Save their text explicitly rather
        // than letting Android restore one row's view state into another row.
        container.setSaveFromParentEnabled(false);
        ArrayList<String> values = savedState == null ? null : savedState.getStringArrayList(STATE_VALUES);
        if (values != null) {
            for (int offset = 0; offset + INPUT_IDS.length <= values.size(); offset += INPUT_IDS.length) {
                View row = addRow();
                for (int field = 0; field < INPUT_IDS.length; field++) {
                    ((EditText) row.findViewById(INPUT_IDS[field])).setText(values.get(offset + field));
                }
            }
        } else if (profile != null && profile.charges != null) {
            for (int index = 1; index < profile.charges.size(); index++) {
                ProfileCharge charge = profile.charges.get(index);
                View row = addRow();
                int[] parts = {charge.minYears, charge.minMonths, charge.minDays,
                        charge.maxYears, charge.maxMonths, charge.maxDays};
                for (int field = 0; field < INPUT_IDS.length; field++) {
                    ((EditText) row.findViewById(INPUT_IDS[field])).setText(Integer.toString(parts[field]));
                }
            }
        }
        addButton.setOnClickListener(view -> {
            View row = addRow();
            row.post(() -> row.requestRectangleOnScreen(new Rect(0, 0, row.getWidth(), row.getHeight()), false));
        });
    }

    private View addRow() {
        View row = LayoutInflater.from(container.getContext())
                .inflate(R.layout.view_additional_charge, container, false);
        row.findViewById(R.id.removeChargeButton).setOnClickListener(view -> {
            container.removeView(row);
            renumber();
        });
        container.addView(row);
        renumber();
        return row;
    }

    private void renumber() {
        String[] labels = container.getResources().getStringArray(R.array.charge_input_labels);
        for (int index = 0; index < container.getChildCount(); index++) {
            View row = container.getChildAt(index);
            int number = index + 2; // Charge 1 is the existing, non-removable form.
            ((TextView) row.findViewById(R.id.chargeTitle)).setText(
                    container.getContext().getString(R.string.charge_title, number));
            row.findViewById(R.id.removeChargeButton).setContentDescription(
                    container.getContext().getString(R.string.remove_charge_description, number));
            for (int field = 0; field < INPUT_IDS.length; field++) {
                row.findViewById(INPUT_IDS[field]).setContentDescription(
                        container.getContext().getString(R.string.charge_input_description, number, labels[field]));
            }
        }
    }

    void saveInstanceState(Bundle outState) {
        ArrayList<String> values = new ArrayList<>();
        for (int index = 0; index < container.getChildCount(); index++) {
            View row = container.getChildAt(index);
            for (int id : INPUT_IDS) values.add(((EditText) row.findViewById(id)).getText().toString());
        }
        outState.putStringArrayList(STATE_VALUES, values);
    }

    List<ProfileCharge> readCharges(Profile firstRow) {
        List<ProfileCharge> charges = new ArrayList<>();
        charges.add(ProfileCharge.firstFormRow(firstRow));
        for (int index = 0; index < container.getChildCount(); index++) {
            View row = container.getChildAt(index);
            int[] parts = new int[INPUT_IDS.length];
            for (int field = 0; field < INPUT_IDS.length; field++) {
                String text = ((EditText) row.findViewById(INPUT_IDS[field])).getText().toString().trim();
                try {
                    parts[field] = text.isEmpty() ? 0 : Integer.parseInt(text);
                    if (parts[field] < 0) throw new NumberFormatException();
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException("Charge " + (index + 2) + ": enter non-negative whole numbers within the integer range.");
                }
            }
            ProfileCharge charge = new ProfileCharge();
            charge.minYears = parts[0]; charge.minMonths = parts[1]; charge.minDays = parts[2];
            charge.maxYears = parts[3]; charge.maxMonths = parts[4]; charge.maxDays = parts[5];
            charges.add(charge);
        }
        return charges;
    }
}
