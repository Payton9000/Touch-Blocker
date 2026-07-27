package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.view.WindowManager;
import org.junit.Test;

public class OverlayWindowSpecTest {
    @Test
    public void flagsIncludeNoLimitsAndInScreen() {
        int flags = OverlayWindowSpec.windowFlags();
        assertTrue((flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0);
    }

    @Test
    public void cutoutModeIsAlwaysFromApi30() {
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                OverlayWindowSpec.cutoutMode(30));
        assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,
                OverlayWindowSpec.cutoutMode(28));
        assertEquals(0, OverlayWindowSpec.cutoutMode(27));
    }

    @Test
    public void fitInsetsClearedFromApi30() {
        assertTrue(OverlayWindowSpec.clearFitInsets(30));
        assertFalse(OverlayWindowSpec.clearFitInsets(29));
    }

    @Test
    public void windowTypeMatchesPlatform() {
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                OverlayWindowSpec.windowType(26));
        assertEquals(WindowManager.LayoutParams.TYPE_PHONE, OverlayWindowSpec.windowType(21));
    }
}
