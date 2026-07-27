package com.payton.touchblocker.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CoordinateTransformerTest {
    @Test
    public void resolvesWithinSelectedRegion() {
        DisplaySnapshot snapshot = TestFixtures.snapshot(
                new DisplayRegion("left", new IntRect(0, 0, 1000, 1800)),
                new DisplayRegion("right", new IntRect(1020, 0, 2020, 1800)));
        ProfilePoint point = ProfilePoint.enabled(
                1, "right", 0.25f, 0.5f, 0L, 0L, 1L);

        ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, 40f);

        assertEquals(1270f, resolved.getCenterX(), 0.01f);
        assertEquals(900f, resolved.getCenterY(), 0.01f);
        assertEquals(40f, resolved.getDiameterPx(), 0.01f);
    }

    @Test
    public void scalesDiameterByDisplayDensity() {
        DisplaySnapshot snapshot = TestFixtures.snapshotWithDensity(1000, 1800, 2.5f);
        ProfilePoint point = ProfilePoint.enabled(
                1, "full", 0.5f, 0.5f, 0L, 0L, 1L);

        ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, 48f);

        assertEquals(120f, resolved.getDiameterPx(), 0.01f);
    }

    @Test
    public void missingRegionDoesNotResolve() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = ProfilePoint.enabled(
                1, "missing", 0.5f, 0.5f, 0L, 0L, 1L);

        assertNull(CoordinateTransformer.resolve(snapshot, point, 40f));
    }

    @Test
    public void resolvesUsingAbsoluteOffsetRegionBounds() {
        IntRect bounds = new IntRect(100, 200, 1100, 2000);
        DisplayRegion region = new DisplayRegion("full", bounds);
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "offset-display", 2, bounds, 0, 2f, 9L, null,
                Collections.singletonList(region),
                Collections.<UnsafeArea>emptyList());
        ProfilePoint point = ProfilePoint.enabled(
                1, "full", 0.25f, 0.5f, 0L, 0L, 9L);

        ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, 40f);

        assertEquals(350f, resolved.getCenterX(), 0.01f);
        assertEquals(1100f, resolved.getCenterY(), 0.01f);
        assertEquals(80f, resolved.getDiameterPx(), 0.01f);
        assertEquals(bounds, snapshot.getBounds());
        assertEquals(1000, snapshot.getWidthPx());
        assertEquals(1800, snapshot.getHeightPx());
    }

    @Test
    public void naturalAnchorKeepsPointAtSamePhysicalLocationAfterQuarterTurn() {
        DisplaySnapshot portrait = new DisplaySnapshot(
                "display", 0, 1000, 2000, 0, 1f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 2000))),
                Collections.<UnsafeArea>emptyList());
        DisplaySnapshot landscape = new DisplaySnapshot(
                "display", 0, 2000, 1000, 1, 1f, 2L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 2000, 1000))),
                Collections.<UnsafeArea>emptyList());
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.25f, 0.10f, 0.25f, 0.10f, 0L, 0L, 1L);

        ResolvedPoint portraitPoint = CoordinateTransformer.resolve(portrait, point, 30f);
        ResolvedPoint landscapePoint = CoordinateTransformer.resolve(landscape, point, 30f);

        assertEquals(250f, portraitPoint.getCenterX(), 0.01f);
        assertEquals(200f, portraitPoint.getCenterY(), 0.01f);
        assertEquals(200f, landscapePoint.getCenterX(), 0.01f);
        assertEquals(750f, landscapePoint.getCenterY(), 0.01f);
    }

    @Test
    public void naturalAnchorUsesWholeDisplayInsteadOfRotatedSystemBarInsets() {
        DisplaySnapshot landscape = new DisplaySnapshot(
                "notched-display", 0, 2000, 1000, 1, 1f, 2L, null,
                Collections.singletonList(
                        new DisplayRegion("safe", new IntRect(100, 0, 1900, 1000))),
                Collections.<UnsafeArea>emptyList());
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "safe", 0.25f, 0.15f, 0.25f, 0.15f, 0L, 0L, 1L);

        ResolvedPoint resolved = CoordinateTransformer.resolve(landscape, point, 30f);

        assertEquals(300f, resolved.getCenterX(), 0.01f);
        assertEquals(750f, resolved.getCenterY(), 0.01f);
    }

    @Test
    public void naturalAnchorStaysAtTheSamePhysicalLocationAcrossAllFourRotationsAndBars() {
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "recorded-region", 0.25f, 0.10f, 0.25f, 0.10f, 0L, 0L, 1L);
        DisplaySnapshot[] snapshots = {
                safeSnapshot(0, new IntRect(0, 100, 1000, 1900)),
                safeSnapshot(1, new IntRect(100, 0, 1900, 1000)),
                safeSnapshot(2, new IntRect(0, 100, 1000, 1900)),
                safeSnapshot(3, new IntRect(100, 0, 1900, 1000))
        };
        float[][] expected = {
                {250f, 200f}, {200f, 750f}, {750f, 1800f}, {1800f, 250f}
        };

        for (int index = 0; index < snapshots.length; index++) {
            ResolvedPoint resolved = CoordinateTransformer.resolve(snapshots[index], point, 30f);
            assertNotNull("rotation=" + index, resolved);
            assertEquals("rotation=" + index, expected[index][0], resolved.getCenterX(), 0.01f);
            assertEquals("rotation=" + index, expected[index][1], resolved.getCenterY(), 0.01f);
        }
    }

    @Test
    public void naturalAnchorIsRejectedWhenRotationMovesItIntoACutout() {
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.25f, 0.10f, 0.25f, 0.10f, 0L, 0L, 1L);
        DisplaySnapshot landscape = new DisplaySnapshot(
                "cutout-display", 0, 2000, 1000, 1, 1f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 2000, 1000))),
                Collections.singletonList(
                        new UnsafeArea(new IntRect(150, 700, 250, 800),
                                PointDisabledReason.CUTOUT)));

        assertNull(CoordinateTransformer.resolve(landscape, point, 30f));
    }

    @Test
    public void naturalAnchorRemainsValidWhenFoldRegionsRotateInAllDirections() {
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "left", 0.25f, 0.10f, 0.25f, 0.10f, 0L, 0L, 1L);
        DisplaySnapshot[] snapshots = {
                splitSnapshot(0, "left", new IntRect(0, 0, 490, 2000),
                        "right", new IntRect(510, 0, 1000, 2000)),
                splitSnapshot(1, "top", new IntRect(0, 0, 2000, 490),
                        "bottom", new IntRect(0, 510, 2000, 1000)),
                splitSnapshot(2, "left", new IntRect(0, 0, 490, 2000),
                        "right", new IntRect(510, 0, 1000, 2000)),
                splitSnapshot(3, "top", new IntRect(0, 0, 2000, 490),
                        "bottom", new IntRect(0, 510, 2000, 1000))
        };

        for (int index = 0; index < snapshots.length; index++) {
            ResolvedPoint resolved = CoordinateTransformer.resolve(snapshots[index], point, 30f);
            assertNotNull("rotation=" + index, resolved);
        }
    }

    private static DisplaySnapshot safeSnapshot(int rotation, IntRect safeBounds) {
        int width = rotation == 1 || rotation == 3 ? 2000 : 1000;
        int height = rotation == 1 || rotation == 3 ? 1000 : 2000;
        return new DisplaySnapshot(
                "display", 0, width, height, rotation, 1f, rotation + 1L, null,
                Collections.singletonList(new DisplayRegion("safe", safeBounds)),
                Collections.<UnsafeArea>emptyList());
    }

    private static DisplaySnapshot splitSnapshot(
            int rotation,
            String firstId,
            IntRect first,
            String secondId,
            IntRect second
    ) {
        int width = rotation == 1 || rotation == 3 ? 2000 : 1000;
        int height = rotation == 1 || rotation == 3 ? 1000 : 2000;
        return new DisplaySnapshot(
                "fold", 0, width, height, rotation, 1f, rotation + 1L, null,
                java.util.Arrays.asList(
                        new DisplayRegion(firstId, first), new DisplayRegion(secondId, second)),
                Collections.<UnsafeArea>emptyList());
    }

    @Test
    public void displaySnapshotExposesFieldsAndProtectsGeometryLists() {
        DisplayRegion region = new DisplayRegion("full", new IntRect(0, 0, 1200, 2000));
        UnsafeArea unsafeArea = new UnsafeArea(
                new IntRect(500, 0, 700, 80), PointDisabledReason.CUTOUT);
        List<DisplayRegion> regions = new ArrayList<>(Collections.singletonList(region));
        List<UnsafeArea> unsafeAreas = new ArrayList<>(
                Collections.singletonList(unsafeArea));

        DisplaySnapshot snapshot = new DisplaySnapshot(
                "stable-display", 8, 1200, 2000, 3, 2.75f, 42L,
                ProfileKind.INNER, regions, unsafeAreas);
        regions.clear();
        unsafeAreas.clear();

        assertEquals("stable-display", snapshot.getStableKey());
        assertEquals(8, snapshot.getDisplayId());
        assertEquals(1200, snapshot.getWidthPx());
        assertEquals(2000, snapshot.getHeightPx());
        assertEquals(new IntRect(0, 0, 1200, 2000), snapshot.getBounds());
        assertEquals(3, snapshot.getRotation());
        assertEquals(2.75f, snapshot.getDensity(), 0f);
        assertEquals(42L, snapshot.getGeneration());
        assertEquals(ProfileKind.INNER, snapshot.getSuggestedKind());
        assertSame(region, snapshot.getRegions().get(0));
        assertSame(unsafeArea, snapshot.getUnsafeAreas().get(0));
        assertListIsUnmodifiable(snapshot.getRegions());
        assertListIsUnmodifiable(snapshot.getUnsafeAreas());
    }

    private static void assertListIsUnmodifiable(List<?> values) {
        try {
            values.clear();
            fail("Expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
