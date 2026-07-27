package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public class RecordingSessionModelTest {
    private static final DisplaySnapshot SNAPSHOT =
            TestFixtures.snapshotWithDensity(1000, 1800, 2f);

    private static ScreenProfile emptyProfile() {
        return new ScreenProfile("p", ProfileKind.INNER, 48f,
                Collections.<ProfilePoint>emptyList(),
                Collections.singletonList("test-display"),
                Collections.singletonList(new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());
    }

    @Test
    public void tapCoveredByExistingEnabledPointIsSkipped() {
        // Existing point at center: u=v=0.5 -> (500,900), 48dp*2 = 96px square.
        ScreenProfile profile = new ScreenProfile("p", ProfileKind.INNER, 48f,
                Collections.singletonList(ProfilePoint.enabled(1, "full", 0.5f, 0.5f,
                        0L, 0L, 1L)),
                Collections.singletonList("test-display"),
                Collections.singletonList(new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());
        RecordingSessionModel model = new RecordingSessionModel(profile, SNAPSHOT);

        assertEquals(RecordingSessionModel.Outcome.COVERED_EXISTING,
                model.onTap(500f, 900f, 1L, 10L));
        assertEquals(0, model.getNewPointCount());
        assertEquals(1, model.getSkippedCount());
    }

    @Test
    public void ghostStormCreatesExactlyOnePoint() {
        RecordingSessionModel model = new RecordingSessionModel(emptyProfile(), SNAPSHOT);
        for (int i = 0; i < 30; i++) {
            model.onTap(100f + (i % 3), 200f + (i % 2), i, 5L);
        }
        assertEquals(1, model.getNewPointCount());
        assertEquals(29, model.getSkippedCount());
    }

    @Test
    public void distantTapsCreateSeparatePoints() {
        RecordingSessionModel model = new RecordingSessionModel(emptyProfile(), SNAPSHOT);
        assertEquals(RecordingSessionModel.Outcome.NEW_POINT,
                model.onTap(100f, 200f, 1L, 5L));
        assertEquals(RecordingSessionModel.Outcome.NEW_POINT,
                model.onTap(600f, 1200f, 2L, 5L));
        assertEquals(2, model.getNewPointCount());
    }

    @Test
    public void tapBesideCutoutIsRecordedButTapInCutoutIsIgnored() {
        DisplaySnapshot insetSnapshot = new DisplaySnapshot(
                "inset-display", 0, 1000, 1800, 0, 2f, 1L, ProfileKind.OUTER,
                Collections.singletonList(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(450, 0, 550, 80),
                        com.payton.touchblocker.profile.PointDisabledReason.CUTOUT)));
        ScreenProfile profile = new ScreenProfile("p", ProfileKind.OUTER, 30f,
                Collections.<ProfilePoint>emptyList(),
                Collections.singletonList("inset-display"),
                insetSnapshot.getRegions(),
                insetSnapshot.getUnsafeAreas());
        RecordingSessionModel model = new RecordingSessionModel(profile, insetSnapshot);

        assertEquals(RecordingSessionModel.Outcome.NEW_POINT,
                model.onTap(100f, 48f, 1L, 5L));
        assertEquals(RecordingSessionModel.Outcome.OUTSIDE_SAFE_REGION,
                model.onTap(500f, 48f, 2L, 5L));
        assertEquals(1, model.getNewPointCount());
        assertEquals(1, model.getSkippedCount());
    }

    @Test
    public void limitReachedAtMaxPointsAfterCoverageAndMergeChecks() {
        ArrayList<ProfilePoint> full = new ArrayList<>();
        for (int id = 1; id <= ScreenProfile.MAX_POINTS; id++) {
            full.add(ProfilePoint.enabled(id, "full",
                    (id % 8) / 8f + 0.01f, ((id / 8) % 8) / 8f + 0.01f,
                    0L, 0L, 1L));
        }
        ScreenProfile profile = new ScreenProfile("p", ProfileKind.INNER, 24f, full,
                Collections.singletonList("test-display"),
                Collections.singletonList(new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());
        RecordingSessionModel model = new RecordingSessionModel(profile, SNAPSHOT);

        // Point 64 covers (10,18), so use an uncovered location to exercise the limit branch.
        assertEquals(RecordingSessionModel.Outcome.LIMIT_REACHED,
                model.onTap(450f, 100f, 1L, 5L));
    }

    @Test
    public void buildUpdatedProfileClampsAndContinuesIds() {
        ScreenProfile profile = TestFixtures.profileWithPoint(
                ProfilePoint.enabled(7, "full", 0.9f, 0.9f, 0L, 0L, 1L));
        RecordingSessionModel model = new RecordingSessionModel(profile, SNAPSHOT);
        model.onTap(0f, 0f, 1L, 5L);

        ScreenProfile updated = model.buildUpdatedProfile();
        assertEquals(2, updated.getPoints().size());
        ProfilePoint added = updated.getPoints().get(1);
        assertEquals(8, added.getId());
        assertEquals(0f, added.getU(), 0.001f);
        assertEquals(0f, added.getV(), 0.001f);
        assertEquals(0f, added.getDiameterDpOverride(), 0.001f);
        assertEquals(SNAPSHOT.getGeneration(), added.getDisplayGeneration());
    }

    @Test
    public void undoRemovesNewestSessionPoint() {
        RecordingSessionModel model = new RecordingSessionModel(emptyProfile(), SNAPSHOT);
        model.onTap(100f, 200f, 1L, 5L);
        assertTrue(model.undoLast());
        assertEquals(0, model.getNewPointCount());
        assertFalse(model.undoLast());
    }
}
