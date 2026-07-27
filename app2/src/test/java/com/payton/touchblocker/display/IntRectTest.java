package com.payton.touchblocker.display;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.profile.PointDisabledReason;

import org.junit.Test;

public class IntRectTest {
    @Test
    public void reportsDimensionsAndIntersection() {
        IntRect rect = new IntRect(10, 20, 110, 220);

        assertEquals(100, rect.width());
        assertEquals(200, rect.height());
        assertTrue(rect.intersects(new IntRect(100, 200, 120, 240)));
        assertFalse(rect.intersects(new IntRect(110, 220, 130, 240)));
    }

    @Test
    public void emptyRectanglesDoNotIntersect() {
        IntRect full = new IntRect(0, 0, 100, 200);

        assertFalse(new IntRect(10, 20, 10, 100).intersects(full));
        assertFalse(new IntRect(10, 20, 90, 20).intersects(full));
    }

    @Test
    public void containsUsesExclusiveRightAndBottomEdges() {
        IntRect rect = new IntRect(10, 20, 110, 220);

        assertTrue(rect.contains(10, 20));
        assertTrue(rect.contains(109, 219));
        assertFalse(rect.contains(110, 20));
        assertFalse(rect.contains(10, 220));
        assertFalse(rect.contains(9, 20));
        assertFalse(rect.contains(10, 19));
    }

    @Test
    public void exposesCoordinatesAndValueEquality() {
        IntRect rect = new IntRect(10, 20, 110, 220);
        IntRect same = new IntRect(10, 20, 110, 220);

        assertEquals(10, rect.getLeft());
        assertEquals(20, rect.getTop());
        assertEquals(110, rect.getRight());
        assertEquals(220, rect.getBottom());
        assertEquals(rect, same);
        assertEquals(rect.hashCode(), same.hashCode());
        assertFalse(rect.equals(new IntRect(10, 20, 111, 220)));
    }

    @Test
    public void persistedGeometryTypesExposeValuesAndUseValueEquality() {
        IntRect bounds = new IntRect(0, 10, 100, 200);
        DisplayRegion region = new DisplayRegion("main", bounds);
        DisplayRegion sameRegion = new DisplayRegion("main", new IntRect(0, 10, 100, 200));
        EdgeInsets insets = new EdgeInsets(1, 2, 3, 4);
        EdgeInsets sameInsets = new EdgeInsets(1, 2, 3, 4);
        UnsafeArea unsafeArea = new UnsafeArea(bounds, PointDisabledReason.CUTOUT);
        UnsafeArea sameUnsafeArea = new UnsafeArea(
                new IntRect(0, 10, 100, 200), PointDisabledReason.CUTOUT);

        assertEquals("main", region.getId());
        assertEquals(bounds, region.getBounds());
        assertEquals(region, sameRegion);
        assertEquals(region.hashCode(), sameRegion.hashCode());

        assertEquals(1, insets.getLeft());
        assertEquals(2, insets.getTop());
        assertEquals(3, insets.getRight());
        assertEquals(4, insets.getBottom());
        assertEquals(insets, sameInsets);
        assertEquals(insets.hashCode(), sameInsets.hashCode());
        assertEquals(new EdgeInsets(0, 0, 0, 0), EdgeInsets.NONE);

        assertEquals(bounds, unsafeArea.getBounds());
        assertEquals(PointDisabledReason.CUTOUT, unsafeArea.getReason());
        assertEquals(unsafeArea, sameUnsafeArea);
        assertEquals(unsafeArea.hashCode(), sameUnsafeArea.hashCode());
    }

    @Test(expected = NullPointerException.class)
    public void displayRegionRejectsNullBounds() {
        new DisplayRegion("main", null);
    }

    @Test(expected = NullPointerException.class)
    public void unsafeAreaRejectsNullReason() {
        new UnsafeArea(new IntRect(0, 0, 1, 1), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unsafeAreaRejectsNoneReason() {
        new UnsafeArea(new IntRect(0, 0, 1, 1), PointDisabledReason.NONE);
    }
}
