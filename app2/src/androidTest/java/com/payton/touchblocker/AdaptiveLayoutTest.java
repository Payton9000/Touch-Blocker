package com.payton.touchblocker;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

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
    public void recordingOpensWithoutOverlayPermissionAndShowsControls() {
        try (ActivityScenario<RecordingActivity> ignored = ActivityScenario.launch(RecordingActivity.class)) {
            onView(withId(R.id.btn_record_start)).check(matches(isDisplayed()));
            onView(withId(R.id.btn_record_stop)).check(matches(isDisplayed()));
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
        }
    }
}
