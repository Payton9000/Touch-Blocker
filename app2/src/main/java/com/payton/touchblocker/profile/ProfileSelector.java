package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;

import java.util.Map;

public final class ProfileSelector {
    public ProfileSelection select(DisplaySnapshot snapshot, ProfileDocument document) {
        String stableKey = snapshot.getStableKey();
        Map<String, ScreenProfile> profiles = document.getProfiles();

        String manualProfileId = document.getManualBindings().get(stableKey);
        if (manualProfileId != null && profiles.containsKey(manualProfileId)) {
            return matched(manualProfileId, ProfileSelection.Reason.MANUAL_BINDING);
        }

        String exactProfileId = null;
        int exactMatches = 0;
        for (ScreenProfile profile : profiles.values()) {
            if (profile.getFingerprints().contains(stableKey)) {
                exactMatches++;
                exactProfileId = profile.getId();
            }
        }
        if (exactMatches == 1) {
            return matched(exactProfileId, ProfileSelection.Reason.EXACT_FINGERPRINT);
        }
        if (exactMatches > 1) {
            return ambiguous();
        }

        ProfileKind suggestedKind = snapshot.getSuggestedKind();
        if (suggestedKind == null) {
            return unknown();
        }

        String suggestedProfileId = null;
        int suggestedMatches = 0;
        for (ScreenProfile profile : profiles.values()) {
            if (profile.getKind() == suggestedKind) {
                suggestedMatches++;
                suggestedProfileId = profile.getId();
            }
        }
        if (suggestedMatches == 1) {
            return matched(suggestedProfileId, ProfileSelection.Reason.SUGGESTED_KIND);
        }
        if (suggestedMatches > 1) {
            return ambiguous();
        }
        return unknown();
    }

    private static ProfileSelection matched(
            String profileId,
            ProfileSelection.Reason reason
    ) {
        return new ProfileSelection(ProfileSelection.Status.MATCHED, reason, profileId);
    }

    private static ProfileSelection ambiguous() {
        return new ProfileSelection(
                ProfileSelection.Status.AMBIGUOUS,
                ProfileSelection.Reason.AMBIGUOUS,
                null
        );
    }

    private static ProfileSelection unknown() {
        return new ProfileSelection(
                ProfileSelection.Status.UNKNOWN,
                ProfileSelection.Reason.NO_MATCH,
                null
        );
    }
}
