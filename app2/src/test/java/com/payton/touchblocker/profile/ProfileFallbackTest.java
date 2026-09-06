package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers the recovery path for a display whose fingerprint no longer matches any stored profile.
 *
 * <p>Folding a foldable, or changing the system display-size setting, changes the fingerprint
 * without the user doing anything to their blocking points. Before this fallback existed the
 * overlay service read the resulting empty profile as "nothing to block", removed every window,
 * and still reported itself as ON.
 */
public class ProfileFallbackTest {
    @Test
    public void prefersTheProfileOfTheSuggestedKindThatHasPoints() {
        ScreenProfile innerWithPoints = profile("inner-1", ProfileKind.INNER, 2);
        ScreenProfile outerWithPoints = profile("outer-1", ProfileKind.OUTER, 3);
        ProfileDocument document = document(innerWithPoints, outerWithPoints);

        assertEquals(
                "inner-1",
                ProfileFallback.selectFallbackProfileId(
                        document, snapshot(ProfileKind.INNER)));
    }

    /**
     * The exact folded-device case reproduced on a Pixel Fold: the display matches a profile that
     * exists but is empty, while the points live in the profile for the other posture.
     */
    @Test
    public void fallsBackAcrossKindsWhenTheSuggestedKindHasNoPoints() {
        ProfileDocument document = document(
                profile("outer-empty", ProfileKind.OUTER, 0),
                profile("inner-full", ProfileKind.INNER, 4));

        assertEquals(
                "inner-full",
                ProfileFallback.selectFallbackProfileId(
                        document, snapshot(ProfileKind.OUTER)));
    }

    @Test
    public void picksTheProfileWithTheMostEnabledPoints() {
        ProfileDocument document = document(
                profile("outer-few", ProfileKind.OUTER, 1),
                profile("outer-many", ProfileKind.OUTER, 5));

        assertEquals(
                "outer-many",
                ProfileFallback.selectFallbackProfileId(
                        document, snapshot(ProfileKind.OUTER)));
    }

    /** Disabled points are not blockable, so a profile holding only those is not a fallback. */
    @Test
    public void ignoresProfilesWhosePointsAreAllDisabled() {
        ProfileDocument document = document(disabledOnlyProfile("outer-disabled"));

        assertNull(ProfileFallback.selectFallbackProfileId(
                document, snapshot(ProfileKind.OUTER)));
    }

    @Test
    public void returnsNullWhenNothingIsBlockableAnywhere() {
        assertNull(ProfileFallback.selectFallbackProfileId(
                document(profile("empty", ProfileKind.OUTER, 0)),
                snapshot(ProfileKind.OUTER)));
        assertNull(ProfileFallback.selectFallbackProfileId(
                document(), snapshot(ProfileKind.OUTER)));
        assertNull(ProfileFallback.selectFallbackProfileId(null, snapshot(ProfileKind.OUTER)));
        assertNull(ProfileFallback.selectFallbackProfileId(
                document(profile("p", ProfileKind.OUTER, 1)), null));
    }

    private static ProfileDocument document(ScreenProfile... profiles) {
        Map<String, ScreenProfile> byId = new LinkedHashMap<>();
        for (ScreenProfile profile : profiles) {
            byId.put(profile.getId(), profile);
        }
        return new ProfileDocument(2, byId, Collections.<String, String>emptyMap());
    }

    private static ScreenProfile profile(String id, ProfileKind kind, int enabledPoints) {
        List<ProfilePoint> points = new ArrayList<>();
        for (int index = 0; index < enabledPoints; index++) {
            points.add(ProfilePoint.enabled(
                    index + 1, "full", 0.5f, 0.5f, 0L, 0L, 1L));
        }
        return screenProfile(id, kind, points);
    }

    private static ScreenProfile disabledOnlyProfile(String id) {
        ProfilePoint disabled = ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L)
                .disabled(PointDisabledReason.NEEDS_REVIEW);
        return screenProfile(id, ProfileKind.OUTER, Collections.singletonList(disabled));
    }

    private static ScreenProfile screenProfile(
            String id, ProfileKind kind, List<ProfilePoint> points) {
        return new ScreenProfile(
                id,
                kind,
                30f,
                points,
                Collections.singletonList("fingerprint-" + id),
                Collections.singletonList(new DisplayRegion(
                        "full", new IntRect(0, 0, 1080, 2400))),
                Collections.<UnsafeArea>emptyList());
    }

    private static DisplaySnapshot snapshot(ProfileKind suggestedKind) {
        return TestFixtures.snapshot("unmatched-display", suggestedKind);
    }
}
