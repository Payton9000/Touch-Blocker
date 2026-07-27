package com.payton.touchblocker.geometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfilePoint;

import java.util.Collections;

import org.junit.Test;

public class PointValidatorTest {
    @Test
    public void hingeIntersectionNoLongerDisablesPoint() {
        DisplaySnapshot snapshot = TestFixtures.withUnsafeRect(
                2020, 1800, new IntRect(1000, 0, 1020, 1800), PointDisabledReason.HINGE);
        ProfilePoint point = ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L);
        ResolvedPoint resolved = new ResolvedPoint(1010f, 900f, 120f);

        assertTrue(PointValidator.validate(snapshot, point, resolved).isValid());
    }

    @Test
    public void circleOverhangingScreenEdgeIsValid() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = ProfilePoint.enabled(1, "full", 0.0f, 0.0f, 0L, 0L, 1L);
        ResolvedPoint resolved = new ResolvedPoint(0f, 0f, 120f);

        assertTrue(PointValidator.validate(snapshot, point, resolved).isValid());
    }

    @Test
    public void missingRegionIsOutOfBounds() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = ProfilePoint.enabled(1, "missing", 0.5f, 0.5f, 0L, 0L, 1L);

        PointValidation result = PointValidator.validate(snapshot, point, null);
        assertFalse(result.isValid());
        assertEquals(PointDisabledReason.OUT_OF_BOUNDS, result.getReason());
    }

    @Test
    public void centerOutsideRegionIsOutOfBounds() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L);
        ResolvedPoint resolved = new ResolvedPoint(1500f, 900f, 120f);

        PointValidation result = PointValidator.validate(snapshot, point, resolved);
        assertFalse(result.isValid());
        assertEquals(PointDisabledReason.OUT_OF_BOUNDS, result.getReason());
    }

    @Test(expected = IllegalArgumentException.class)
    public void validPointValidationRejectsDisabledReason() {
        new PointValidation(true, PointDisabledReason.HINGE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidPointValidationRejectsNoneReason() {
        new PointValidation(false, PointDisabledReason.NONE);
    }

    @Test
    public void centerAtSelectedRegionExclusiveRightEdgeIsOutOfBounds() {
        DisplaySnapshot snapshot = insetRegionSnapshot();
        ProfilePoint point = enabledPoint("safe", 1f, 0.5f);
        ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, 20f);

        PointValidation result = PointValidator.validate(snapshot, point, resolved);

        assertInvalid(result, PointDisabledReason.OUT_OF_BOUNDS);
    }

    @Test
    public void rotationAnchoredPointUsesItsCurrentSafeRegionInsteadOfItsOldRegionId() {
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "rotated-fold", 0, 2000, 1000, 1, 1f, 2L, null,
                java.util.Arrays.asList(
                        new DisplayRegion("top", new IntRect(0, 0, 2000, 500)),
                        new DisplayRegion("bottom", new IntRect(0, 500, 2000, 1000))),
                Collections.<UnsafeArea>emptyList());
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "left", 0.25f, 0.1f, 0.25f, 0.1f, 0L, 0L, 1L);
        ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, 30f);

        assertTrue(PointValidator.validate(snapshot, point, resolved).isValid());
    }

    @Test
    public void nonFiniteUIsInvalidData() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = enabledPoint("full", Float.NaN, 0.5f);

        PointValidation result = PointValidator.validate(
                snapshot, point, new ResolvedPoint(500f, 900f, 40f));

        assertInvalid(result, PointDisabledReason.INVALID_DATA);
    }

    @Test
    public void nonFiniteVIsInvalidData() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = enabledPoint("full", 0.5f, Float.POSITIVE_INFINITY);

        PointValidation result = PointValidator.validate(
                snapshot, point, new ResolvedPoint(500f, 900f, 40f));

        assertInvalid(result, PointDisabledReason.INVALID_DATA);
    }

    @Test
    public void nonFiniteDiameterIsInvalidData() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = enabledPoint("full", 0.5f, 0.5f);

        PointValidation result = PointValidator.validate(
                snapshot, point, new ResolvedPoint(500f, 900f, Float.NaN));

        assertInvalid(result, PointDisabledReason.INVALID_DATA);
    }

    @Test
    public void nonFiniteDensityIsInvalidData() {
        DisplaySnapshot snapshot = TestFixtures.snapshotWithDensity(
                1000, 1800, Float.POSITIVE_INFINITY);
        ProfilePoint point = enabledPoint("full", 0.5f, 0.5f);

        PointValidation result = PointValidator.validate(
                snapshot, point, new ResolvedPoint(500f, 900f, 40f));

        assertInvalid(result, PointDisabledReason.INVALID_DATA);
    }

    @Test
    public void nonFiniteResolvedCenterIsInvalidData() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = enabledPoint("full", 0.5f, 0.5f);

        PointValidation result = PointValidator.validate(
                snapshot, point,
                new ResolvedPoint(Float.NEGATIVE_INFINITY, 900f, 40f));

        assertInvalid(result, PointDisabledReason.INVALID_DATA);
    }

    @Test
    public void nonPositiveDiameterIsInvalidData() {
        DisplaySnapshot snapshot = TestFixtures.singleRegion(1000, 1800);
        ProfilePoint point = enabledPoint("full", 0.5f, 0.5f);

        PointValidation result = PointValidator.validate(
                snapshot, point, new ResolvedPoint(500f, 900f, 0f));

        assertInvalid(result, PointDisabledReason.INVALID_DATA);
    }

    private static ProfilePoint enabledPoint(String regionId, float u, float v) {
        return ProfilePoint.enabled(1, regionId, u, v, 0L, 0L, 1L);
    }

    private static DisplaySnapshot insetRegionSnapshot() {
        return new DisplaySnapshot(
                "inset-display", 0, 1000, 1800, 0, 1f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("safe", new IntRect(50, 100, 950, 1700))),
                Collections.<UnsafeArea>emptyList());
    }

    private static void assertInvalid(
            PointValidation result, PointDisabledReason expectedReason) {
        assertFalse(result.isValid());
        assertEquals(expectedReason, result.getReason());
    }
}
