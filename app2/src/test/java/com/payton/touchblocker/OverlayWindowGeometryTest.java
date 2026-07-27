package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.PointDisabledReason;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class OverlayWindowGeometryTest {
    @Test
    public void createsOneSquareUsedByWindowAndView() {
        OverlayWindowGeometry geometry =
                OverlayWindowGeometry.centeredAt(100f, 200f, 100);

        assertEquals(new IntRect(50, 150, 150, 250), geometry.getBounds());
        assertEquals(100, geometry.getSizePx());
    }

    @Test
    public void roundsHalfPixelOriginsTowardPositiveInfinity() {
        OverlayWindowGeometry geometry =
                OverlayWindowGeometry.centeredAt(100.5f, -100.5f, 100);

        assertEquals(new IntRect(51, -150, 151, -50), geometry.getBounds());
    }

    @Test
    public void preservesOddAndMaximumRequestedSizes() {
        OverlayWindowGeometry odd = OverlayWindowGeometry.centeredAt(100f, 200f, 101);
        OverlayWindowGeometry maximum =
                OverlayWindowGeometry.centeredAt(0f, 0f, Integer.MAX_VALUE);

        assertEquals(new IntRect(50, 150, 151, 251), odd.getBounds());
        assertEquals(101, odd.getBounds().width());
        assertEquals(
                new IntRect(-1_073_741_823, -1_073_741_823, 1_073_741_824, 1_073_741_824),
                maximum.getBounds());
        assertEquals(Integer.MAX_VALUE, maximum.getBounds().width());
    }

    @Test
    public void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(1f, 1f, 0));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(1f, 1f, -1));
    }

    @Test
    public void rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(Float.NaN, 1f, 100));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(1f, Float.NaN, 100));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(Float.POSITIVE_INFINITY, 1f, 100));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(1f, Float.NEGATIVE_INFINITY, 100));
    }

    @Test
    public void rejectsBoundsThatOverflowIntegerCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(2_147_483_520f, 0f, 256));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(-2_147_483_520f, 0f, 512));
        assertThrows(IllegalArgumentException.class,
                () -> OverlayWindowGeometry.centeredAt(0f, Float.MAX_VALUE, 1));
    }

    @Test
    public void rejectsWindowCrossingFoldHinge() {
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "fold", 0, 1000, 1800, 0, 1f, 1L, null,
                Arrays.asList(
                        new DisplayRegion("left", new IntRect(0, 0, 490, 1800)),
                        new DisplayRegion("right", new IntRect(510, 0, 1000, 1800))),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(490, 0, 510, 1800), PointDisabledReason.HINGE)));

        assertEquals(false,
                OverlayWindowGeometry.centeredAt(500f, 900f, 40).isSafeFor(snapshot));
        assertEquals(true,
                OverlayWindowGeometry.centeredAt(250f, 900f, 40).isSafeFor(snapshot));
    }

    @Test
    public void rejectsWindowIntersectingCutout() {
        DisplaySnapshot snapshot = TestFixtures.withUnsafeRect(
                1000, 1800, new IntRect(450, 0, 550, 80), PointDisabledReason.CUTOUT);

        assertEquals(false,
                OverlayWindowGeometry.centeredAt(500f, 40f, 80).isSafeFor(snapshot));
    }

    @Test
    public void preservesBothSafeCornersBesideTopNotch() {
        DisplaySnapshot snapshot = TestFixtures.withUnsafeRect(
                1080, 2092, new IntRect(480, 0, 590, 110), PointDisabledReason.CUTOUT);

        assertEquals(true,
                OverlayWindowGeometry.centeredAt(400f, 150f, 80).isSafeFor(snapshot));
        assertEquals(true,
                OverlayWindowGeometry.centeredAt(680f, 150f, 80).isSafeFor(snapshot));
        assertEquals(false,
                OverlayWindowGeometry.centeredAt(535f, 50f, 80).isSafeFor(snapshot));
    }

    @Test
    public void adaptsToNotchMovedToLeftEdgeAfterRotation() {
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "rotated-outer", 0, 1800, 1080, 1, 1f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(80, 0, 1800, 1080))),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(0, 450, 80, 630), PointDisabledReason.CUTOUT)));

        assertEquals(false,
                OverlayWindowGeometry.centeredAt(40f, 540f, 80).isSafeFor(snapshot));
        assertEquals(true,
                OverlayWindowGeometry.centeredAt(160f, 120f, 80).isSafeFor(snapshot));
        assertEquals(true,
                OverlayWindowGeometry.centeredAt(1680f, 120f, 80).isSafeFor(snapshot));
    }
}
