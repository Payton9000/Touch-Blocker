package com.payton.touchblocker.display;

import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfileKind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class DisplaySnapshotMappingTest {
    @Test
    public void verticalSeparatingHingeCreatesAbsoluteLeftAndRightRegions() {
        DisplaySnapshot snapshot = DisplaySnapshotMapper.map(
                "inner-display",
                4,
                new IntRect(200, 100, 2220, 1900),
                0,
                1f,
                1L,
                ProfileKind.INNER,
                EdgeInsets.NONE,
                Collections.<IntRect>emptyList(),
                new FoldFeatureData(new IntRect(1200, 100, 1220, 1900), true, true));

        assertEquals(Arrays.asList(
                new DisplayRegion("left", new IntRect(200, 100, 1200, 1900)),
                new DisplayRegion("right", new IntRect(1220, 100, 2220, 1900))
        ), snapshot.getRegions());
        assertEquals(Collections.singletonList(
                new UnsafeArea(new IntRect(1200, 100, 1220, 1900), PointDisabledReason.HINGE)
        ), snapshot.getUnsafeAreas());
    }

    @Test
    public void horizontalSeparatingHingeCreatesInsetTopAndBottomRegions() {
        DisplaySnapshot snapshot = DisplaySnapshotMapper.map(
                "inner-display",
                0,
                new IntRect(100, 200, 1100, 2000),
                0,
                1f,
                1L,
                ProfileKind.INNER,
                new EdgeInsets(10, 20, 30, 40),
                Collections.<IntRect>emptyList(),
                new FoldFeatureData(new IntRect(100, 1080, 1100, 1100), true, true));

        assertEquals(Arrays.asList(
                new DisplayRegion("top", new IntRect(110, 220, 1070, 1080)),
                new DisplayRegion("bottom", new IntRect(110, 1100, 1070, 1960))
        ), snapshot.getRegions());
    }

    @Test
    public void noSeparatingFoldCreatesOneInsetFullRegion() {
        DisplaySnapshot snapshot = DisplaySnapshotMapper.map(
                "outer-display",
                0,
                new IntRect(50, 75, 1050, 1875),
                0,
                2f,
                1L,
                ProfileKind.OUTER,
                new EdgeInsets(10, 80, 20, 40),
                Collections.<IntRect>emptyList(),
                new FoldFeatureData(new IntRect(540, 75, 540, 1875), false, false));

        assertEquals(Collections.singletonList(
                new DisplayRegion("full", new IntRect(60, 155, 1030, 1835))
        ), snapshot.getRegions());
        assertEquals(Collections.emptyList(), snapshot.getUnsafeAreas());
    }

    @Test
    public void cutoutsAndFullOcclusionNonSeparatingFoldRemainUnsafe() {
        DisplaySnapshot snapshot = DisplaySnapshotMapper.map(
                "inner-display",
                0,
                new IntRect(0, 0, 1000, 1800),
                0,
                1f,
                1L,
                ProfileKind.INNER,
                EdgeInsets.NONE,
                Arrays.asList(
                        new IntRect(0, 0, 120, 80),
                        new IntRect(880, 0, 1000, 80)),
                new FoldFeatureData(new IntRect(500, 0, 500, 1800), false, true));

        assertEquals(Collections.singletonList(
                new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))
        ), snapshot.getRegions());
        assertEquals(PointDisabledReason.CUTOUT, snapshot.getUnsafeAreas().get(0).getReason());
        assertEquals(PointDisabledReason.CUTOUT, snapshot.getUnsafeAreas().get(1).getReason());
        assertEquals(PointDisabledReason.HINGE, snapshot.getUnsafeAreas().get(2).getReason());
        assertEquals(1, snapshot.getUnsafeAreas().get(2).getBounds().width());
    }
}
