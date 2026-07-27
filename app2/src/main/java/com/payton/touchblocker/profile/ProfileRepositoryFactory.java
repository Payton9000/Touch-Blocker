package com.payton.touchblocker.profile;

/** Opens profile documents from their long-lived preference namespace. */
public final class ProfileRepositoryFactory {
    public static final String PROFILE_PREFS = "touch_blocker_profile_prefs";

    private ProfileRepositoryFactory() {
    }

    public static ProfileRepository create(KeyValueStoreProvider provider) {
        if (provider == null) {
            throw new NullPointerException("provider == null");
        }
        KeyValueStore store = provider.open(PROFILE_PREFS);
        if (store == null) {
            throw new IllegalStateException("No store for " + PROFILE_PREFS);
        }
        return new ProfileRepository(store, new ProfileJsonCodec());
    }

    public interface KeyValueStoreProvider {
        KeyValueStore open(String preferenceName);
    }
}
