package com.payton.touchblocker.display;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConservativeSystemBarInsetsTest {
    @Test
    public void missingResourceDimensionsStillReserveConservativeBands() {
        EdgeInsets insets = ConservativeSystemBarInsets.create(
                0, 0, 0, 0, 0, 320);

        assertEquals(new EdgeInsets(48, 48, 48, 96), insets);
    }

    @Test
    public void largerResourceDimensionsWinOverDensityDefaults() {
        EdgeInsets insets = ConservativeSystemBarInsets.create(
                60, 120, 80, 72, 100, 320);

        assertEquals(new EdgeInsets(80, 60, 80, 120), insets);
    }
}
