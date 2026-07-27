package com.payton.touchblocker;

import android.os.Build;
import android.view.WindowManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.google.android.material.button.MaterialButton;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class AdaptiveLayoutTest {
    @Test
    public void mainActivityShowsHeroAndWorkspace() {
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.screen_preview)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_start_record)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void recordingOpensWithoutOverlayPermissionAndUsesTouchLayerOnly() {
        try (ActivityScenario<RecordingActivity> ignored = ActivityScenario.launch(RecordingActivity.class)) {
            onView(withId(R.id.record_touch_layer)).check(matches(isDisplayed()));
            onView(isAssignableFrom(MaterialButton.class)).check(doesNotExist());
        }
    }

    @Test
    public void recordingInformationBarUsesFiftyPercentOpacity() {
        try (ActivityScenario<RecordingActivity> scenario = ActivityScenario.launch(RecordingActivity.class)) {
            scenario.onActivity(activity -> assertEquals(0.5f,
                    activity.findViewById(R.id.record_info_bar).getAlpha(), 0f));
        }
    }

    @Test
    public void recordingUsesTheEntireWindowIncludingDisplayCutoutArea() {
        try (ActivityScenario<RecordingActivity> scenario = ActivityScenario.launch(RecordingActivity.class)) {
            scenario.onActivity(activity -> {
                WindowMetrics metrics = WindowMetricsCalculator.getOrCreate()
                        .computeCurrentWindowMetrics(activity);
                assertEquals(metrics.getBounds().width(),
                        activity.findViewById(R.id.record_root).getWidth());
                assertEquals(metrics.getBounds().height(),
                        activity.findViewById(R.id.record_root).getHeight());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                            activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
                }
            });
        }
    }

    @Test
    public void manageShowsGlobalSlider() {
        try (ActivityScenario<ManagePointsActivity> ignored = ActivityScenario.launch(ManagePointsActivity.class)) {
            onView(withId(R.id.slider_global_size)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void testScreenShowsSurface() {
        try (ActivityScenario<TestBlockActivity> ignored = ActivityScenario.launch(TestBlockActivity.class)) {
            onView(withId(R.id.test_block_view)).check(matches(isDisplayed()));
            onView(isAssignableFrom(MaterialButton.class)).check(doesNotExist());
        }
    }

    @Test
    public void testPromptTextPassesTouchesToTheTestSurface() {
        try (ActivityScenario<TestBlockActivity> ignored = ActivityScenario.launch(TestBlockActivity.class)) {
            onView(withText(R.string.blocking_test_prompt)).perform(click());
            String expected = InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getString(R.string.test_stats, 0, 1);
            onView(withId(R.id.tv_test_stats)).check(matches(withText(expected)));
        }
    }
}
