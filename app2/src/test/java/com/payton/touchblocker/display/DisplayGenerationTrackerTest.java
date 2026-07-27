package com.payton.touchblocker.display;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DisplayGenerationTrackerTest {
    @Test
    public void repeatedEquivalentGeometryKeepsGenerationStablePerDisplay() {
        DisplayGenerationTracker tracker = new DisplayGenerationTracker();
        DisplayGeometrySignature signature = signature(
                new IntRect(200, 100, 1200, 1900),
                0,
                new EdgeInsets(0, 80, 0, 40));

        assertEquals(1L, tracker.generationFor(4, signature));
        assertEquals(1L, tracker.generationFor(4, signature));
        assertEquals(1L, tracker.generationFor(7, signature));
    }

    @Test
    public void anyGeometrySignatureChangeIncrementsOnlyThatDisplay() {
        DisplayGenerationTracker tracker = new DisplayGenerationTracker();
        DisplayGeometrySignature portrait = signature(
                new IntRect(0, 0, 1000, 1800), 0, EdgeInsets.NONE);
        DisplayGeometrySignature landscape = signature(
                new IntRect(0, 0, 1800, 1000), 1, EdgeInsets.NONE);

        assertEquals(1L, tracker.generationFor(0, portrait));
        assertEquals(1L, tracker.generationFor(9, portrait));
        assertEquals(2L, tracker.generationFor(0, landscape));
        assertEquals(1L, tracker.generationFor(9, portrait));
    }

    private static DisplayGeometrySignature signature(
            IntRect bounds,
            int rotation,
            EdgeInsets insets
    ) {
        return new DisplayGeometrySignature(
                bounds,
                rotation,
                insets,
                new FoldFeatureData(new IntRect(600, 100, 620, 1900), true, true),
                Collections.singletonList(new IntRect(200, 100, 320, 180)));
    }
}
