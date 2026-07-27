package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

public class ActiveProfileResolverTest {
    @Before
    public void resetProcessCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    @Test
    public void unknownEnsurePersistsCreatedProfile() {
        InMemoryStore store = new InMemoryStore();
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());
        ProfileDocument document = TestFixtures.document();

        ActiveProfileResolver.Resolution resolution = ActiveProfileResolver.resolve(
                repository, document, TestFixtures.snapshot("new-display", ProfileKind.OUTER));

        assertTrue(resolution.isCreated());
        assertEquals(ProfileSelection.Status.UNKNOWN, resolution.getSelection().getStatus());
        assertNotNull(resolution.getProfile());

        ProfileRepository.clearProcessCacheForTest();
        ProfileRepository.LoadResult reloaded = new ProfileRepository(
                store, new ProfileJsonCodec()).load();
        assertTrue(reloaded.isSuccess());
        assertTrue(reloaded.getDocument().getProfiles().containsKey(
                resolution.getProfile().getId()));
    }

    @Test
    public void matchedSelectionUsesManuallyBoundProfile() {
        ScreenProfile automaticallyMatched = TestFixtures.profile(
                "inner", ProfileKind.INNER, "inner-display");
        ScreenProfile selected = TestFixtures.profile(
                "outer", ProfileKind.OUTER, "outer-display");
        ProfileDocument document = TestFixtures.document(automaticallyMatched, selected)
                .withManualBinding("inner-display", selected.getId());

        ActiveProfileResolver.Resolution resolution = ActiveProfileResolver.resolve(
                new ProfileRepository(new InMemoryStore(), new ProfileJsonCodec()),
                document,
                TestFixtures.snapshot("inner-display", ProfileKind.INNER));

        assertFalse(resolution.isCreated());
        assertEquals(ProfileSelection.Status.MATCHED, resolution.getSelection().getStatus());
        assertSame(selected, resolution.getProfile());
    }

    private static final class InMemoryStore implements KeyValueStore {
        private final Map<String, String> strings = new HashMap<>();

        @Override
        public String getString(String key, String defaultValue) {
            return strings.containsKey(key) ? strings.get(key) : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return defaultValue;
        }

        @Override
        public boolean putStringsAndBooleans(
                Map<String, String> newStrings,
                Map<String, Boolean> booleans
        ) {
            strings.putAll(newStrings);
            return true;
        }
    }
}
