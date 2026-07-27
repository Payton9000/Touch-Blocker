package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.UnsafeArea;

/** Determines whether manual screen-profile selection is useful in the current layout. */
public final class FoldableUi {
    private FoldableUi() {
    }

    public static boolean shouldShowProfileControls(
            ProfileDocument document,
            DisplaySnapshot snapshot
    ) {
        if (document == null) {
            throw new NullPointerException("document == null");
        }
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }
        if (document.getProfiles().size() > 1 || snapshot.getRegions().size() > 1) {
            return true;
        }
        for (UnsafeArea area : snapshot.getUnsafeAreas()) {
            if (area.getReason() == PointDisabledReason.HINGE) {
                return true;
            }
        }
        return false;
    }
}
