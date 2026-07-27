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
}
