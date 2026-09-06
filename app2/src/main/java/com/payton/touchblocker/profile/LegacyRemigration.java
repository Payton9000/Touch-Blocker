package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;

/**
 * Repairs profiles produced by the first release of the v2 migration.
 *
 * <p>That migration stored only {@code u}/{@code v} fractions and no natural anchor, so every
 * migrated point was resolved by stretching its fraction across the current rotated bounds instead
 * of rotating it. Two things followed: a point drifted to a different physical spot on every screen
 * rotation, and whichever rotation the device happened to be in during the upgrade got baked into
 * the stored fractions -- upgrading while held at 180 degrees left every point mirrored.
 *
 * <p>The original pre-migration records are still kept under
 * {@link ProfileRepository#KEY_LEGACY_BACKUP}, so the damage is recoverable: re-running the now
 * rotation-anchored migration against that backup reproduces the points the user actually recorded.
 * This runs once, guarded by {@link #KEY_REMIGRATION_COMPLETE}, and leaves the profile untouched
 * when there is no backup or nothing needs repair.
 */
public final class LegacyRemigration {
    /** Set once the repair has been attempted, so a later user edit is never overwritten. */
    public static final String KEY_REMIGRATION_COMPLETE = "profiles_v2_anchor_repair_complete";

    private LegacyRemigration() {
    }

    /**
     * @return {@code true} when a repaired document was written, {@code false} when nothing needed
     *     repair or no repair was possible.
     */
    public static boolean repairIfNeeded(
            ProfileRepository repository,
            KeyValueStore store,
            String legacyPointsJson,
            int legacyGlobalPx,
            DisplaySnapshot snapshot) {
        if (repository == null || store == null || snapshot == null) {
            return false;
        }
        if (store.getBoolean(KEY_REMIGRATION_COMPLETE, false)) {
            return false;
        }
        // Mark first: a repair that cannot succeed must not be retried on every refresh.
        store.putStringsAndBooleans(
                java.util.Collections.<String, String>emptyMap(),
                java.util.Collections.singletonMap(KEY_REMIGRATION_COMPLETE, Boolean.TRUE));

        ProfileRepository.LoadResult loaded = repository.load();
        if (!loaded.isSuccess() || !needsRepair(loaded.getDocument())) {
            return false;
        }
        if (legacyPointsJson == null || legacyPointsJson.length() == 0
                || "[]".equals(legacyPointsJson.trim())) {
            // Nothing to rebuild from; the anchorless points stay as they are rather than being
            // thrown away, since a drifting blocker is still better than none.
            return false;
        }
        return repository.remigrateFromLegacyBackup(
                legacyPointsJson, legacyGlobalPx, snapshot);
    }

    /**
     * A profile needs repair when it has enabled points that carry no natural anchor: every point
     * the current recorder writes has one, so an anchorless point can only have come from the
     * original migration.
     */
    static boolean needsRepair(ProfileDocument document) {
        if (document == null) {
            return false;
        }
        for (ScreenProfile profile : document.getProfiles().values()) {
            for (ProfilePoint point : profile.getPoints()) {
                if (!point.hasNaturalAnchor()) {
                    return true;
                }
            }
        }
        return false;
    }
}
