package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Pins the storage namespace every profile reader and writer must agree on.
 *
 * <p>The static, process-wide cache inside {@link ProfileRepository} is keyed by nothing at all,
 * so two repositories opened over two different preference files silently serve each other's
 * documents. That makes a namespace mismatch invisible in a warm process and only visible after
 * a cold start, which is exactly the kind of bug that survives manual testing. These tests fail
 * if a caller is ever pointed at a second file again.
 */
public class ProfileStorageNamespaceTest {
    private static final String PROFILE_WITH_POINT =
            "{\"schemaVersion\":2,\"profiles\":[{\"id\":\"outer-1\",\"kind\":\"OUTER\","
                    + "\"globalDiameterDp\":30.0,\"fingerprints\":[\"fp\"],"
                    + "\"referenceRegions\":[{\"id\":\"full\",\"bounds\":{\"left\":0,\"top\":0,"
                    + "\"right\":1080,\"bottom\":2400}}],\"referenceUnsafeAreas\":[],"
                    + "\"points\":[{\"id\":1,\"regionId\":\"full\",\"u\":0.5,\"v\":0.5,"
                    + "\"naturalU\":0.5,\"naturalV\":0.5,\"diameterDpOverride\":0.0,"
                    + "\"enabled\":true,\"disabledReason\":\"NONE\",\"timestamp\":1,"
                    + "\"durationMs\":1,\"displayGeneration\":1}]}],\"manualBindings\":{}}";

    private static final String EMPTY_DOCUMENT =
            "{\"schemaVersion\":2,\"profiles\":[],\"manualBindings\":{}}";

    @Before
    public void resetCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    /**
     * The factory's namespace is the one canonical name. If a caller hardcodes a different
     * preference file, its points live in a file nothing else reads.
     */
    @Test
    public void factoryNamespaceIsTheSingleCanonicalProfileStore() {
        assertEquals("touch_blocker_profile_prefs", ProfileRepositoryFactory.PROFILE_PREFS);
    }

    /**
     * Two repositories over two different files must not serve each other's documents. This is
     * the cold-start behaviour: whichever file a reader is actually pointed at is what it gets.
     * A warm process hides the mismatch behind the shared static cache, so a namespace bug looks
     * like it works until the process restarts.
     */
    @Test
    public void repositoriesOverDifferentFilesSeeDifferentDocuments() {
        MapStore canonical = new MapStore();
        canonical.strings.put(ProfileRepository.KEY_CURRENT, PROFILE_WITH_POINT);
        MapStore other = new MapStore();
        other.strings.put(ProfileRepository.KEY_CURRENT, EMPTY_DOCUMENT);

        ProfileRepository.LoadResult fromCanonical =
                new ProfileRepository(canonical, new ProfileJsonCodec()).load();
        assertTrue(fromCanonical.isSuccess());
        assertEquals(1, fromCanonical.getDocument().getProfiles().size());

        // A cold read of the other file must reflect that file, not the cached first one.
        ProfileRepository.clearProcessCacheForTest();
        ProfileRepository.LoadResult fromOther =
                new ProfileRepository(other, new ProfileJsonCodec()).load();
        assertTrue(fromOther.isSuccess());
        assertEquals(0, fromOther.getDocument().getProfiles().size());
    }

    /**
     * Documents the cache hazard itself: without an intervening reset, a repository over an
     * entirely different store still returns the previously cached document. Any two callers on
     * different namespaces therefore disagree only after a process restart.
     */
    @Test
    public void warmCacheServesTheFirstDocumentRegardlessOfStore() {
        MapStore canonical = new MapStore();
        canonical.strings.put(ProfileRepository.KEY_CURRENT, PROFILE_WITH_POINT);
        assertTrue(new ProfileRepository(canonical, new ProfileJsonCodec()).load().isSuccess());

        MapStore unrelated = new MapStore();
        unrelated.strings.put(ProfileRepository.KEY_CURRENT, EMPTY_DOCUMENT);
        ProfileRepository.LoadResult warm =
                new ProfileRepository(unrelated, new ProfileJsonCodec()).load();

        assertTrue(warm.isSuccess());
        assertEquals(
                "the process cache ignores which store a repository was built over",
                1, warm.getDocument().getProfiles().size());
    }

    private static final class MapStore implements KeyValueStore {
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
                Map<String, String> newStrings, Map<String, Boolean> booleans) {
            strings.putAll(newStrings);
            return true;
        }
    }
}
