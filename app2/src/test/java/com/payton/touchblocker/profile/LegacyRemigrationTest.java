package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.geometry.CoordinateTransformer;
import com.payton.touchblocker.geometry.ResolvedPoint;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Covers the one-time repair of profiles written by the first v2 migration, whose points carried no
 * rotation anchor and so moved to a different physical spot every time the screen turned.
 */
public class LegacyRemigrationTest {
    /** One point recorded at physical (240, 200) on a 1200x2000 portrait panel. */
    private static final String LEGACY_JSON =
            "[{\"id\":1,\"x\":240.0,\"y\":200.0,\"timestamp\":1,\"duration\":1,"
                    + "\"enabled\":true,\"sizeOverride\":0,\"nx\":0.2,\"ny\":0.1,"
                    + "\"rotation\":0,\"baseWidth\":1200,\"baseHeight\":2000}]";

    @Before
    public void resetCache() {
        ProfileRepository.clearProcessCacheForTest();
    }

    @Test
    public void detectsAnchorlessPointsAsNeedingRepair() {
        ProfilePoint anchorless = ProfilePoint.enabled(1, "full", 0.2f, 0.1f, 1L, 1L, 1L);
        assertTrue(LegacyRemigration.needsRepair(documentWith(anchorless)));

        ProfilePoint anchored = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.2f, 0.1f, 0.2f, 0.1f, 1L, 1L, 1L);
        assertFalse(LegacyRemigration.needsRepair(documentWith(anchored)));
        assertFalse(LegacyRemigration.needsRepair(null));
    }

    /**
     * The reported upgrade symptom. A profile whose points were migrated while the device was held
     * at 180 degrees has them mirrored; re-migrating from the retained legacy records must put them
     * back on the spot the user originally recorded.
     */
    @Test
    public void repairRestoresTheOriginallyRecordedPhysicalSpot() {
        MapStore store = new MapStore();
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());

        // What the old migration produced when run at 180 degrees: mirrored fractions, no anchor.
        ProfilePoint mirrored = ProfilePoint.enabled(1, "full", 0.8f, 0.9f, 1L, 1L, 1L);
        assertTrue(repository.save(documentWith(mirrored)));
        ProfileRepository.clearProcessCacheForTest();

        assertTrue(LegacyRemigration.repairIfNeeded(
                repository, store, LEGACY_JSON, 60, portrait()));

        ProfileRepository.LoadResult repaired = repository.load();
        assertTrue(repaired.isSuccess());
        ProfilePoint point = firstPoint(repaired.getDocument());
        assertTrue("repaired point must be rotation-anchored", point.hasNaturalAnchor());

        ResolvedPoint resolved = CoordinateTransformer.resolve(portrait(), point, 30f);
        assertNotNull(resolved);
        assertEquals(240f, resolved.getCenterX(), 1f);
        assertEquals(200f, resolved.getCenterY(), 1f);
    }

    /** The repair runs at most once, so a later user edit can never be reverted by it. */
    @Test
    public void repairRunsOnlyOnce() {
        MapStore store = new MapStore();
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());
        assertTrue(repository.save(documentWith(
                ProfilePoint.enabled(1, "full", 0.8f, 0.9f, 1L, 1L, 1L))));
        ProfileRepository.clearProcessCacheForTest();

        assertTrue(LegacyRemigration.repairIfNeeded(
                repository, store, LEGACY_JSON, 60, portrait()));
        assertTrue(store.getBoolean(LegacyRemigration.KEY_REMIGRATION_COMPLETE, false));

        assertFalse("second call must be a no-op", LegacyRemigration.repairIfNeeded(
                repository, store, LEGACY_JSON, 60, portrait()));
    }

    @Test
    public void alreadyAnchoredProfilesAreLeftAlone() {
        MapStore store = new MapStore();
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());
        ProfilePoint anchored = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.2f, 0.1f, 0.2f, 0.1f, 1L, 1L, 1L);
        assertTrue(repository.save(documentWith(anchored)));
        ProfileRepository.clearProcessCacheForTest();

        assertFalse(LegacyRemigration.repairIfNeeded(
                repository, store, LEGACY_JSON, 60, portrait()));
        assertEquals(0.2f, firstPoint(repository.load().getDocument()).getNaturalU(), 0.001f);
    }

    /** With no retained legacy records there is nothing to rebuild from; points must survive. */
    @Test
    public void withoutALegacyBackupThePointsAreKept() {
        MapStore store = new MapStore();
        ProfileRepository repository = new ProfileRepository(store, new ProfileJsonCodec());
        assertTrue(repository.save(documentWith(
                ProfilePoint.enabled(1, "full", 0.8f, 0.9f, 1L, 1L, 1L))));
        ProfileRepository.clearProcessCacheForTest();

        assertFalse(LegacyRemigration.repairIfNeeded(repository, store, "[]", 60, portrait()));
        assertEquals(1, firstProfile(repository.load().getDocument()).getPoints().size());
    }

    private static DisplaySnapshot portrait() {
        return new DisplaySnapshot(
                "tablet", 0, 1200, 2000, 0, 2f, 1L, null,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1200, 2000))),
                Collections.<UnsafeArea>emptyList());
    }

    private static ProfileDocument documentWith(ProfilePoint point) {
        ScreenProfile profile = new ScreenProfile(
                "legacy-outer", ProfileKind.OUTER, 30f,
                Collections.singletonList(point),
                Collections.singletonList("tablet"),
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1200, 2000))),
                Collections.<UnsafeArea>emptyList());
        return new ProfileDocument(2,
                Collections.singletonMap(profile.getId(), profile),
                Collections.<String, String>emptyMap());
    }

    private static ScreenProfile firstProfile(ProfileDocument document) {
        return new java.util.ArrayList<>(document.getProfiles().values()).get(0);
    }

    private static ProfilePoint firstPoint(ProfileDocument document) {
        return firstProfile(document).getPoints().get(0);
    }

    private static final class MapStore implements KeyValueStore {
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
                Map<String, String> newStrings, Map<String, Boolean> newBooleans) {
            strings.putAll(newStrings);
            booleans.putAll(newBooleans);
            return true;
        }
    }
}
