package com.example.myapplication;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AdditionalChargesTest {
    private static final int[] FIELDS = { R.id.chargeMinYears, R.id.chargeMinMonths,
            R.id.chargeMinDays, R.id.chargeMaxYears, R.id.chargeMaxMonths, R.id.chargeMaxDays };

    @Test public void removedRowsNeverRestoreIntoSurvivingDraftsAndNewVisitsStartFresh() throws Exception {
        try (ActivityScenario<newEntryActivity> scenario = ActivityScenario.launch(newEntryActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("Charge 1", ((TextView) activity.findViewById(R.id.firstChargeTitle)).getText().toString());
                assertNull(activity.findViewById(R.id.removeChargeButton));
                activity.inputMinYears.setText("2");
                activity.inputMaxYears.setText("5");
                LinearLayout rows = activity.findViewById(R.id.additionalChargesContainer);
                for (int i = 0; i < 3; i++) {
                    activity.findViewById(R.id.addChargeButton).performClick();
                    for (int field = 0; field < FIELDS.length; field++) {
                        ((EditText) rows.getChildAt(i).findViewById(FIELDS[field]))
                                .setText(Integer.toString(i * 6 + field + 1));
                    }
                }
                rows.getChildAt(1).findViewById(R.id.removeChargeButton).performClick();
                assertRows(rows);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("2", activity.inputMinYears.getText().toString());
                assertEquals("5", activity.inputMaxYears.getText().toString());
                assertRows(activity.findViewById(R.id.additionalChargesContainer));
                ScrollView scroll = activity.findViewById(R.id.newEntryScroll);
                scroll.scrollTo(0, Math.max(0, activity.findViewById(R.id.firstChargeTitle).getTop() - 12));
            });
            screenshot("add-entry-three-charges.png");
            scenario.onActivity(activity -> {
                LinearLayout rows = activity.findViewById(R.id.additionalChargesContainer);
                rows.getChildAt(0).findViewById(R.id.removeChargeButton).performClick();
                assertEquals("13", ((EditText) rows.getChildAt(0).findViewById(FIELDS[0])).getText().toString());
                assertEquals("Charge 2", ((TextView) rows.getChildAt(0).findViewById(R.id.chargeTitle)).getText().toString());
                activity.findViewById(R.id.addChargeButton).performClick();
                for (int field : FIELDS) assertEquals("", ((EditText) rows.getChildAt(1).findViewById(field)).getText().toString());
                while (rows.getChildCount() > 0) rows.getChildAt(0).findViewById(R.id.removeChargeButton).performClick();
                assertNull(activity.findViewById(R.id.removeChargeButton));
                assertEquals("2", activity.inputMinYears.getText().toString());
                assertEquals("5", activity.inputMaxYears.getText().toString());
                activity.findViewById(R.id.addChargeButton).performClick();
            });
        }
        try (ActivityScenario<newEntryActivity> reopened = ActivityScenario.launch(newEntryActivity.class)) {
            reopened.onActivity(activity -> {
                assertEquals(0, ((LinearLayout) activity.findViewById(R.id.additionalChargesContainer)).getChildCount());
                assertEquals("", activity.inputMinYears.getText().toString());
            });
        }
    }

    private void assertRows(LinearLayout rows) {
        assertEquals(2, rows.getChildCount());
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            assertEquals("Charge " + (i + 2), ((TextView) row.findViewById(R.id.chargeTitle)).getText().toString());
            assertEquals("Remove Charge " + (i + 2), row.findViewById(R.id.removeChargeButton).getContentDescription());
            for (int field = 0; field < FIELDS.length; field++) {
                assertEquals(Integer.toString((i == 0 ? 0 : 12) + field + 1),
                        ((EditText) row.findViewById(FIELDS[field])).getText().toString());
            }
        }
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5000);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("test-screenshots");
        assertNotNull(directory);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { image.recycle(); }
    }
}
