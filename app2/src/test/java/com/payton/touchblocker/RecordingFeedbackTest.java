package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RecordingFeedbackTest {
    @Test
    public void markersFadeToTransparentAfterTenSeconds() {
        RecordingFeedback feedback = new RecordingFeedback();
        feedback.replace(Arrays.asList(new float[]{100f, 200f, 30f}), 1_000L);

        assertTrue(feedback.hasVisibleMarkers(1_000L));
        assertEquals(204, feedback.alphaAt(1_000L));
        assertTrue(feedback.alphaAt(6_000L) < 204);
        assertEquals(0, feedback.alphaAt(11_000L));
        assertFalse(feedback.hasVisibleMarkers(11_000L));
        assertEquals(Collections.emptyList(), feedback.markers(11_000L));
    }
}
