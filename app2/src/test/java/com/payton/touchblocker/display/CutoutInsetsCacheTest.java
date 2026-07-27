package com.payton.touchblocker.display;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CutoutInsetsCacheTest {
    @Test
    public void storesAndReturnsDefensiveCopies() {
        CutoutInsetsCache cache = new CutoutInsetsCache();
        List<IntRect> rects = new ArrayList<>();
        rects.add(new IntRect(100, 0, 200, 80));

        cache.update(0, rects);
        rects.clear();

        List<IntRect> cachedRects = cache.get(0);
        assertEquals(1, cachedRects.size());
        assertEquals(new IntRect(100, 0, 200, 80), cachedRects.get(0));

        cachedRects.clear();
        assertEquals(1, cache.get(0).size());
    }

    @Test
    public void unknownDisplayYieldsEmptyList() {
        assertTrue(new CutoutInsetsCache().get(9).isEmpty());
    }
}
