package com.payton.touchblocker.geometry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.display.EdgeInsets;
import com.payton.touchblocker.display.IntRect;

import org.junit.Test;

/**
 * Covers detection of points a system bar intercepts before the overlay can.
 *
 * <p>Measured on an emulator: an overlay window centred 36px from the top blocked 0 of 10 taps
 * with the launcher in front (status bar visible, touchableRegion [0,0][2208,74]) and 10 of 10 with
 * a fullscreen activity in front. The bar wins because it sits at window layer 151000 while
 * {@code TYPE_APPLICATION_OVERLAY} -- the highest layer available to a normal app -- is 111000.
 */
public class SystemBarShadowTest {
    private static final IntRect SCREEN = new IntRect(0, 0, 1080, 2400);
    private static final EdgeInsets BARS = new EdgeInsets(0, 74, 0, 120);

    @Test
    public void pointUnderTheStatusBarIsShadowed() {
        assertTrue(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 36f));
        assertTrue(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 73f));
    }

    @Test
    public void pointJustBelowTheStatusBarIsNotShadowed() {
        assertFalse(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 74f));
        assertFalse(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 1200f));
    }

    @Test
    public void pointUnderTheNavigationBarIsShadowed() {
        assertTrue(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 2399f));
        assertTrue(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 2280f));
        assertFalse(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 2279f));
    }

    /** Landscape puts the navigation bar on a side edge, so left/right insets matter too. */
    @Test
    public void sideInsetsShadowLeftAndRightEdges() {
        EdgeInsets sideBars = new EdgeInsets(120, 0, 80, 0);
        IntRect landscape = new IntRect(0, 0, 2400, 1080);

        assertTrue(SystemBarShadow.isShadowed(landscape, sideBars, 10f, 540f));
        assertTrue(SystemBarShadow.isShadowed(landscape, sideBars, 2350f, 540f));
        assertFalse(SystemBarShadow.isShadowed(landscape, sideBars, 1200f, 540f));
    }

    @Test
    public void noInsetsMeansNothingIsShadowed() {
        assertFalse(SystemBarShadow.isShadowed(SCREEN, EdgeInsets.NONE, 540f, 0f));
        assertFalse(SystemBarShadow.isShadowed(SCREEN, EdgeInsets.NONE, 540f, 2399f));
    }

    /** A point outside the display is out of bounds, not shadowed; that is a different report. */
    @Test
    public void pointOutsideTheDisplayIsNotReportedAsShadowed() {
        assertFalse(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, -50f));
        assertFalse(SystemBarShadow.isShadowed(SCREEN, BARS, 540f, 2500f));
        assertFalse(SystemBarShadow.isShadowed(SCREEN, BARS, -10f, 500f));
    }

    @Test
    public void nullInputsAreNotShadowed() {
        assertFalse(SystemBarShadow.isShadowed(null, BARS, 1f, 1f));
        assertFalse(SystemBarShadow.isShadowed(SCREEN, null, 1f, 1f));
    }

    /** Offset display bounds (multi-display) must be handled relative to their own origin. */
    @Test
    public void insetsAreRelativeToTheDisplayOrigin() {
        IntRect offset = new IntRect(100, 200, 1180, 2600);

        assertTrue(SystemBarShadow.isShadowed(offset, BARS, 640f, 240f));
        assertFalse(SystemBarShadow.isShadowed(offset, BARS, 640f, 300f));
    }
}
