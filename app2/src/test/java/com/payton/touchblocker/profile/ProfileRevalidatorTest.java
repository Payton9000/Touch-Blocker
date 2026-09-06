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
import com.payton.touchblocker.display.UnsafeArea;

import org.junit.Test;

import java.util.Collections;

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

    /**
     * The full round trip that used to lose blockers permanently: a rotation pushes an anchored
     * point under the camera hole, and rotating back must switch it on again.
     *
     * <p>This works only because validation now reports {@code CUTOUT} rather than
     * {@code OUT_OF_BOUNDS} -- the re-enable branch in {@code revalidate} is reached only for
     * {@code CUTOUT}/{@code HINGE}, so with the wrong reason the point stayed off for good.
     */
    @Test
    public void pointDisabledByACutoutIsReEnabledOnceTheCutoutNoLongerCoversIt() {
        ProfilePoint anchored = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.25f, 0.10f, 0.25f, 0.10f, 0L, 0L, 1L);
        ScreenProfile profile = TestFixtures.profileWithPoint(anchored);
        DisplaySnapshot covered = new DisplaySnapshot(
                "display", 0, 1000, 1800, 0, 1f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(200, 130, 300, 230), PointDisabledReason.CUTOUT)));

        ScreenProfile disabled = new ProfileRevalidator().revalidate(profile, covered);
        assertFalse(disabled.getPoints().get(0).isEnabled());
        assertEquals(
                "must record CUTOUT so the recovery branch can find it",
                PointDisabledReason.CUTOUT, disabled.getPoints().get(0).getDisabledReason());

        ScreenProfile recovered = new ProfileRevalidator().revalidate(
                disabled, TestFixtures.singleRegion(1000, 1800));

        assertTrue(
                "the blocker must come back once the cutout no longer covers it",
                recovered.getPoints().get(0).isEnabled());
        assertEquals(PointDisabledReason.NONE,
                recovered.getPoints().get(0).getDisabledReason());
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
