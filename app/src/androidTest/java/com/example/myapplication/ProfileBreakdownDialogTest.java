package com.example.myapplication;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcel;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProfileBreakdownDialogTest {
    private Bundle arguments(Context context, boolean cpi) {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2018-01-10";
        in.commitmentDate = "2018-06-20";
        in.observationDate = "2020-02-20";
        in.minYears = 10;
        in.maxYears = 15;
        in.disqualifiedForCPI = cpi;
        in.stalPercent = cpi ? 40 : 20;
        SentenceCalculator.Result r = SentenceCalculator.calculate(in);
        LocalDate observation = LocalDate.parse(in.observationDate);
        Bundle original = ProfileBreakdownDialogFragment.createArguments(
                context.getString(R.string.breakdown_balance_value,
                        r.minimumText(observation), r.minRemainingDays),
                context.getString(R.string.breakdown_balance_value,
                        r.maximumText(observation), r.maxRemainingDays), r);
        // Exercise the same Bundle serialization used by the activity result and saved state.
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(original);
            assertTrue("Popup payload should stay small", parcel.dataSize() < 4096);
            parcel.setDataPosition(0);
            return parcel.readBundle(getClass().getClassLoader());
        } finally {
            parcel.recycle();
        }
    }

    private Dialog dialog(HomeActivity activity) {
        ProfileBreakdownDialogFragment fragment = (ProfileBreakdownDialogFragment)
                activity.getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG);
        assertNotNull(fragment);
        assertTrue(fragment.requireDialog().isShowing());
        return fragment.requireDialog();
    }

    private void text(Dialog dialog, int id, String expected) {
        TextView cell = dialog.findViewById(id);
        assertNotNull(cell);
        assertEquals(expected, cell.getText().toString());
    }

    @Test public void independentCellsRestoreAfterRotationAndStayClosedAfterDismissal() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> ProfileBreakdownDialogFragment.newInstance(arguments(activity, false))
                    .showNow(activity.getSupportFragmentManager(), ProfileBreakdownDialogFragment.TAG));
            scenario.onActivity(activity -> {
                Dialog d = dialog(activity);
                text(d, R.id.leaDaysValue, "161");
                text(d, R.id.leaGctaValue, "0/0");
                text(d, R.id.leaTastmValue, "0/0");
                text(d, R.id.jailDaysValue, "610");
                text(d, R.id.jailGctaValue, "400/400");
                text(d, R.id.jailTastmValue, "300/300");
                text(d, R.id.prisonDaysValue, "\u2014");
                text(d, R.id.prisonGctaValue, "\u2014");
                text(d, R.id.prisonTastmValue, "\u2014");
                text(d, R.id.totalDaysValue, "771");
                text(d, R.id.totalGctaValue, "400/400");
                text(d, R.id.totalTastmValue, "300/300");
                text(d, R.id.stalCreditLabel, "STAL (20%)");
                text(d, R.id.stalCreditValue, "1,096 days");
            });
            screenshot("breakdown-ordinary.png");
            scenario.recreate();
            scenario.onActivity(activity -> {
                Dialog d = dialog(activity);
                text(d, R.id.totalDaysValue, "771");
                text(d, R.id.jailGctaValue, "400/400");
                assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
                text(d, R.id.stalCreditValue, "1,096 days");
                d.findViewById(R.id.closeBreakdownButton).performClick();
                activity.getSupportFragmentManager().executePendingTransactions();
                assertNull(activity.getSupportFragmentManager().findFragmentByTag(ProfileBreakdownDialogFragment.TAG));
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertNull(activity.getSupportFragmentManager()
                    .findFragmentByTag(ProfileBreakdownDialogFragment.TAG)));
        }
    }

    @Test public void cpiShowsPrisonInsteadOfJailAndZeroCredits() throws Exception {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                ProfileBreakdownDialogFragment.newInstance(arguments(activity, true))
                        .showNow(activity.getSupportFragmentManager(), ProfileBreakdownDialogFragment.TAG);
                Dialog d = dialog(activity);
                text(d, R.id.jailDaysValue, "\u2014");
                text(d, R.id.prisonDaysValue, "610");
                text(d, R.id.prisonGctaValue, "0/0");
                text(d, R.id.prisonTastmValue, "0/0");
                text(d, R.id.totalGctaValue, "0/0");
                text(d, R.id.totalTastmValue, "0/0");
                text(d, R.id.stalCreditLabel, "STAL (40%)");
                text(d, R.id.stalCreditValue, "2,192 days");
            });
            screenshot("breakdown-cpi.png");
        }
    }

    @Test public void monthlyRatiosRestoreAndFitWithFourDigitTotals() throws Exception {
        SentenceCalculator.Input in = new SentenceCalculator.Input();
        in.leaArrestDate = "2018-01-10"; in.commitmentDate = "2018-02-28";
        in.prisonTransferDate = "2020-02-28"; in.observationDate = "2026-02-28";
        in.minYears = 10; in.maxYears = 15;
        in.deductionMonths = java.util.Arrays.asList(new DeductionMonth(2018,2,true,true),
                new DeductionMonth(2020,2,true,false));
        SentenceCalculator.Result result = SentenceCalculator.calculate(in);
        assertTrue(result.problem, result.available);
        Bundle values = BreakdownViewBinder.createArguments("Minimum", "Maximum", result);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(a -> ProfileBreakdownDialogFragment.newInstance(values)
                    .showNow(a.getSupportFragmentManager(), ProfileBreakdownDialogFragment.TAG));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertMonthlyRatios(scenario);
            screenshot("breakdown-net-total.png");
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertMonthlyRatios(scenario);
        }
    }

    private void assertMonthlyRatios(ActivityScenario<HomeActivity> scenario) {
        scenario.onActivity(a -> {
            Dialog d = dialog(a);
            text(d,R.id.leaGctaValue,"0/0"); text(d,R.id.leaTastmValue,"0/0");
            text(d,R.id.jailGctaValue,"460/480"); text(d,R.id.jailTastmValue,"345/360");
            text(d,R.id.prisonGctaValue,"1,705/1,728"); text(d,R.id.prisonTastmValue,"1,080/1,080");
            text(d,R.id.totalGctaValue,"2,165/2,208"); text(d,R.id.totalTastmValue,"1,425/1,440");
            text(d,R.id.breakdownDeductionValue,"58 days");
            assertEquals("Total, GCTA: 2,165 of 2,208 possible days",
                    d.findViewById(R.id.totalGctaValue).getContentDescription());
            for (int id : new int[]{R.id.jailGctaValue,R.id.jailTastmValue,R.id.prisonGctaValue,
                    R.id.prisonTastmValue,R.id.totalGctaValue,R.id.totalTastmValue}) {
                TextView cell = d.findViewById(id);
                assertNotNull(cell.getLayout()); assertEquals(1,cell.getLayout().getLineCount());
                assertEquals(0,cell.getLayout().getEllipsisCount(0));
                assertTrue("Credit ratio fits its cell",cell.getLayout().getLineWidth(0)
                        <= cell.getWidth()-cell.getCompoundPaddingLeft()-cell.getCompoundPaddingRight()+1);
            }
        });
    }

    private void screenshot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // A Looper can be idle between animation frames; wait for the dialog to settle too.
        InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(500, 5000);
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("test-screenshots");
        assertNotNull(directory);
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            image.recycle();
        }
    }
}
