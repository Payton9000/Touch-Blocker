package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ProfilePointTest {
    @Test
    public void correctedPositionPreservesMetadata() {
        ProfilePoint point = new ProfilePoint(
                7, "left", 0.25f, 0.75f, 48f, true,
                PointDisabledReason.NONE, 1000L, 120L, 3L);

        ProfilePoint corrected = point.withPosition("right", 0.5f, 0.4f, 4L);

        assertEquals(7, corrected.getId());
        assertEquals(48f, corrected.getDiameterDpOverride(), 0f);
        assertTrue(corrected.isEnabled());
        assertEquals(PointDisabledReason.NONE, corrected.getDisabledReason());
        assertEquals(1000L, corrected.getTimestamp());
        assertEquals(120L, corrected.getDurationMs());
        assertEquals("right", corrected.getRegionId());
        assertEquals(0.5f, corrected.getU(), 0f);
        assertEquals(0.4f, corrected.getV(), 0f);
        assertEquals(4L, corrected.getDisplayGeneration());
    }

    @Test
    public void disabledCopyKeepsOriginalCoordinates() {
        ProfilePoint point = ProfilePoint.enabled(1, "full", 0.2f, 0.3f, 10L, 20L, 1L);

        ProfilePoint disabled = point.disabled(PointDisabledReason.HINGE);

        assertFalse(disabled.isEnabled());
        assertEquals(PointDisabledReason.HINGE, disabled.getDisabledReason());
        assertEquals(point.getU(), disabled.getU(), 0f);
        assertEquals(point.getV(), disabled.getV(), 0f);
    }

    @Test
    public void enabledFactoryUsesDefaultOverrideAndReason() {
        ProfilePoint point = ProfilePoint.enabled(
                2, "full", 0.1f, 0.9f, 30L, 40L, 5L);

        assertEquals(2, point.getId());
        assertEquals("full", point.getRegionId());
        assertEquals(0.1f, point.getU(), 0f);
        assertEquals(0.9f, point.getV(), 0f);
        assertEquals(0f, point.getDiameterDpOverride(), 0f);
        assertTrue(point.isEnabled());
        assertEquals(PointDisabledReason.NONE, point.getDisabledReason());
        assertEquals(30L, point.getTimestamp());
        assertEquals(40L, point.getDurationMs());
        assertEquals(5L, point.getDisplayGeneration());
    }

    @Test
    public void copyMethodsChangeOnlyRequestedState() {
        ProfilePoint point = new ProfilePoint(
                3, "full", 0.2f, 0.4f, 36f, false,
                PointDisabledReason.NEEDS_REVIEW, 50L, 60L, 7L);

        ProfilePoint resized = point.withDiameterDpOverride(72f);
        ProfilePoint enabled = point.withEnabled(true);

        assertEquals(72f, resized.getDiameterDpOverride(), 0f);
        assertFalse(resized.isEnabled());
        assertEquals(PointDisabledReason.NEEDS_REVIEW, resized.getDisabledReason());
        assertEquals(point.getRegionId(), resized.getRegionId());
        assertEquals(point.getU(), resized.getU(), 0f);
        assertEquals(point.getV(), resized.getV(), 0f);
        assertEquals(point.getDisplayGeneration(), resized.getDisplayGeneration());

        assertTrue(enabled.isEnabled());
        assertEquals(PointDisabledReason.NONE, enabled.getDisabledReason());
        assertEquals(point.getDiameterDpOverride(), enabled.getDiameterDpOverride(), 0f);
        assertEquals(point.getTimestamp(), enabled.getTimestamp());
        assertEquals(point.getDurationMs(), enabled.getDurationMs());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullRegionId() {
        ProfilePoint.enabled(1, null, 0.2f, 0.3f, 10L, 20L, 1L);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullDisabledReason() {
        new ProfilePoint(1, "full", 0.2f, 0.3f, 0f, false,
                null, 10L, 20L, 1L);
    }

    @Test
    public void screenProfileClampsDiameterAndDefensivelyCopiesLists() {
        ProfilePoint point = ProfilePoint.enabled(1, "full", 0.2f, 0.3f, 10L, 20L, 1L);
        DisplayRegion region = new DisplayRegion("full", new IntRect(0, 0, 100, 200));
        UnsafeArea unsafeArea = new UnsafeArea(
                new IntRect(40, 0, 60, 200), PointDisabledReason.HINGE);
        List<ProfilePoint> points = new ArrayList<>(Collections.singletonList(point));
        List<String> fingerprints = new ArrayList<>(Collections.singletonList("display-A"));
        List<DisplayRegion> regions = new ArrayList<>(Collections.singletonList(region));
        List<UnsafeArea> unsafeAreas = new ArrayList<>(Collections.singletonList(unsafeArea));

        ScreenProfile profile = new ScreenProfile(
                "inner", ProfileKind.INNER, 10f,
                points, fingerprints, regions, unsafeAreas);

        points.clear();
        fingerprints.clear();
        regions.clear();
        unsafeAreas.clear();

        assertEquals("inner", profile.getId());
        assertEquals(ProfileKind.INNER, profile.getKind());
        assertEquals(24f, profile.getGlobalDiameterDp(), 0f);
        assertSame(point, profile.getPoints().get(0));
        assertEquals("display-A", profile.getFingerprints().get(0));
        assertSame(region, profile.getReferenceRegions().get(0));
        assertSame(unsafeArea, profile.getReferenceUnsafeAreas().get(0));
        assertListIsUnmodifiable(profile.getPoints());
        assertListIsUnmodifiable(profile.getFingerprints());
        assertListIsUnmodifiable(profile.getReferenceRegions());
        assertListIsUnmodifiable(profile.getReferenceUnsafeAreas());

        ScreenProfile maximum = new ScreenProfile(
                "outer", ProfileKind.OUTER, 250f,
                Collections.<ProfilePoint>emptyList(),
                Collections.<String>emptyList(),
                Collections.<DisplayRegion>emptyList(),
                Collections.<UnsafeArea>emptyList());
        assertEquals(200f, maximum.getGlobalDiameterDp(), 0f);
    }

    @Test
    public void profileDocumentCopiesMapsAndCopyMethodsLeaveOriginalUnchanged() {
        ScreenProfile inner = emptyProfile("inner", ProfileKind.INNER);
        ScreenProfile outer = emptyProfile("outer", ProfileKind.OUTER);
        Map<String, ScreenProfile> profiles = new LinkedHashMap<>();
        profiles.put(inner.getId(), inner);
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("display-A", "inner");

        ProfileDocument original = new ProfileDocument(9, profiles, bindings);
        profiles.clear();
        bindings.clear();

        ProfileDocument withProfile = original.withProfile(outer);
        ProfileDocument withBinding = withProfile.withManualBinding("display-B", "outer");

        assertEquals(9, original.getSchemaVersion());
        assertSame(inner, original.getProfiles().get("inner"));
        assertEquals("inner", original.getManualBindings().get("display-A"));
        assertFalse(original.getProfiles().containsKey("outer"));
        assertFalse(original.getManualBindings().containsKey("display-B"));
        assertSame(outer, withProfile.getProfiles().get("outer"));
        assertEquals("outer", withBinding.getManualBindings().get("display-B"));
        assertMapIsUnmodifiable(original.getProfiles());
        assertMapIsUnmodifiable(original.getManualBindings());
    }

    @Test(expected = NullPointerException.class)
    public void screenProfileRejectsNullId() {
        new ScreenProfile(
                null, ProfileKind.INNER, 40f,
                Collections.<ProfilePoint>emptyList(),
                Collections.<String>emptyList(),
                Collections.<DisplayRegion>emptyList(),
                Collections.<UnsafeArea>emptyList());
    }

    @Test(expected = NullPointerException.class)
    public void profileDocumentRejectsNullProfiles() {
        new ProfileDocument(2, null, Collections.<String, String>emptyMap());
    }

    private static ScreenProfile emptyProfile(String id, ProfileKind kind) {
        return new ScreenProfile(
                id, kind, 40f,
                Collections.<ProfilePoint>emptyList(),
                Collections.<String>emptyList(),
                Collections.<DisplayRegion>emptyList(),
                Collections.<UnsafeArea>emptyList());
    }

    private static void assertListIsUnmodifiable(List<?> values) {
        try {
            values.clear();
            fail("Expected an unmodifiable list");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertMapIsUnmodifiable(Map<?, ?> values) {
        try {
            values.clear();
            fail("Expected an unmodifiable map");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
