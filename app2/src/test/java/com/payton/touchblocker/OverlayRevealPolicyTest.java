package com.payton.touchblocker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlayRevealPolicyTest {
    @Test
    public void userInitiatedActionsRevealNewWindows() {
        assertTrue(OverlayRevealPolicy.shouldRevealNewWindows(
                OverlayService.ACTION_START_OVERLAY));
        assertTrue(OverlayRevealPolicy.shouldRevealNewWindows(
                OverlayService.ACTION_REFRESH_POINTS));
    }

    /**
     * A restart with no intent is the system bringing a sticky service back, not the user asking
     * for the overlay, so the rebuilt windows must stay invisible.
     */
    @Test
    public void systemDrivenRefreshesDoNotRevealNewWindows() {
        assertFalse(OverlayRevealPolicy.shouldRevealNewWindows(null));
        assertFalse(OverlayRevealPolicy.shouldRevealNewWindows(
                OverlayService.ACTION_STOP_OVERLAY));
        assertFalse(OverlayRevealPolicy.shouldRevealNewWindows(
                OverlayService.ACTION_SET_DEBUG));
        assertFalse(OverlayRevealPolicy.shouldRevealNewWindows("some.unrelated.action"));
    }
}
