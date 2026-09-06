package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProfileRepository {
    public static final String KEY_CURRENT = "profiles_v2_json";
    public static final String KEY_BACKUP = "profiles_v2_backup_json";
    public static final String KEY_LEGACY_BACKUP = "legacy_points_backup_json";
    public static final String KEY_MIGRATION_COMPLETE = "profiles_v2_migration_complete";
    private static final Object PROCESS_LOCK = new Object();

    private static ProfileDocument cachedDocument;
    private static boolean cachedFromBackup;
    private static long revision = 1L;

    private final KeyValueStore store;
    private final ProfileJsonCodec codec;
    private final LegacyProfileMigrator migrator;

    public ProfileRepository(KeyValueStore store, ProfileJsonCodec codec) {
        if (store == null) {
            throw new NullPointerException("store == null");
        }
        if (codec == null) {
            throw new NullPointerException("codec == null");
        }
        this.store = store;
        this.codec = codec;
        this.migrator = new LegacyProfileMigrator();
    }

    public LoadResult load() {
        synchronized (PROCESS_LOCK) {
            return loadLocked();
        }
    }

    public long getRevision() {
        synchronized (PROCESS_LOCK) {
            return revision;
        }
    }

    /** Test-only: resets the process-level cache so tests do not leak state into each other. */
    public static void clearProcessCacheForTest() {
        synchronized (PROCESS_LOCK) {
            cachedDocument = null;
            cachedFromBackup = false;
            revision = 1L;
        }
    }

    public boolean save(ProfileDocument document) {
        synchronized (PROCESS_LOCK) {
            if (!hasSupportedPointCounts(document)) {
                return false;
            }
            final String encoded;
            try {
                encoded = codec.encode(document);
            } catch (Exception failure) {
                return false;
            }
            if (!codec.decode(encoded).isSuccess()) {
                return false;
            }

            LinkedHashMap<String, String> strings = new LinkedHashMap<>();
            String current = store.getString(KEY_CURRENT, null);
            if (current != null && codec.decode(current).isSuccess()) {
                strings.put(KEY_BACKUP, current);
            }
            strings.put(KEY_CURRENT, encoded);
            boolean committed = store.putStringsAndBooleans(
                    strings, Collections.<String, Boolean>emptyMap());
            if (committed) {
                cachedDocument = document;
                cachedFromBackup = false;
                revision++;
            }
            return committed;
        }
    }

    public LoadResult migrateIfNeeded(
            String legacyJson,
            int legacyGlobalPx,
            DisplaySnapshot snapshot,
            ProfileKind kind
    ) {
        synchronized (PROCESS_LOCK) {
            if (store.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
                return loadLocked();
            }
            LoadResult existing = loadLocked();
            if (existing.isSuccess()) {
                return existing;
            }

            final List<LegacyPointRecord> legacyPoints;
            try {
                legacyPoints = parseLegacyPoints(legacyJson);
            } catch (Exception failure) {
                return LoadResult.failure(message(failure));
            }
            LegacyProfileMigrator.MigrationResult migration = migrator.migrate(
                    legacyPoints, legacyGlobalPx, snapshot, kind);
            if (!migration.isSuccess()) {
                return LoadResult.failure(migration.getWarnings().toString());
            }

            final String encoded;
            try {
                encoded = codec.encode(migration.getDocument());
            } catch (Exception failure) {
                return LoadResult.failure(message(failure));
            }
            ProfileJsonCodec.DecodeResult verified = codec.decode(encoded);
            if (!verified.isSuccess()) {
                return LoadResult.failure(verified.getFatalError());
            }

            LinkedHashMap<String, String> strings = new LinkedHashMap<>();
            strings.put(KEY_LEGACY_BACKUP, legacyJson);
            strings.put(KEY_CURRENT, encoded);
            Map<String, Boolean> booleans = Collections.singletonMap(
                    KEY_MIGRATION_COMPLETE, true);
            if (!store.putStringsAndBooleans(strings, booleans)) {
                return LoadResult.failure("migration commit failed");
            }
            cachedDocument = verified.getDocument();
            cachedFromBackup = false;
            revision++;
            return LoadResult.success(verified.getDocument(), false);
        }
    }

    /**
     * Rebuilds the profile document from the pre-migration records kept under
     * {@link #KEY_LEGACY_BACKUP}, replacing whatever the first v2 migration produced.
     *
     * <p>Used only by {@link LegacyRemigration} to repair points that the original migration stored
     * without a natural anchor, which made them move to a different physical spot on every screen
     * rotation. The current backup is preserved as {@link #KEY_BACKUP} first, so a bad repair is
     * still recoverable.
     *
     * @return {@code true} when a repaired document was committed.
     */
    public boolean remigrateFromLegacyBackup(
            String legacyJson,
            int legacyGlobalPx,
            DisplaySnapshot snapshot
    ) {
        synchronized (PROCESS_LOCK) {
            if (legacyJson == null || snapshot == null) {
                return false;
            }
            final List<LegacyPointRecord> legacyPoints;
            try {
                legacyPoints = parseLegacyPoints(legacyJson);
            } catch (Exception failure) {
                return false;
            }
            if (legacyPoints.isEmpty()) {
                return false;
            }
            ProfileKind kind = snapshot.getSuggestedKind() == null
                    ? ProfileKind.OUTER : snapshot.getSuggestedKind();
            LegacyProfileMigrator.MigrationResult migration = migrator.migrate(
                    legacyPoints, legacyGlobalPx, snapshot, kind);
            if (!migration.isSuccess()) {
                return false;
            }
            return save(migration.getDocument());
        }
    }

    private LoadResult loadLocked() {
        if (cachedDocument != null) {
            return LoadResult.success(cachedDocument, cachedFromBackup);
        }

        String current = store.getString(KEY_CURRENT, null);
        ProfileJsonCodec.DecodeResult currentResult = codec.decode(current);
        if (currentResult.isSuccess()) {
            cachedDocument = currentResult.getDocument();
            cachedFromBackup = false;
            return LoadResult.success(currentResult.getDocument(), false);
        }

        String backup = store.getString(KEY_BACKUP, null);
        ProfileJsonCodec.DecodeResult backupResult = codec.decode(backup);
        if (backupResult.isSuccess()) {
            cachedDocument = backupResult.getDocument();
            cachedFromBackup = true;
            return LoadResult.success(backupResult.getDocument(), true);
        }
        return LoadResult.failure(
                "current: " + currentResult.getFatalError()
                        + "; backup: " + backupResult.getFatalError());
    }

    private static boolean hasSupportedPointCounts(ProfileDocument document) {
        if (document == null) {
            return false;
        }
        for (ScreenProfile profile : document.getProfiles().values()) {
            if (profile.getPoints().size() > ScreenProfile.MAX_POINTS) {
                return false;
            }
        }
        return true;
    }

    private static List<LegacyPointRecord> parseLegacyPoints(String raw) throws Exception {
        if (raw == null) {
            throw new IllegalArgumentException("legacyJson == null");
        }
        JSONTokener tokener = new JSONTokener(raw);
        Object root = tokener.nextValue();
        if (!(root instanceof JSONArray)) {
            throw new IllegalArgumentException("legacy root must be an array");
        }
        if (tokener.nextClean() != 0) {
            throw new IllegalArgumentException("unexpected trailing legacy content");
        }
        JSONArray array = (JSONArray) root;
        if (array.length() > ScreenProfile.MAX_POINTS) {
            throw new IllegalArgumentException(
                    "legacy points exceed maximum of " + ScreenProfile.MAX_POINTS);
        }
        ArrayList<LegacyPointRecord> points = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            Object value = array.get(index);
            if (!(value instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "legacyPoints[" + index + "] must be an object");
            }
            JSONObject point = (JSONObject) value;
            String path = "legacyPoints[" + index + "]";
            points.add(new LegacyPointRecord(
                    requireLegacyInt(point, "id", path + ".id"),
                    requireLegacyFiniteFloat(point, "x", path + ".x"),
                    requireLegacyFiniteFloat(point, "y", path + ".y"),
                    requireLegacyLong(point, "timestamp", path + ".timestamp"),
                    requireLegacyLong(point, "duration", path + ".duration"),
                    requireLegacyBoolean(point, "enabled", path + ".enabled"),
                    requireLegacyInt(point, "sizeOverride", path + ".sizeOverride"),
                    requireLegacyFiniteFloat(point, "nx", path + ".nx"),
                    requireLegacyFiniteFloat(point, "ny", path + ".ny"),
                    requireLegacyInt(point, "rotation", path + ".rotation"),
                    requireLegacyInt(point, "baseWidth", path + ".baseWidth"),
                    requireLegacyInt(point, "baseHeight", path + ".baseHeight")
            ));
        }
        return points;
    }

    private static Object requireLegacyValue(
            JSONObject object,
            String name,
            String path
    ) {
        if (!object.has(name)) {
            throw new IllegalArgumentException(path + " is required");
        }
        Object value = object.opt(name);
        if (value == null || value == JSONObject.NULL) {
            throw new IllegalArgumentException(path + " must not be null");
        }
        return value;
    }

    private static boolean requireLegacyBoolean(
            JSONObject object,
            String name,
            String path
    ) {
        Object value = requireLegacyValue(object, name, path);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static int requireLegacyInt(
            JSONObject object,
            String name,
            String path
    ) {
        long value = requireLegacyIntegralNumber(object, name, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(path + " is outside the integer range");
        }
        return (int) value;
    }

    private static long requireLegacyLong(
            JSONObject object,
            String name,
            String path
    ) {
        return requireLegacyIntegralNumber(object, name, path);
    }

    private static long requireLegacyIntegralNumber(
            JSONObject object,
            String name,
            String path
    ) {
        Object value = requireLegacyValue(object, name, path);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(path + " must be a JSON number");
        }
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(path + " must be an exact integer");
        }
    }

    private static float requireLegacyFiniteFloat(
            JSONObject object,
            String name,
            String path
    ) {
        Object value = requireLegacyValue(object, name, path);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(path + " must be a JSON number");
        }
        double doubleValue = ((Number) value).doubleValue();
        if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
            throw new IllegalArgumentException(path + " must be finite");
        }
        float floatValue = (float) doubleValue;
        if (Float.isNaN(floatValue) || Float.isInfinite(floatValue)) {
            throw new IllegalArgumentException(path + " must fit in a finite float");
        }
        if (doubleValue != 0d && floatValue == 0f) {
            throw new IllegalArgumentException(path + " is too small to preserve as a float");
        }
        return floatValue;
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return message == null ? failure.getClass().getSimpleName() : message;
    }

    public static final class LoadResult {
        private final ProfileDocument document;
        private final boolean loadedFromBackup;
        private final String error;

        private LoadResult(
                ProfileDocument document,
                boolean loadedFromBackup,
                String error
        ) {
            this.document = document;
            this.loadedFromBackup = loadedFromBackup;
            this.error = error;
        }

        private static LoadResult success(
                ProfileDocument document,
                boolean loadedFromBackup
        ) {
            return new LoadResult(document, loadedFromBackup, null);
        }

        private static LoadResult failure(String error) {
            return new LoadResult(null, false, error);
        }

        public boolean isSuccess() {
            return document != null && error == null;
        }

        public ProfileDocument getDocument() {
            return document;
        }

        public boolean isLoadedFromBackup() {
            return loadedFromBackup;
        }

        public String getError() {
            return error;
        }
    }
}
