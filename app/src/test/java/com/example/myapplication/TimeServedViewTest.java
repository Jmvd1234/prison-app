package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TimeServedViewTest {
    private Context context() {
        return new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MyApplication);
    }
    private Bundle values(String observation) {
        SentenceCalculator.Input i = new SentenceCalculator.Input();
        i.leaArrestDate = "2018-01-10"; i.commitmentDate = "2018-06-20";
        i.observationDate = observation; i.minYears = 3; i.maxYears = 5;
        SentenceCalculator.Result r = SentenceCalculator.calculatePreview(i);
        assertTrue(r.problem, r.available);
        java.time.LocalDate date = java.time.LocalDate.parse(observation);
        return BreakdownViewBinder.createArguments(context().getString(R.string.breakdown_balance_value, r.minimumText(date), r.minRemainingDays),
                context().getString(R.string.breakdown_balance_value, r.maximumText(date), r.maxRemainingDays), r);
    }
    private String text(View root, int id) { return ((TextView)root.findViewById(id)).getText().toString(); }
    @Test public void popupAndDetailedViewShowBothTotalsAndRefreshWhenRewinding() {
        for (int layout : new int[]{R.layout.dialog_profile_breakdown, R.layout.view_sentence_breakdown}) {
            View root = LayoutInflater.from(context()).inflate(layout, null);
            BreakdownViewBinder.bind(root, values("2020-02-20"));
            assertEquals("2y, 1m, 10d (771 days)", text(root, R.id.actualTimeServedValue));
            assertEquals("4y, 0m, 10d (1471 days)", text(root, R.id.timeServedWithAllowancesValue));
            BreakdownViewBinder.bind(root, values("2018-01-10"));
            assertEquals("0y, 0m, 0d (0 days)", text(root, R.id.actualTimeServedValue));
            assertEquals("0y, 0m, 0d (0 days)", text(root, R.id.timeServedWithAllowancesValue));
        }
    }
    @Test public void totalsSurviveSavedDialogArguments() {
        Parcel parcel = Parcel.obtain();
        Bundle restored;
        try {
            parcel.writeBundle(values("2020-02-20")); parcel.setDataPosition(0);
            restored = parcel.readBundle(getClass().getClassLoader());
        } finally { parcel.recycle(); }
        View root = LayoutInflater.from(context()).inflate(R.layout.dialog_profile_breakdown, null);
        BreakdownViewBinder.bind(root, restored);
        assertEquals("4y, 0m, 10d (1471 days)", text(root, R.id.timeServedWithAllowancesValue));
    }
    @Test public void portraitAndLandscapeKeepTheTotalsAboveRemainingSentence() throws Exception {
        for (int width : new int[]{320, 640}) {
            View root = LayoutInflater.from(context()).inflate(R.layout.view_sentence_breakdown, null);
            BreakdownViewBinder.bind(root, values("2020-02-20"));
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            root.layout(0, 0, width, root.getMeasuredHeight());
            TextView actual = root.findViewById(R.id.actualTimeServedValue);
            TextView credited = root.findViewById(R.id.timeServedWithAllowancesValue);
            TextView minimum = root.findViewById(R.id.minimumSentenceValue);
            assertTrue(actual.getBottom() < credited.getTop());
            assertTrue(credited.getBottom() < minimum.getTop());
            assertNotEquals(actual.getCurrentTextColor(), credited.getCurrentTextColor());
            for (TextView value : new TextView[]{actual, credited}) {
                assertTrue(value.getHeight() >= value.getLayout().getHeight() + value.getPaddingTop() + value.getPaddingBottom());
                assertEquals(0, value.getLayout().getEllipsisCount(value.getLineCount()-1));
            }
            String folder = System.getProperty("laya.test.screenshots");
            if (folder != null) {
                Bitmap bitmap = Bitmap.createBitmap(width, root.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE); root.draw(canvas);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(folder,"time-served-"+width+".png"))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } finally { bitmap.recycle(); }
            }
        }
    }
}
