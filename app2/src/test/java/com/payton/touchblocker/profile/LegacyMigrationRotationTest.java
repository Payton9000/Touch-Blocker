package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.geometry.CoordinateTransformer;
import com.payton.touchblocker.geometry.ResolvedPoint;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * A blocking point marks a physically fixed spot on the glass (a faulty digitiser area), so it must
 * stay on that spot through every rotation. These tests pin the two ways that guarantee was lost.
 */
public class LegacyMigrationRotationTest {
    /** Portrait 1200x2000, matching the tablet where the regression was reported. */
    private static DisplaySnapshot portrait() {
        return snapshotAt(0, 1200, 2000);
    }

    private static DisplaySnapshot snapshotAt(int rotation, int width, int height) {
        return new DisplaySnapshot(
                "device", 0, width, height, rotation, 2f, rotation + 1L, null,
                Collections.singletonList(new com.payton.touchblocker.display.DisplayRegion(
                        "full", new com.payton.touchblocker.display.IntRect(0, 0, width, height))),
                Collections.<com.payton.touchblocker.display.UnsafeArea>emptyList());
    }

    /**
     * Migrated points must carry a natural anchor. Without one {@code CoordinateTransformer}
     * takes the {@code u}/{@code v} path, which stretches the stored fraction across the rotated
     * bounds instead of rotating it -- so a point recorded near the top-left reappears near the
     * top-left of the *rotated* frame, i.e. somewhere else on the glass entirely.
     */
    @Test
    public void migratedPointKeepsItsPhysicalSpotAfterAQuarterTurn() {
        // Recorded at physical (240, 200) in portrait: a fifth across, a tenth down.
        LegacyPointRecord legacy = new LegacyPointRecord(
                1, 240f, 200f, 1L, 1L, true, 0, 0.2f, 0.1f, 0, 1200, 2000);

        LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator()
                .migrate(Collections.singletonList(legacy), 60, portrait(), ProfileKind.OUTER);
        assertTrue(result.getWarnings().toString(), result.isSuccess());
        ProfilePoint migrated = onlyPoint(result);

        assertTrue(
                "a migrated point must be rotation-anchored, otherwise it moves on the glass "
                        + "every time the screen turns",
                migrated.hasNaturalAnchor());

        ResolvedPoint inPortrait = CoordinateTransformer.resolve(portrait(), migrated, 30f);
        assertNotNull(inPortrait);
        assertEquals(240f, inPortrait.getCenterX(), 1f);
        assertEquals(200f, inPortrait.getCenterY(), 1f);

        // Rotating the device 90 degrees moves the same physical spot to (y, naturalWidth - x).
        ResolvedPoint inLandscape = CoordinateTransformer.resolve(
                snapshotAt(1, 2000, 1200), migrated, 30f);
        assertNotNull(inLandscape);
        assertEquals(200f, inLandscape.getCenterX(), 1f);
        assertEquals(1200f - 240f, inLandscape.getCenterY(), 1f);
    }

    /**
     * The reported upgrade symptom: installing the new version while the device happened to be
     * rotated 180 degrees left every point mirrored. Migration reads the recorded {@code
     * baseRotation} to recover the physical spot, so the result must not depend on the rotation
     * the device is in at upgrade time.
     */
    @Test
    public void migrationResultIsIndependentOfTheRotationAtUpgradeTime() {
        LegacyPointRecord legacy = new LegacyPointRecord(
                1, 240f, 200f, 1L, 1L, true, 0, 0.2f, 0.1f, 0, 1200, 2000);

        float[] expected = null;
        int[][] rotations = {{0, 1200, 2000}, {1, 2000, 1200}, {2, 1200, 2000}, {3, 2000, 1200}};
        for (int[] rotation : rotations) {
            DisplaySnapshot upgradeTime = snapshotAt(rotation[0], rotation[1], rotation[2]);
            LegacyProfileMigrator.MigrationResult result = new LegacyProfileMigrator()
                    .migrate(Collections.singletonList(legacy), 60, upgradeTime,
                            ProfileKind.OUTER);
            assertTrue(result.getWarnings().toString(), result.isSuccess());
            ProfilePoint migrated = onlyPoint(result);

            // Resolve every migrated variant back in portrait: the physical spot must agree.
            ResolvedPoint resolved = CoordinateTransformer.resolve(portrait(), migrated, 30f);
            assertNotNull("rotation " + rotation[0], resolved);
            if (expected == null) {
                expected = new float[]{resolved.getCenterX(), resolved.getCenterY()};
                assertEquals("baseline must be the recorded spot", 240f, expected[0], 1f);
                assertEquals("baseline must be the recorded spot", 200f, expected[1], 1f);
            } else {
                assertEquals(
                        "upgrading while rotated " + (rotation[0] * 90)
                                + " degrees moved the point",
                        expected[0], resolved.getCenterX(), 1f);
                assertEquals(
                        "upgrading while rotated " + (rotation[0] * 90)
                                + " degrees moved the point",
                        expected[1], resolved.getCenterY(), 1f);
            }
        }
    }

    /** A freshly recorded point already behaves correctly; this guards against regressing it. */
    @Test
    public void recordedPointsStayFixedAcrossAllFourRotations() {
        DisplaySnapshot recordedIn = portrait();
        float[] anchor = CoordinateTransformer.naturalAnchor(recordedIn, 240f, 200f);
        assertNotNull(anchor);
        ProfilePoint point = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.2f, 0.1f, anchor[0], anchor[1], 1L, 1L, 1L);

        int[][] rotations = {{0, 1200, 2000}, {1, 2000, 1200}, {2, 1200, 2000}, {3, 2000, 1200}};
        float[][] expected = {
                {240f, 200f}, {200f, 960f}, {960f, 1800f}, {1800f, 240f}
        };
        for (int index = 0; index < rotations.length; index++) {
            DisplaySnapshot snapshot = snapshotAt(
                    rotations[index][0], rotations[index][1], rotations[index][2]);
            ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, 30f);
            assertNotNull("rotation " + rotations[index][0], resolved);
            assertEquals("rotation " + rotations[index][0],
                    expected[index][0], resolved.getCenterX(), 1f);
            assertEquals("rotation " + rotations[index][0],
                    expected[index][1], resolved.getCenterY(), 1f);
        }
    }

    private static ProfilePoint onlyPoint(LegacyProfileMigrator.MigrationResult result) {
        List<ScreenProfile> profiles =
                new java.util.ArrayList<>(result.getDocument().getProfiles().values());
        assertEquals(1, profiles.size());
        List<ProfilePoint> points = profiles.get(0).getPoints();
        assertEquals(1, points.size());
        return points.get(0);
    }
}
