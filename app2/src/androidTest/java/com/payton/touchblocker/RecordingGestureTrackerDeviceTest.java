package com.payton.touchblocker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RecordingGestureTrackerDeviceTest {
    private static final float TOUCH_SLOP = 12f;

    @Test
    public void stationaryTapIsRecorded() {
        RecordingGestureTracker tracker = new RecordingGestureTracker(TOUCH_SLOP);

        tracker.onDown(100f, 200f);

        assertTrue(tracker.finishAsTap());
    }

    @Test
    public void smallFingerJitterStillCountsAsTap() {
        RecordingGestureTracker tracker = new RecordingGestureTracker(TOUCH_SLOP);

        tracker.onDown(100f, 200f);
        tracker.onMove(111f, 189f);

        assertTrue(tracker.finishAsTap());
    }

    @Test
    public void accidentalSwipeIsNotRecorded() {
        RecordingGestureTracker tracker = new RecordingGestureTracker(TOUCH_SLOP);

        tracker.onDown(100f, 200f);
        tracker.onMove(113f, 200f);

        assertFalse(tracker.finishAsTap());
    }

    @Test
    public void cancelledGestureIsNotRecorded() {
        RecordingGestureTracker tracker = new RecordingGestureTracker(TOUCH_SLOP);

        tracker.onDown(100f, 200f);
        tracker.cancel();

        assertFalse(tracker.finishAsTap());
    }
}
