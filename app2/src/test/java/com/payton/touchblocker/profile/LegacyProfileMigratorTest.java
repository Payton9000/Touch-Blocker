package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class LegacyProfileMigratorTest {
    @Test
    public void migratesLegacyPointsOnlyIntoCurrentProfile() {
        DisplaySnapshot snapshot = TestFixtures.snapshot("display-outer", ProfileKind.OUTER);
        LegacyPointRecord legacy = record(
                1, 500f, 900f, true, -1,
                0.5f, 0.5f, 0, 1000, 1800);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 120, snapshot, ProfileKind.OUTER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        assertEquals(1, result.getDocument().getProfiles().size());
        ScreenProfile profile = result.getDocument().getProfiles().values().iterator().next();
        assertEquals(ProfileKind.OUTER, profile.getKind());
        assertEquals(Collections.singletonList("display-outer"), profile.getFingerprints());
        assertEquals(snapshot.getRegions(), profile.getReferenceRegions());
        assertEquals(snapshot.getUnsafeAreas(), profile.getReferenceUnsafeAreas());
        assertEquals(1, profile.getPoints().size());
        assertTrue(profile.getPoints().get(0).isEnabled());
    }

    @Test
    public void convertsLegacyPixelsToClampedDp() {
        DisplaySnapshot snapshot = TestFixtures.snapshotWithDensity(1000, 1800, 3f);
        List<LegacyPointRecord> points = Arrays.asList(
                record(1, 250f, 900f, true, -1, 0.25f, 0.5f, 0, 1000, 1800),
                record(2, 500f, 900f, true, 30, 0.5f, 0.5f, 0, 1000, 1800),
                record(3, 750f, 900f, true, 600, 0.75f, 0.5f, 0, 1000, 1800));

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                points, 600, snapshot, ProfileKind.INNER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ScreenProfile profile = result.getDocument().getProfiles().values().iterator().next();
        assertEquals(200f, profile.getGlobalDiameterDp(), 0f);
        assertEquals(0f, profile.getPoints().get(0).getDiameterDpOverride(), 0f);
        assertEquals(24f, profile.getPoints().get(1).getDiameterDpOverride(), 0f);
        assertEquals(200f, profile.getPoints().get(2).getDiameterDpOverride(), 0f);
    }

    @Test
    public void pointIntersectingRealSeparatingHingeIsPreservedAndEnabled() {
        DisplayRegion left = new DisplayRegion("left", new IntRect(0, 0, 1000, 1800));
        DisplayRegion right = new DisplayRegion("right", new IntRect(1020, 0, 2020, 1800));
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "fold-display", 0, 2020, 1800, 0, 1f, 7L, ProfileKind.INNER,
                Arrays.asList(left, right),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(1000, 0, 1020, 1800), PointDisabledReason.HINGE)));
        LegacyPointRecord legacy = record(
                1, 990f, 900f, true, -1,
                990f / 2020f, 0.5f, 0, 2020, 1800);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 120, snapshot, ProfileKind.INNER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ProfilePoint point = onlyPoint(result);
        assertEquals("left", point.getRegionId());
        assertEquals(0.99f, point.getU(), 0.0001f);
        assertTrue(point.isEnabled());
        assertEquals(PointDisabledReason.NONE, point.getDisabledReason());
    }

    @Test
    public void pointCenteredInsideHingeUsesSyntheticFullRegionWithoutPaneSnapping() {
        DisplayRegion left = new DisplayRegion("left", new IntRect(0, 0, 1000, 1800));
        DisplayRegion right = new DisplayRegion("right", new IntRect(1020, 0, 2020, 1800));
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "fold-display", 0, 2020, 1800, 0, 1f, 7L, ProfileKind.INNER,
                Arrays.asList(left, right),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(1000, 0, 1020, 1800), PointDisabledReason.HINGE)));
        LegacyPointRecord legacy = record(
                1, 1010f, 900f, true, -1,
                0.5f, 0.5f, 0, 2020, 1800);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 40, snapshot, ProfileKind.INNER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ScreenProfile profile = result.getDocument().getProfiles().values().iterator().next();
        ProfilePoint point = profile.getPoints().get(0);
        assertEquals("legacy-full", point.getRegionId());
        assertEquals(0.5f, point.getU(), 0.0001f);
        assertEquals(0.5f, point.getV(), 0.0001f);
        assertEquals(new DisplayRegion("legacy-full", snapshot.getBounds()),
                profile.getReferenceRegions().get(profile.getReferenceRegions().size() - 1));
        assertFalse(point.isEnabled());
        assertEquals(PointDisabledReason.NEEDS_REVIEW, point.getDisabledReason());
    }

    @Test
    public void pointCenteredInsideCutoutUsesSyntheticFullRegionEvenWhenFullRegionContainsIt() {
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "cutout-display", 0, 1000, 1800, 0, 1f, 8L, ProfileKind.OUTER,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(450, 0, 550, 100), PointDisabledReason.CUTOUT)));
        LegacyPointRecord legacy = record(
                1, 500f, 50f, true, -1,
                0.5f, 50f / 1800f, 0, 1000, 1800);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 40, snapshot, ProfileKind.OUTER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ProfilePoint point = onlyPoint(result);
        assertEquals("legacy-full", point.getRegionId());
        assertEquals(0.5f, point.getU(), 0.0001f);
        assertEquals(50f / 1800f, point.getV(), 0.0001f);
        assertFalse(point.isEnabled());
        assertEquals(PointDisabledReason.NEEDS_REVIEW, point.getDisabledReason());
    }

    @Test
    public void trulyOutOfBoundsPointIsConstrainedOnlyForSchemaAndWarned() {
        LegacyPointRecord legacy = record(
                1, -10f, 900f, true, -1,
                -0.01f, 0.5f, 0, 1000, 1800);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 40,
                TestFixtures.singleRegion(1000, 1800), ProfileKind.OUTER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ProfilePoint point = onlyPoint(result);
        assertFalse(point.isEnabled());
        assertEquals(PointDisabledReason.OUT_OF_BOUNDS, point.getDisabledReason());
        assertEquals(0f, point.getU(), 0f);
        assertTrue(result.getWarnings().get(0).contains("schema boundary"));
    }

    @Test
    public void missingBaseGeometryPreservesPointDisabledForReview() {
        LegacyPointRecord legacy = record(
                1, 500f, 900f, true, -1,
                0.5f, 0.5f, -1, -1, -1);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 120,
                TestFixtures.singleRegion(1000, 1800), ProfileKind.OUTER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ProfilePoint point = onlyPoint(result);
        assertEquals(0.5f, point.getU(), 0.0001f);
        assertEquals(0.5f, point.getV(), 0.0001f);
        assertFalse(point.isEnabled());
        assertEquals(PointDisabledReason.NEEDS_REVIEW, point.getDisabledReason());
    }

    @Test
    public void baseGeometryRotatesDeterministicallyIntoCurrentRegion() {
        DisplaySnapshot landscape = new DisplaySnapshot(
                "landscape", 0, 1800, 1000, 1, 1f, 4L, ProfileKind.OUTER,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1800, 1000))),
                Collections.<UnsafeArea>emptyList());
        LegacyPointRecord legacy = record(
                1, 250f, 900f, true, -1,
                0.25f, 0.5f, 0, 1000, 1800);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                Collections.singletonList(legacy), 40, landscape, ProfileKind.OUTER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ProfilePoint point = onlyPoint(result);
        assertEquals(0.5f, point.getU(), 0.0001f);
        assertEquals(0.75f, point.getV(), 0.0001f);
        assertTrue(point.isEnabled());
    }

    @Test
    public void sameInputProducesExactlySameEncodedDocument() throws Exception {
        DisplaySnapshot snapshot = TestFixtures.snapshot("stable-display", ProfileKind.INNER);
        List<LegacyPointRecord> points = Collections.singletonList(record(
                8, 500f, 900f, true, 96,
                0.5f, 0.5f, 0, 1000, 1800));
        LegacyProfileMigrator migrator = new LegacyProfileMigrator();
        ProfileJsonCodec codec = new ProfileJsonCodec();

        LegacyProfileMigrator.MigrationResult first = migrator.migrate(
                points, 120, snapshot, ProfileKind.INNER);
        LegacyProfileMigrator.MigrationResult second = migrator.migrate(
                points, 120, snapshot, ProfileKind.INNER);

        assertTrue(first.getWarnings().toString(), first.isSuccess());
        assertTrue(second.getWarnings().toString(), second.isSuccess());
        assertEquals(codec.encode(first.getDocument()), codec.encode(second.getDocument()));
    }

    @Test
    public void acceptsExactlyMaximumPointCount() {
        assertEquals(64, ScreenProfile.MAX_POINTS);
        List<LegacyPointRecord> points = records(ScreenProfile.MAX_POINTS);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                points, 40, TestFixtures.singleRegion(1000, 1800), ProfileKind.INNER);

        assertTrue(result.getWarnings().toString(), result.isSuccess());
        assertEquals(ScreenProfile.MAX_POINTS,
                result.getDocument().getProfiles().values().iterator().next().getPoints().size());
    }

    @Test
    public void rejectsMoreThanMaximumPointCountWithoutTruncating() {
        List<LegacyPointRecord> points = records(ScreenProfile.MAX_POINTS + 1);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator().migrate(
                points, 40, TestFixtures.singleRegion(1000, 1800), ProfileKind.INNER);

        assertFalse(result.isSuccess());
        assertNull(result.getDocument());
        assertFalse(result.getWarnings().isEmpty());
    }

    private static ProfilePoint onlyPoint(LegacyProfileMigrator.MigrationResult result) {
        return result.getDocument().getProfiles().values().iterator().next().getPoints().get(0);
    }

    private static List<LegacyPointRecord> records(int count) {
        ArrayList<LegacyPointRecord> points = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            points.add(record(
                    id, 500f, 900f, true, -1,
                    0.5f, 0.5f, 0, 1000, 1800));
        }
        return points;
    }

    private static LegacyPointRecord record(
            int id,
            float x,
            float y,
            boolean enabled,
            int sizeOverridePx,
            float normalizedX,
            float normalizedY,
            int baseRotation,
            int baseWidthPx,
            int baseHeightPx
    ) {
        return new LegacyPointRecord(
                id, x, y, 10L + id, 20L + id, enabled, sizeOverridePx,
                normalizedX, normalizedY, baseRotation, baseWidthPx, baseHeightPx);
    }
}
