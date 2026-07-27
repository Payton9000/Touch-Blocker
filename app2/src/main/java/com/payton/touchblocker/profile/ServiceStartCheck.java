package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.geometry.CoordinateTransformer;

/**
 * Decides whether the overlay service has anything to block on the current display: a
 * profile must be unambiguously selected for the snapshot, and at least one of its enabled
 * points must resolve to an actual position on that snapshot.
 */
public final class ServiceStartCheck {
    private ServiceStartCheck() {
    }

    public static boolean hasBlockablePoints(ProfileDocument document, DisplaySnapshot snapshot) {
        ProfileSelection selection = new ProfileSelector().select(snapshot, document);
        if (selection.getStatus() != ProfileSelection.Status.MATCHED) {
            return false;
        }
        ScreenProfile profile = document.getProfiles().get(selection.getProfileId());
        if (profile == null) {
            return false;
        }
        for (ProfilePoint point : profile.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride()
                    : profile.getGlobalDiameterDp();
            if (CoordinateTransformer.resolve(snapshot, point, diameterDp) != null) {
                return true;
            }
        }
        return false;
    }
}
