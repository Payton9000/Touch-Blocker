package com.payton.touchblocker;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestFixtures {
    private TestFixtures() {
    }

    public static DisplaySnapshot singleRegion(int width, int height) {
        return snapshot(new DisplayRegion("full", new IntRect(0, 0, width, height)));
    }

    public static DisplaySnapshot snapshot(DisplayRegion... regions) {
        IntRect firstBounds = regions[0].getBounds();
        int width = firstBounds.getRight();
        int height = firstBounds.getBottom();
        for (DisplayRegion region : regions) {
            width = Math.max(width, region.getBounds().getRight());
            height = Math.max(height, region.getBounds().getBottom());
        }
        return new DisplaySnapshot(
                "test-display", 0, width, height, 0, 1f, 1L, null,
                Arrays.asList(regions), Collections.<UnsafeArea>emptyList());
    }

    public static DisplaySnapshot snapshot(String stableKey, ProfileKind suggestedKind) {
        return new DisplaySnapshot(
                stableKey, 0, 1000, 1800, 0, 1f, 1L, suggestedKind,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());
    }

    public static DisplaySnapshot snapshotWithDensity(int width, int height, float density) {
        return new DisplaySnapshot(
                "test-display", 0, width, height, 0, density, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, width, height))),
                Collections.<UnsafeArea>emptyList());
    }

    public static DisplaySnapshot withUnsafeRect(
            int width, int height, IntRect unsafe, PointDisabledReason reason) {
        return new DisplaySnapshot(
                "test-display", 0, width, height, 0, 1f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, width, height))),
                Collections.singletonList(new UnsafeArea(unsafe, reason)));
    }

    public static ScreenProfile profile(String id, ProfileKind kind, String fingerprint) {
        return new ScreenProfile(
                id, kind, 40f,
                Collections.<ProfilePoint>emptyList(),
                Collections.singletonList(fingerprint),
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());
    }

    public static ScreenProfile profileWithPoint(ProfilePoint point) {
        return new ScreenProfile(
                "profile", ProfileKind.INNER, 40f,
                Collections.singletonList(point),
                Collections.singletonList("test-display"),
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());
    }

    public static ProfileDocument document(ScreenProfile... profiles) {
        Map<String, ScreenProfile> map = new LinkedHashMap<>();
        for (ScreenProfile profile : profiles) {
            map.put(profile.getId(), profile);
        }
        return new ProfileDocument(2, map, Collections.<String, String>emptyMap());
    }
}
