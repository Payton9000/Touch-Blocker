package com.payton.touchblocker.display;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DisplayFoldCacheTest {
    @Test
    public void keepsLastFoldIndependentlyForEachDisplay() {
        DisplayFoldCache cache = new DisplayFoldCache();
        FoldFeatureData inner = new FoldFeatureData(
                new IntRect(500, 0, 520, 1800), true, true);
        FoldFeatureData external = new FoldFeatureData(
                new IntRect(0, 400, 1600, 420), true, true);

        cache.update(0, inner);
        cache.update(4, external);

        assertEquals(inner, cache.get(0));
        assertEquals(external, cache.get(4));
    }

    @Test
    public void noFoldUpdateClearsOnlyTheObservedDisplay() {
        DisplayFoldCache cache = new DisplayFoldCache();
        FoldFeatureData fold = new FoldFeatureData(
                new IntRect(500, 0, 520, 1800), true, true);
        cache.update(0, fold);
        cache.update(4, fold);

        cache.update(0, null);

        assertNull(cache.get(0));
        assertEquals(fold, cache.get(4));
    }
}
