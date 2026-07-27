package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.profile.KeyValueStore;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfileRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ProfileRestoreCheckTest {
    private static final String LEGACY_ENABLED_POINT =
            "[{\"id\":7,\"x\":500.0,\"y\":900.0,"
                    + "\"timestamp\":11,\"duration\":22,\"enabled\":true,"
                    + "\"sizeOverride\":-1,\"nx\":0.5,\"ny\":0.5,"
                    + "\"rotation\":0,\"baseWidth\":1000,\"baseHeight\":1800}]";

    @Before
    public void resetProcessCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    @Test
    public void legacyEnabledPointIsMigratedBeforeRestoreCheck() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());

        boolean blockable = ProfileRestoreCheck.hasBlockablePoints(
                repository,
                LEGACY_ENABLED_POINT,
                120,
                TestFixtures.snapshot("legacy-restore-display", null));

        assertTrue(blockable);
        assertTrue(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertEquals(
                ProfileKind.OUTER,
                repository.load().getDocument().getProfiles().values().iterator().next().getKind());
    }

    private static final class InMemoryKeyValueStore implements KeyValueStore {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();

        @Override
        public String getString(String key, String defaultValue) {
            return strings.containsKey(key) ? strings.get(key) : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return booleans.containsKey(key) ? booleans.get(key) : defaultValue;
        }

        @Override
        public boolean putStringsAndBooleans(
                Map<String, String> newStrings,
                Map<String, Boolean> newBooleans
        ) {
            strings.putAll(newStrings);
            booleans.putAll(newBooleans);
            return true;
        }
    }
}
