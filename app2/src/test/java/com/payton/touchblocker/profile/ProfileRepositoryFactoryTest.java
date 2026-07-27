package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class ProfileRepositoryFactoryTest {
    @Before
    public void resetProcessCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    @Test
    public void loadsManualBindingFromLegacyProfileNamespaceWithoutMigrating() throws Exception {
        InMemoryStore legacyProfileStore = new InMemoryStore();
        InMemoryStore pointStore = new InMemoryStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        ScreenProfile inner = TestFixtures.profile("inner", ProfileKind.INNER, "current-display");
        ScreenProfile selected = TestFixtures.profile("outer", ProfileKind.OUTER, "outer-display");
        ProfileDocument legacyDocument = TestFixtures.document(inner, selected)
                .withManualBinding("current-display", selected.getId());
        String encodedLegacyDocument = codec.encode(legacyDocument);
        legacyProfileStore.put(ProfileRepository.KEY_CURRENT, encodedLegacyDocument);

        Map<String, KeyValueStore> stores = new HashMap<>();
        stores.put("touch_blocker_profile_prefs", legacyProfileStore);
        stores.put("touch_blocker_prefs", pointStore);
        ProfileRepository repository = ProfileRepositoryFactory.create(stores::get);

        ProfileRepository.LoadResult loaded = repository.migrateIfNeeded(
                "[{\"id\":7,\"x\":500.0,\"y\":900.0,\"timestamp\":11,"
                        + "\"duration\":22,\"enabled\":true,\"sizeOverride\":-1,"
                        + "\"nx\":0.5,\"ny\":0.5,\"rotation\":0,"
                        + "\"baseWidth\":1000,\"baseHeight\":1800}]",
                120,
                TestFixtures.snapshot("current-display", ProfileKind.INNER),
                ProfileKind.INNER);
        ActiveProfileResolver.Resolution resolution = ActiveProfileResolver.resolve(
                repository,
                loaded.getDocument(),
                TestFixtures.snapshot("current-display", ProfileKind.INNER));

        assertTrue(loaded.isSuccess());
        assertFalse(resolution.isCreated());
        assertEquals("outer", resolution.getProfile().getId());
        assertEquals(2, loaded.getDocument().getProfiles().size());
        assertEquals("outer", loaded.getDocument().getManualBindings().get("current-display"));
        assertEquals(
                encodedLegacyDocument,
                legacyProfileStore.getString(ProfileRepository.KEY_CURRENT, null));
        assertEquals(0, legacyProfileStore.getCommitCount());
        assertEquals(0, pointStore.getCommitCount());
    }

    private static final class InMemoryStore implements KeyValueStore {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();
        private int commitCount;

        void put(String key, String value) {
            strings.put(key, value);
        }

        int getCommitCount() {
            return commitCount;
        }

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
            commitCount++;
            return true;
        }
    }
}
