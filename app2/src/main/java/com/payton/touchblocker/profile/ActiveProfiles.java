package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;

import java.util.Collections;
import java.util.Locale;

/**
 * Selects the {@link ScreenProfile} that should be active for the current display, creating
 * a fresh fingerprinted profile the first time an unrecognized display is seen.
 */
public final class ActiveProfiles {
    private static final float NEW_PROFILE_DIAMETER_DP = 48f;

    private ActiveProfiles() {
    }

    public static Active ensure(ProfileDocument document, DisplaySnapshot snapshot) {
        ProfileSelection selection = new ProfileSelector().select(snapshot, document);
        switch (selection.getStatus()) {
            case MATCHED:
                return new Active(document, selection.getProfileId(), false);
            case UNKNOWN:
                ScreenProfile created = createProfile(snapshot);
                ProfileDocument updated = document.withProfile(created);
                return new Active(updated, created.getId(), true);
            case AMBIGUOUS:
            default:
                return null;
        }
    }

    private static ScreenProfile createProfile(DisplaySnapshot snapshot) {
        ProfileKind kind = snapshot.getSuggestedKind() != null
                ? snapshot.getSuggestedKind()
                : ProfileKind.OUTER;
        String stableKey = snapshot.getStableKey();
        String id = kind.name().toLowerCase(Locale.US)
                + "-" + Integer.toHexString(stableKey.hashCode());
        return new ScreenProfile(
                id,
                kind,
                NEW_PROFILE_DIAMETER_DP,
                Collections.<ProfilePoint>emptyList(),
                Collections.singletonList(stableKey),
                snapshot.getRegions(),
                snapshot.getUnsafeAreas());
    }

    public static final class Active {
        private final ProfileDocument document;
        private final String profileId;
        private final boolean created;

        private Active(ProfileDocument document, String profileId, boolean created) {
            this.document = document;
            this.profileId = profileId;
            this.created = created;
        }

        public ProfileDocument getDocument() {
            return document;
        }

        public String getProfileId() {
            return profileId;
        }

        public boolean isCreated() {
            return created;
        }
    }
}
