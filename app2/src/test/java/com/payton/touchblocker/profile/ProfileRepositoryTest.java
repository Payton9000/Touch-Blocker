package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplaySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

public class ProfileRepositoryTest {
    private static final String LEGACY_POINTS_KEY = "points_json";
    private static final String VALID_EMPTY_DOCUMENT =
            "{\"schemaVersion\":2,\"profiles\":[],\"manualBindings\":{}}";
    private static final String VALID_LEGACY_POINT =
            "{\"id\":7,\"x\":500.0,\"y\":900.0,"
            + "\"timestamp\":11,\"duration\":22,\"enabled\":true,"
            + "\"sizeOverride\":-1,\"nx\":0.5,\"ny\":0.5,"
            + "\"rotation\":0,\"baseWidth\":1000,\"baseHeight\":1800}";
    private static final String VALID_LEGACY_JSON = "[" + VALID_LEGACY_POINT + "]";

    // ProfileRepository now keeps a process-level static cache (Task 5) so that repeated
    // load() calls avoid re-reading the store. That cache is static/process-global, so it
    // must be reset before each test here or tests would observe documents cached by whichever
    // test happened to run earlier in this JVM. See ProfileRepositoryCacheTest for the cache's
    // own behavioral tests.
    @Before
    public void resetProcessCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    @Test
    public void malformedCurrentDocumentLoadsValidBackup() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(ProfileRepository.KEY_CURRENT, "{broken");
        store.put(ProfileRepository.KEY_BACKUP, VALID_EMPTY_DOCUMENT);
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());

        ProfileRepository.LoadResult result = repository.load();

        assertTrue(result.getError(), result.isSuccess());
        assertTrue(result.isLoadedFromBackup());
        assertEquals(0, result.getDocument().getProfiles().size());
    }

    @Test
    public void validCurrentDocumentWinsOverBackup() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(ProfileRepository.KEY_CURRENT, VALID_EMPTY_DOCUMENT);
        store.put(ProfileRepository.KEY_BACKUP, "{broken");

        ProfileRepository.LoadResult result =
                new ProfileRepository(store, new ProfileJsonCodec()).load();

        assertTrue(result.getError(), result.isSuccess());
        assertFalse(result.isLoadedFromBackup());
    }

    @Test
    public void loadReportsFailureWhenNeitherDocumentIsReadable() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(ProfileRepository.KEY_CURRENT, "{broken-current");
        store.put(ProfileRepository.KEY_BACKUP, "{broken-backup");

        ProfileRepository.LoadResult result =
                new ProfileRepository(store, new ProfileJsonCodec()).load();

        assertFalse(result.isSuccess());
        assertNull(result.getDocument());
        assertNotNull(result.getError());
    }

    @Test
    public void savingDocumentMovesPreviousCurrentToBackupInOneCommit() throws Exception {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        String documentA = codec.encode(TestFixtures.document(
                TestFixtures.profile("profile-a", ProfileKind.INNER, "display-a")));
        String documentB = codec.encode(TestFixtures.document(
                TestFixtures.profile("profile-b", ProfileKind.OUTER, "display-b")));
        store.put(ProfileRepository.KEY_CURRENT, documentA);

        boolean saved = new ProfileRepository(store, codec).save(
                codec.decode(documentB).getDocument());

        assertTrue(saved);
        assertEquals(documentB, store.getString(ProfileRepository.KEY_CURRENT, null));
        assertEquals(documentA, store.getString(ProfileRepository.KEY_BACKUP, null));
        assertEquals(1, store.getCommitCount());
        assertEquals(2, store.getLastStrings().size());
    }

    @Test
    public void failedSaveCommitDoesNotReportSuccessOrChangeCurrent() throws Exception {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        String documentA = codec.encode(TestFixtures.document(
                TestFixtures.profile("profile-a", ProfileKind.INNER, "display-a")));
        ProfileDocument documentB = TestFixtures.document(
                TestFixtures.profile("profile-b", ProfileKind.OUTER, "display-b"));
        store.put(ProfileRepository.KEY_CURRENT, documentA);
        store.setCommitSucceeds(false);

        boolean saved = new ProfileRepository(store, codec).save(documentB);

        assertFalse(saved);
        assertEquals(documentA, store.getString(ProfileRepository.KEY_CURRENT, null));
        assertNull(store.getString(ProfileRepository.KEY_BACKUP, null));
    }

    @Test
    public void migrationBacksUpExactLegacyBytesAndCommitsReadableV2BeforeFlag() {
        String legacyJson = "  [ {\"id\":7,\"x\":500.0,\"y\":900.0,"
                + "\"timestamp\":11,\"duration\":22,\"enabled\":true,"
                + "\"sizeOverride\":-1,\"nx\":0.5,\"ny\":0.5,"
                + "\"rotation\":0,\"baseWidth\":1000,\"baseHeight\":1800} ]  ";
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        store.observeMigrationCompletion(codec);

        ProfileRepository.LoadResult result = new ProfileRepository(store, codec)
                .migrateIfNeeded(
                        legacyJson,
                        120,
                        TestFixtures.snapshot("display-outer", ProfileKind.OUTER),
                        ProfileKind.OUTER);

        assertTrue(result.getError(), result.isSuccess());
        assertEquals(legacyJson, store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertTrue(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertTrue(store.wasReadableCurrentVisibleWhenCompletionWasWritten());
        assertTrue(codec.decode(store.getString(ProfileRepository.KEY_CURRENT, null)).isSuccess());
        assertEquals(1, store.getCommitCount());
    }

    @Test
    public void validCurrentWithMissingMigrationFlagIsReturnedWithoutOverwritingAnything()
            throws Exception {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        String current = codec.encode(TestFixtures.document(
                TestFixtures.profile("current", ProfileKind.INNER, "display-current")));
        String backup = codec.encode(TestFixtures.document(
                TestFixtures.profile("backup", ProfileKind.OUTER, "display-backup")));
        store.put(ProfileRepository.KEY_CURRENT, current);
        store.put(ProfileRepository.KEY_BACKUP, backup);

        ProfileRepository.LoadResult result = new ProfileRepository(store, codec)
                .migrateIfNeeded(
                        VALID_LEGACY_JSON,
                        120,
                        TestFixtures.singleRegion(1000, 1800),
                        ProfileKind.INNER);

        assertTrue(result.getError(), result.isSuccess());
        assertFalse(result.isLoadedFromBackup());
        assertEquals(current, codec.encode(result.getDocument()));
        assertEquals(current, store.getString(ProfileRepository.KEY_CURRENT, null));
        assertEquals(backup, store.getString(ProfileRepository.KEY_BACKUP, null));
        assertNull(store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertFalse(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertEquals(0, store.getCommitCount());
    }

    @Test
    public void validBackupWithMissingMigrationFlagIsReturnedWithoutOverwritingAnything()
            throws Exception {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        String malformedCurrent = "{broken-current";
        String backup = codec.encode(TestFixtures.document(
                TestFixtures.profile("backup", ProfileKind.OUTER, "display-backup")));
        store.put(ProfileRepository.KEY_CURRENT, malformedCurrent);
        store.put(ProfileRepository.KEY_BACKUP, backup);

        ProfileRepository.LoadResult result = new ProfileRepository(store, codec)
                .migrateIfNeeded(
                        VALID_LEGACY_JSON,
                        120,
                        TestFixtures.singleRegion(1000, 1800),
                        ProfileKind.INNER);

        assertTrue(result.getError(), result.isSuccess());
        assertTrue(result.isLoadedFromBackup());
        assertEquals(backup, codec.encode(result.getDocument()));
        assertEquals(malformedCurrent,
                store.getString(ProfileRepository.KEY_CURRENT, null));
        assertEquals(backup, store.getString(ProfileRepository.KEY_BACKUP, null));
        assertNull(store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertFalse(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertEquals(0, store.getCommitCount());
    }

    @Test
    public void malformedLegacyMigrationLeavesOriginalPointsAndFlagUntouched() {
        String original = " [ { definitely-not-json ] ";
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(LEGACY_POINTS_KEY, original);

        ProfileRepository.LoadResult result = new ProfileRepository(
                store, new ProfileJsonCodec()).migrateIfNeeded(
                original,
                120,
                TestFixtures.singleRegion(1000, 1800),
                ProfileKind.INNER);

        assertFalse(result.isSuccess());
        assertEquals(original, store.getString(LEGACY_POINTS_KEY, null));
        assertEquals(0, store.getCommitCount());
        assertFalse(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertNull(store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertNull(store.getString(ProfileRepository.KEY_CURRENT, null));
    }

    @Test
    public void legacyPointMissingRequiredFieldFailsWithoutWritingAnything() {
        assertMalformedLegacyDoesNotWrite("[{}]");
    }

    @Test
    public void legacyPointWithWrongFieldTypeFailsWithoutWritingAnything() {
        assertMalformedLegacyDoesNotWrite(
                "[" + VALID_LEGACY_POINT.replace("\"enabled\":true", "\"enabled\":\"true\"")
                        + "]");
    }

    @Test
    public void legacyPointWithNonFiniteCoordinateFailsWithoutWritingAnything() {
        String[] coordinateFields = {"x", "y", "nx", "ny"};
        String[] finiteValues = {"500.0", "900.0", "0.5", "0.5"};
        for (int index = 0; index < coordinateFields.length; index++) {
            String malformed = VALID_LEGACY_POINT.replace(
                    "\"" + coordinateFields[index] + "\":" + finiteValues[index],
                    "\"" + coordinateFields[index] + "\":1e400");
            assertMalformedLegacyDoesNotWrite("[" + malformed + "]");
        }
    }

    @Test
    public void duplicateLegacyPointIdFailsWithoutWritingAnything() {
        assertMalformedLegacyDoesNotWrite(
                "[" + VALID_LEGACY_POINT + "," + VALID_LEGACY_POINT + "]");
    }

    @Test
    public void failedMigrationCommitLeavesLegacyAndCompletionUntouched() {
        String original = "[]";
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(LEGACY_POINTS_KEY, original);
        store.setCommitSucceeds(false);

        ProfileRepository.LoadResult result = new ProfileRepository(
                store, new ProfileJsonCodec()).migrateIfNeeded(
                original,
                120,
                TestFixtures.singleRegion(1000, 1800),
                ProfileKind.INNER);

        assertFalse(result.isSuccess());
        assertEquals(original, store.getString(LEGACY_POINTS_KEY, null));
        assertFalse(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertNull(store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertNull(store.getString(ProfileRepository.KEY_CURRENT, null));
    }

    @Test
    public void completedMigrationLoadsWithoutOverwritingBackup() {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(ProfileRepository.KEY_CURRENT, VALID_EMPTY_DOCUMENT);
        store.put(ProfileRepository.KEY_LEGACY_BACKUP, "original-backup");
        store.putBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, true);

        ProfileRepository.LoadResult result = new ProfileRepository(
                store, new ProfileJsonCodec()).migrateIfNeeded(
                "[{\"id\":99}]",
                600,
                TestFixtures.singleRegion(1000, 1800),
                ProfileKind.OUTER);

        assertTrue(result.getError(), result.isSuccess());
        assertEquals("original-backup",
                store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertEquals(0, store.getCommitCount());
    }

    @Test
    public void migrationRejectsSixtyFiveLegacyPointsWithoutWritingAnything() {
        StringBuilder legacy = new StringBuilder("[");
        for (int id = 0; id < ScreenProfile.MAX_POINTS + 1; id++) {
            if (id > 0) {
                legacy.append(',');
            }
            legacy.append("{\"id\":").append(id)
                    .append(",\"x\":500,\"y\":900,\"rotation\":0,")
                    .append("\"baseWidth\":1000,\"baseHeight\":1800}");
        }
        legacy.append(']');
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();

        ProfileRepository.LoadResult result = new ProfileRepository(
                store, new ProfileJsonCodec()).migrateIfNeeded(
                legacy.toString(),
                120,
                TestFixtures.singleRegion(1000, 1800),
                ProfileKind.INNER);

        assertFalse(result.isSuccess());
        assertEquals(0, store.getCommitCount());
        assertFalse(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
    }

    @Test
    public void sharedPreferencesAdapterCommitsAllValuesWithOneEditor() {
        RecordingSharedPreferences preferences = new RecordingSharedPreferences(true);
        SharedPreferencesKeyValueStore store =
                new SharedPreferencesKeyValueStore(preferences);
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put("first", "A");
        strings.put("second", "B");
        Map<String, Boolean> booleans = Collections.singletonMap("done", true);

        boolean committed = store.putStringsAndBooleans(strings, booleans);

        assertTrue(committed);
        assertEquals(1, preferences.getEditCalls());
        assertEquals(1, preferences.getCommitCalls());
        assertEquals("A", preferences.getString("first", null));
        assertEquals("B", preferences.getString("second", null));
        assertTrue(preferences.getBoolean("done", false));
    }

    @Test
    public void sharedPreferencesAdapterReturnsCommitFailureWithoutApplyingValues() {
        RecordingSharedPreferences preferences = new RecordingSharedPreferences(false);
        preferences.putInitial("current", "old-current");
        preferences.putInitial("backup", "old-backup");
        SharedPreferencesKeyValueStore store =
                new SharedPreferencesKeyValueStore(preferences);
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put("current", "new-current");
        strings.put("backup", "new-backup");

        boolean committed = store.putStringsAndBooleans(
                strings,
                Collections.singletonMap("done", true));

        assertFalse(committed);
        assertEquals(2, preferences.getEditCalls());
        assertEquals(2, preferences.getCommitCalls());
        assertEquals("old-current", preferences.getString("current", null));
        assertEquals("old-backup", preferences.getString("backup", null));
        assertFalse(preferences.getBoolean("done", false));
    }

    @Test
    public void failedSharedPreferencesMigrationRestoresRepositoryStateInMemory() {
        RecordingSharedPreferences preferences = new RecordingSharedPreferences(false);
        preferences.putInitial(ProfileRepository.KEY_CURRENT, "{broken-current");
        preferences.putInitial(ProfileRepository.KEY_BACKUP, "{broken-backup");
        preferences.putInitial(ProfileRepository.KEY_LEGACY_BACKUP, "old-legacy-backup");
        preferences.putInitial(ProfileRepository.KEY_MIGRATION_COMPLETE, false);
        SharedPreferencesKeyValueStore store =
                new SharedPreferencesKeyValueStore(preferences);

        ProfileRepository.LoadResult result = new ProfileRepository(
                store, new ProfileJsonCodec()).migrateIfNeeded(
                VALID_LEGACY_JSON,
                120,
                TestFixtures.singleRegion(1000, 1800),
                ProfileKind.INNER);

        assertFalse(result.isSuccess());
        assertEquals("{broken-current",
                preferences.getString(ProfileRepository.KEY_CURRENT, null));
        assertEquals("{broken-backup",
                preferences.getString(ProfileRepository.KEY_BACKUP, null));
        assertEquals("old-legacy-backup",
                preferences.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertFalse(preferences.getBoolean(
                ProfileRepository.KEY_MIGRATION_COMPLETE, true));
        assertEquals(2, preferences.getCommitCalls());
    }

    @Test
    public void repositoryInstancesSerializeSavesSoBackupKeepsTheOtherNewDocument()
            throws Exception {
        CoordinatedKeyValueStore store = new CoordinatedKeyValueStore();
        ProfileJsonCodec codec = new ProfileJsonCodec();
        String documentA = codec.encode(TestFixtures.document(
                TestFixtures.profile("profile-a", ProfileKind.INNER, "display-a")));
        ProfileDocument documentB = TestFixtures.document(
                TestFixtures.profile("profile-b", ProfileKind.INNER, "display-b"));
        ProfileDocument documentC = TestFixtures.document(
                TestFixtures.profile("profile-c", ProfileKind.OUTER, "display-c"));
        String encodedB = codec.encode(documentB);
        String encodedC = codec.encode(documentC);
        store.put(ProfileRepository.KEY_CURRENT, documentA);
        ProfileRepository firstRepository = new ProfileRepository(store, codec);
        ProfileRepository secondRepository = new ProfileRepository(store, codec);
        AtomicReference<Boolean> firstSaved = new AtomicReference<>();
        AtomicReference<Boolean> secondSaved = new AtomicReference<>();

        Thread first = new Thread(
                () -> firstSaved.set(firstRepository.save(documentB)), "profile-save-b");
        first.start();
        assertTrue(store.awaitFirstCurrentRead());
        Thread second = new Thread(
                () -> secondSaved.set(secondRepository.save(documentC)), "profile-save-c");
        second.start();
        first.join(5000L);
        second.join(5000L);

        assertFalse(first.isAlive());
        assertFalse(second.isAlive());
        assertEquals(Boolean.TRUE, firstSaved.get());
        assertEquals(Boolean.TRUE, secondSaved.get());
        Set<String> expectedLatest = new HashSet<>();
        expectedLatest.add(encodedB);
        expectedLatest.add(encodedC);
        Set<String> actualLatest = new HashSet<>();
        actualLatest.add(store.getString(ProfileRepository.KEY_CURRENT, null));
        actualLatest.add(store.getString(ProfileRepository.KEY_BACKUP, null));
        assertEquals(expectedLatest, actualLatest);
    }

    private static void assertMalformedLegacyDoesNotWrite(String legacyJson) {
        InMemoryKeyValueStore store = new InMemoryKeyValueStore();
        store.put(LEGACY_POINTS_KEY, legacyJson);

        ProfileRepository.LoadResult result = new ProfileRepository(
                store, new ProfileJsonCodec()).migrateIfNeeded(
                legacyJson,
                120,
                TestFixtures.singleRegion(1000, 1800),
                ProfileKind.INNER);

        assertFalse(result.isSuccess());
        assertEquals(legacyJson, store.getString(LEGACY_POINTS_KEY, null));
        assertEquals(0, store.getCommitCount());
        assertFalse(store.getBoolean(ProfileRepository.KEY_MIGRATION_COMPLETE, false));
        assertNull(store.getString(ProfileRepository.KEY_LEGACY_BACKUP, null));
        assertNull(store.getString(ProfileRepository.KEY_CURRENT, null));
    }

    private static final class InMemoryKeyValueStore implements KeyValueStore {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();
        private boolean commitSucceeds = true;
        private int commitCount;
        private Map<String, String> lastStrings = Collections.emptyMap();
        private ProfileJsonCodec observedCodec;
        private boolean readableCurrentVisibleWhenCompletionWasWritten;

        void put(String key, String value) {
            strings.put(key, value);
        }

        void putBoolean(String key, boolean value) {
            booleans.put(key, value);
        }

        void setCommitSucceeds(boolean commitSucceeds) {
            this.commitSucceeds = commitSucceeds;
        }

        int getCommitCount() {
            return commitCount;
        }

        Map<String, String> getLastStrings() {
            return lastStrings;
        }

        void observeMigrationCompletion(ProfileJsonCodec codec) {
            observedCodec = codec;
        }

        boolean wasReadableCurrentVisibleWhenCompletionWasWritten() {
            return readableCurrentVisibleWhenCompletionWasWritten;
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
            commitCount++;
            lastStrings = new LinkedHashMap<>(newStrings);
            if (!commitSucceeds) {
                return false;
            }
            if (Boolean.TRUE.equals(newBooleans.get(ProfileRepository.KEY_MIGRATION_COMPLETE))
                    && observedCodec != null) {
                String pendingCurrent = newStrings.containsKey(ProfileRepository.KEY_CURRENT)
                        ? newStrings.get(ProfileRepository.KEY_CURRENT)
                        : strings.get(ProfileRepository.KEY_CURRENT);
                readableCurrentVisibleWhenCompletionWasWritten =
                        observedCodec.decode(pendingCurrent).isSuccess();
            }
            strings.putAll(newStrings);
            booleans.putAll(newBooleans);
            return true;
        }
    }

    private static final class CoordinatedKeyValueStore implements KeyValueStore {
        private final Map<String, String> strings = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();
        private final CountDownLatch firstCurrentRead = new CountDownLatch(1);
        private final CountDownLatch secondCurrentRead = new CountDownLatch(1);
        private int currentReadCount;

        synchronized void put(String key, String value) {
            strings.put(key, value);
        }

        boolean awaitFirstCurrentRead() throws InterruptedException {
            return firstCurrentRead.await(2L, TimeUnit.SECONDS);
        }

        @Override
        public String getString(String key, String defaultValue) {
            final String value;
            final int readNumber;
            synchronized (this) {
                value = strings.containsKey(key) ? strings.get(key) : defaultValue;
                if (ProfileRepository.KEY_CURRENT.equals(key)) {
                    currentReadCount++;
                    readNumber = currentReadCount;
                } else {
                    readNumber = 0;
                }
            }
            if (readNumber == 1) {
                firstCurrentRead.countDown();
                try {
                    secondCurrentRead.await(1L, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            } else if (readNumber == 2) {
                secondCurrentRead.countDown();
            }
            return value;
        }

        @Override
        public synchronized boolean getBoolean(String key, boolean defaultValue) {
            return booleans.containsKey(key) ? booleans.get(key) : defaultValue;
        }

        @Override
        public synchronized boolean putStringsAndBooleans(
                Map<String, String> newStrings,
                Map<String, Boolean> newBooleans
        ) {
            strings.putAll(newStrings);
            booleans.putAll(newBooleans);
            return true;
        }
    }

    private static final class RecordingSharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private final boolean commitResult;
        private int editCalls;
        private int commitCalls;

        private RecordingSharedPreferences(boolean commitResult) {
            this.commitResult = commitResult;
        }

        int getEditCalls() {
            return editCalls;
        }

        int getCommitCalls() {
            return commitCalls;
        }

        void putInitial(String key, Object value) {
            values.put(key, value);
        }

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defaultValues) {
            Object value = values.get(key);
            if (value instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> result = (Set<String>) value;
                return result;
            }
            return defaultValues;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            editCalls++;
            return new RecordingEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener
        ) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener
        ) {
        }

        private final class RecordingEditor implements Editor {
            private final Map<String, Object> pending = new LinkedHashMap<>();
            private final List<String> removals = new ArrayList<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> values) {
                pending.put(key, values);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                commitCalls++;
                if (clear) {
                    values.clear();
                }
                for (String removal : removals) {
                    values.remove(removal);
                }
                values.putAll(pending);
                return commitResult;
            }

            @Override
            public void apply() {
                throw new AssertionError("SharedPreferencesKeyValueStore must use commit()");
            }
        }
    }
}
