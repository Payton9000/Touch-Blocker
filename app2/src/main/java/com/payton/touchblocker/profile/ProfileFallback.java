package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;

import java.util.Map;

/**
 * Picks a usable profile when the current display does not match any stored fingerprint.
 *
 * <p>A display's fingerprint changes for reasons that have nothing to do with the user's intent:
 * folding or unfolding, or changing the system display-size setting (which moves {@code
 * densityDpi}). Before this existed, an unmatched display made {@code ActiveProfiles} mint an
 * empty profile, which the overlay service read as "nothing to block" and tore every window down
 * while still reporting itself as ON. The blockers silently stopped working with no way for the
 * user to tell, let alone recover.
 *
 * <p>Reusing the closest same-kind profile keeps blocking alive across those transitions. Points
 * are stored as rotation-independent natural anchors normalized to 0..1, so they carry over to a
 * different display size sensibly rather than being lost.
 */
public final class ProfileFallback {
    private ProfileFallback() {
    }

    /**
     * @return the id of the best profile to reuse for {@code snapshot}, or {@code null} when no
     *     stored profile has any blockable points and there is genuinely nothing to fall back to.
     */
    public static String selectFallbackProfileId(
            ProfileDocument document, DisplaySnapshot snapshot) {
        if (document == null || snapshot == null) {
            return null;
        }
        ProfileKind preferredKind = snapshot.getSuggestedKind();
        String sameKindBest = null;
        int sameKindPoints = 0;
        String anyKindBest = null;
        int anyKindPoints = 0;

        for (Map.Entry<String, ScreenProfile> entry : document.getProfiles().entrySet()) {
            ScreenProfile profile = entry.getValue();
            int enabledPoints = countEnabledPoints(profile);
            if (enabledPoints == 0) {
                continue;
            }
            if (preferredKind != null && profile.getKind() == preferredKind
                    && enabledPoints > sameKindPoints) {
                sameKindBest = entry.getKey();
                sameKindPoints = enabledPoints;
            }
            if (enabledPoints > anyKindPoints) {
                anyKindBest = entry.getKey();
                anyKindPoints = enabledPoints;
            }
        }
        return sameKindBest != null ? sameKindBest : anyKindBest;
    }

    private static int countEnabledPoints(ScreenProfile profile) {
        int count = 0;
        for (ProfilePoint point : profile.getPoints()) {
            if (point.isEnabled()) {
                count++;
            }
        }
        return count;
    }
}
