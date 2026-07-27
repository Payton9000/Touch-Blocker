package com.payton.touchblocker.display;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DisplayFrameDataTest {
    @Test
    public void mergesAllSafetyInsetsWithoutLosingTheLargestEdge() {
        DisplayFrameData frame = DisplayFrameData.fromWindowData(
                new IntRect(200, 100, 1200, 1900),
                new EdgeInsets(0, 40, 0, 80),
                new EdgeInsets(20, 60, 0, 0),
                new EdgeInsets(10, 0, 12, 30),
                Collections.<IntRect>emptyList());

        assertEquals(new EdgeInsets(20, 60, 12, 80), frame.getSafeInsets());
    }

    @Test
    public void convertsWindowRelativeCutoutsToAbsoluteCoordinates() {
        DisplayFrameData frame = DisplayFrameData.fromWindowData(
                new IntRect(200, 100, 1200, 1900),
                EdgeInsets.NONE,
                new EdgeInsets(0, 60, 0, 0),
                EdgeInsets.NONE,
                Collections.singletonList(new IntRect(0, 0, 120, 60)));

        assertEquals(Collections.singletonList(
                new IntRect(200, 100, 320, 160)), frame.getCutouts());
    }
}
