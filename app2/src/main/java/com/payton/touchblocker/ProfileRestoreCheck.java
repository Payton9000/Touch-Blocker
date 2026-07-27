package com.payton.touchblocker;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ServiceStartCheck;

/** Shared profile-backed check used before restoring an overlay after a system restart. */
final class ProfileRestoreCheck {
    private ProfileRestoreCheck() {
    }

    static boolean hasBlockablePoints(
            ProfileRepository repository,
            String legacyPointsJson,
            int legacyGlobalSizePx,
            DisplaySnapshot snapshot
    ) {
        if (snapshot == null) {
            return false;
        }
        ProfileRepository.LoadResult loaded = repository.migrateIfNeeded(
                legacyPointsJson,
                legacyGlobalSizePx,
                snapshot,
                snapshot.getSuggestedKind() == null
                        ? ProfileKind.OUTER : snapshot.getSuggestedKind());
        return loaded.isSuccess()
                && ServiceStartCheck.hasBlockablePoints(loaded.getDocument(), snapshot);
    }
}
