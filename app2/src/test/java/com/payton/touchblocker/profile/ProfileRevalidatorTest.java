package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;

import org.junit.Test;

public class ProfileRevalidatorTest {
    @Test
    public void geometryChangeDisablesInvalidExistingPoint() {
        ScreenProfile profile = TestFixtures.profileWithPoint(
                ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L));
        DisplaySnapshot otherRegionOnly = TestFixtures.snapshot(
                new DisplayRegion("other", new IntRect(0, 0, 1000, 1800)));

        ScreenProfile updated = new ProfileRevalidator().revalidate(profile, otherRegionOnly);

        assertNotSame(profile, updated);
        assertFalse(updated.getPoints().get(0).isEnabled());
        assertEquals(PointDisabledReason.OUT_OF_BOUNDS,
                updated.getPoints().get(0).getDisabledReason());
    }

    @Test
    public void staleHingeDisabledPointIsReenabledWhenValid() {
        ProfilePoint stale = ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L)
                .disabled(PointDisabledReason.HINGE);
        ScreenProfile updated = new ProfileRevalidator().revalidate(
                TestFixtures.profileWithPoint(stale), TestFixtures.singleRegion(1000, 1800));

        assertTrue(updated.getPoints().get(0).isEnabled());
    }

    @Test
    public void needsReviewDisabledPointStaysDisabled() {
        ProfilePoint reviewed = ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L)
                .disabled(PointDisabledReason.NEEDS_REVIEW);
        ScreenProfile updated = new ProfileRevalidator().revalidate(
                TestFixtures.profileWithPoint(reviewed), TestFixtures.singleRegion(1000, 1800));

        assertFalse(updated.getPoints().get(0).isEnabled());
    }

    @Test
    public void explicitReenableValidatesCurrentGeometryFirst() {
        ProfilePoint disabled = ProfilePoint.enabled(
                1, "full", 0.5f, 0.5f, 0L, 0L, 1L)
                .disabled(PointDisabledReason.NEEDS_REVIEW);
        ScreenProfile profile = TestFixtures.profileWithPoint(disabled);
        DisplaySnapshot otherRegionOnly = TestFixtures.snapshot(
                new DisplayRegion("other", new IntRect(0, 0, 1000, 1800)));

        ScreenProfile stillDisabled = new ProfileRevalidator()
                .setPointEnabled(profile, 1, true, otherRegionOnly);

        assertFalse(stillDisabled.getPoints().get(0).isEnabled());
        assertEquals(PointDisabledReason.OUT_OF_BOUNDS,
                stillDisabled.getPoints().get(0).getDisabledReason());
    }

    @Test
    public void unchangedValidProfileReturnsSameInstance() {
        ScreenProfile profile = TestFixtures.profileWithPoint(
                ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L));

        ScreenProfile updated = new ProfileRevalidator().revalidate(
                profile, TestFixtures.singleRegion(1000, 1800));

        assertSame(profile, updated);
    }

    @Test
    public void missingPointEnableRequestIsNoOp() {
        ScreenProfile profile = TestFixtures.profileWithPoint(
                ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L));

        ScreenProfile updated = new ProfileRevalidator().setPointEnabled(
                profile, 99, true, TestFixtures.singleRegion(1000, 1800));

        assertSame(profile, updated);
    }

    @Test
    public void explicitEnableSucceedsWhenCurrentGeometryIsValid() {
        ProfilePoint disabled = ProfilePoint.enabled(
                1, "full", 0.5f, 0.5f, 0L, 0L, 1L)
                .disabled(PointDisabledReason.NEEDS_REVIEW);
        ScreenProfile profile = TestFixtures.profileWithPoint(disabled);

        ScreenProfile updated = new ProfileRevalidator().setPointEnabled(
                profile, 1, true, TestFixtures.singleRegion(1000, 1800));

        assertNotSame(profile, updated);
        assertTrue(updated.getPoints().get(0).isEnabled());
        assertEquals(PointDisabledReason.NONE,
                updated.getPoints().get(0).getDisabledReason());
    }

    @Test
    public void explicitDisableUsesReviewReasonAndPreservesOtherData() {
        ProfilePoint original = ProfilePoint.enabled(
                1, "full", 0.25f, 0.75f, 10L, 20L, 3L);
        ScreenProfile profile = TestFixtures.profileWithPoint(original);

        ScreenProfile updated = new ProfileRevalidator().setPointEnabled(
                profile, 1, false, TestFixtures.singleRegion(1000, 1800));

        ProfilePoint disabled = updated.getPoints().get(0);
        assertFalse(disabled.isEnabled());
        assertEquals(PointDisabledReason.NEEDS_REVIEW, disabled.getDisabledReason());
        assertEquals(original.getU(), disabled.getU(), 0f);
        assertEquals(original.getV(), disabled.getV(), 0f);
        assertEquals(original.getTimestamp(), disabled.getTimestamp());
        assertEquals(original.getDurationMs(), disabled.getDurationMs());
    }
}
