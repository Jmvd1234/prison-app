package com.example.myapplication;

import android.content.Context;
import android.view.View;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class HomeLayoutTest {
    private Context context;
    @Before public void setup() {
        context=ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("home_preferences",0).edit().clear().commit();
        select(101);
    }
    private void select(long id) {
        context.getSharedPreferences("user_session",0).edit().putLong("currentUserID",id).commit();
    }
    @Test public void aboutDefaultsOpenAndRemembersEachProfileAcrossVisitsAndRotation() {
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(a->{
                assertEquals(View.VISIBLE,a.findViewById(R.id.homeAboutDescription).getVisibility());
                a.findViewById(R.id.homeAboutToggle).performClick();
            });
            scenario.recreate();
            scenario.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.homeAboutDescription).getVisibility()));
        }
        select(202);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(a->assertEquals(View.VISIBLE,a.findViewById(R.id.homeAboutDescription).getVisibility()));
        }
        select(101);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.homeAboutDescription).getVisibility()));
        }
    }
    private void checkLayout(int width,int height) {
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(a->{
                View root=a.findViewById(R.id.main);
                layout(root,width,height);
                NestedScrollView scroll=a.findViewById(R.id.homeScroll);
                View button=a.findViewById(R.id.dashboardButton);
                int[] initial=new int[2];button.getLocationOnScreen(initial);
                a.findViewById(R.id.homeAboutToggle).performClick();layout(root,width,height);
                a.findViewById(R.id.homeAboutToggle).performClick();layout(root,width,height);
                int[] expanded=new int[2];button.getLocationOnScreen(expanded);
                assertArrayEquals("Expanding About must not move the actions",initial,expanded);
                assertTrue("Expanded content must scroll down on a small screen",scroll.canScrollVertically(1));
                assertEquals(0,scroll.getScrollY());
            });
        }
    }
    private void layout(View root,int width,int height) {
        for(int i=0;i<3;i++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
            root.layout(0,0,width,height);
            root.getViewTreeObserver().dispatchOnPreDraw();
        }
    }
    @Test @Config(qualifiers="port-mdpi") public void portraitExpansionExtendsDownward(){checkLayout(360,600);}
    @Test @Config(qualifiers="land-mdpi") public void landscapeExpansionExtendsDownward(){checkLayout(640,360);}
}
