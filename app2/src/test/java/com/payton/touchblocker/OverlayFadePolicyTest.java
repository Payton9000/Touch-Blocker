package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayFadePolicyTest {
    @Test
    public void nonDebugOverlayFadesBothCircleAndBaseToTransparentAfterTenSeconds() {
        assertEquals(0, OverlayFadePolicy.alphaAt(180, 10_000L));
        assertEquals(0, OverlayFadePolicy.alphaAt(24, 10_000L));
        assertTrue(OverlayFadePolicy.alphaAt(180, 5_000L) < 180);
    }

    @Test
    public void fadeStartsFullyOpaqueAndDecreasesMonotonically() {
        assertEquals(180, OverlayFadePolicy.alphaAt(180, 0L));
        int previous = OverlayFadePolicy.alphaAt(180, 0L);
        for (long elapsed = 0; elapsed <= OverlayFadePolicy.DURATION_MS; elapsed += 100L) {
            int alpha = OverlayFadePolicy.alphaAt(180, elapsed);
            assertTrue("alpha must never increase at " + elapsed + "ms", alpha <= previous);
            previous = alpha;
        }
        assertEquals(0, previous);
    }

    /**
     * The animator ticks once per display frame (~600 times over the fade at 60Hz) and every
     * distinct alpha costs a redraw of every overlay window. Quantising caps the number of redraws
     * at the number of steps regardless of refresh rate; measured on an emulator this cut rendered
     * frames for seven windows from ~1190 to ~187.
     */
    @Test
    public void alphaIsQuantisedSoRepeatedTicksInOneStepReturnTheSameValue() {
        java.util.Set<Integer> distinct = new java.util.HashSet<>();
        for (long elapsed = 0; elapsed < OverlayFadePolicy.DURATION_MS; elapsed += 5L) {
            distinct.add(OverlayFadePolicy.alphaAt(180, elapsed));
        }
        assertTrue(
                "expected at most " + OverlayFadePolicy.STEPS + " distinct levels, got "
                        + distinct.size(),
                distinct.size() <= OverlayFadePolicy.STEPS);
        assertTrue("fade must still be gradual", distinct.size() >= 8);
    }

    @Test
    public void clampsOutOfRangeInputs() {
        // Negative elapsed clamps to 0, i.e. the start of the fade: fully opaque, not transparent.
        assertEquals(180, OverlayFadePolicy.alphaAt(180, -1L));
        assertEquals(0, OverlayFadePolicy.alphaAt(0, 0L));
        assertEquals(255, OverlayFadePolicy.alphaAt(999, 0L));
        assertEquals(0, OverlayFadePolicy.alphaAt(180, 99_999L));
    }
}
