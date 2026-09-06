package com.payton.touchblocker.profile;

import java.util.Map;

/**
 * Carries manual profile bindings over from the superseded profile preference file.
 *
 * <p>Two preference files used to hold a {@code profiles_v2_json} document: the main screen wrote
 * {@code touch_blocker_profile_prefs} while the recorder, the point manager, the overlay service
 * and the boot receiver all used {@code touch_blocker_prefs}. Recorded points therefore only ever
 * landed in the latter, which is why that one is now canonical for everybody.
 *
 * <p>The one thing the old file held exclusively is the user's Inner/Outer profile choice, since
 * that was saved from the main screen. This copies those bindings across once so the choice is not
 * silently reset. Points are never copied: the old file's document could only contain profiles the
 * main screen itself minted, and adopting them would resurrect empty duplicates.
 */
public final class ProfileStoreMigration {
    /** The preference file the main screen used before the namespaces were unified. */
    public static final String LEGACY_PROFILE_PREFS = "touch_blocker_profile_prefs";

    /** Marks the carry-over as done so a later user edit is never overwritten by it. */
    public static final String KEY_BINDINGS_MERGED = "profile_prefs_bindings_merged";

    private ProfileStoreMigration() {
    }

    /**
     * Merges bindings from {@code legacyStore} into {@code canonical}, keeping any binding the
     * canonical document already has.
     *
     * @return the document to persist, or {@code null} when there is nothing to change.
     */
    public static ProfileDocument mergeLegacyBindings(
            ProfileDocument canonical,
            KeyValueStore canonicalStore,
            KeyValueStore legacyStore,
            ProfileJsonCodec codec) {
        if (canonical == null || canonicalStore == null || legacyStore == null || codec == null) {
            return null;
        }
        if (canonicalStore.getBoolean(KEY_BINDINGS_MERGED, false)) {
            return null;
        }
        ProfileJsonCodec.DecodeResult legacy = codec.decode(
                legacyStore.getString(ProfileRepository.KEY_CURRENT, null));
        if (!legacy.isSuccess()) {
            return null;
        }

        ProfileDocument merged = canonical;
        boolean changed = false;
        for (Map.Entry<String, String> binding
                : legacy.getDocument().getManualBindings().entrySet()) {
            String stableKey = binding.getKey();
            String profileId = binding.getValue();
            // Only adopt a binding that still names a profile this document actually has, and
            // never clobber a binding the user has already made against the canonical store.
            if (merged.getManualBindings().containsKey(stableKey)
                    || !merged.getProfiles().containsKey(profileId)) {
                continue;
            }
            merged = merged.withManualBinding(stableKey, profileId);
            changed = true;
        }
        return changed ? merged : null;
    }
}
