package com.payton.touchblocker.profile;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

public class ProfileRepositoryCacheTest {
    private static final String VALID =
            "{\"schemaVersion\":2,\"profiles\":[],\"manualBindings\":{}}";

    private static final class CountingStore implements KeyValueStore {
        final Map<String, String> strings = new HashMap<>();
        int stringReads;

        @Override public String getString(String key, String defaultValue) {
            stringReads++;
            return strings.containsKey(key) ? strings.get(key) : defaultValue;
        }
        @Override public boolean getBoolean(String key, boolean defaultValue) { return false; }
        @Override public boolean putStringsAndBooleans(
                Map<String, String> s, Map<String, Boolean> b) {
            strings.putAll(s);
            return true;
        }
    }

    @Before
    public void resetCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    @Test
    public void secondLoadServesCacheWithoutRereading() {
        CountingStore store = new CountingStore();
        store.strings.put(ProfileRepository.KEY_CURRENT, VALID);
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());

        assertTrue(repository.load().isSuccess());
        int readsAfterFirst = store.stringReads;
        assertTrue(repository.load().isSuccess());

        assertEquals(readsAfterFirst, store.stringReads);
    }

    @Test
    public void saveBumpsRevisionAndRefreshesCache() {
        CountingStore store = new CountingStore();
        store.strings.put(ProfileRepository.KEY_CURRENT, VALID);
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());
        ProfileDocument document = repository.load().getDocument();
        long before = repository.getRevision();

        assertTrue(repository.save(document));

        assertTrue(repository.getRevision() > before);
        assertTrue(repository.load().isSuccess());
    }
}
