package com.payton.touchblocker.geometry;

import static org.junit.Assert.assertEquals;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class OverlayRefreshPlannerTest {
    @Test
    public void resolvesEnabledPointsOnlyAndClusters() {
        ScreenProfile profile = new ScreenProfile(
                "p", ProfileKind.INNER, 40f,
                Arrays.asList(
                        ProfilePoint.enabled(1, "full", 0.1f, 0.1f, 0L, 0L, 1L),
                        ProfilePoint.enabled(2, "full", 0.5f, 0.5f, 0L, 0L, 1L)
                                .withEnabled(false),
                        ProfilePoint.enabled(3, "missing", 0.5f, 0.5f, 0L, 0L, 1L)),
                Collections.singletonList("test-display"),
                Collections.singletonList(new DisplayRegion("full", new IntRect(0, 0, 1000, 1800))),
                Collections.<UnsafeArea>emptyList());

        List<OverlayClusterPlanner.WindowPlan> plans =
                OverlayRefreshPlanner.plan(TestFixtures.singleRegion(1000, 1800), profile);

        assertEquals(1, plans.size());
        assertEquals("1", plans.get(0).key());
    }

    // orientation contract (spec §7): same (u,v), different
    // region rects per orientation → resolved positions follow the region, not raw pixels.
    @Test
    public void sameUvResolvesPerOrientationRegions() {
        ProfilePoint point = ProfilePoint.enabled(1, "full", 0.25f, 0.5f, 0L, 0L, 1L);
        ScreenProfile profile = TestFixtures.profileWithPoint(point);

        List<OverlayClusterPlanner.WindowPlan> portrait = OverlayRefreshPlanner.plan(
                TestFixtures.singleRegion(1000, 1800), profile);
        List<OverlayClusterPlanner.WindowPlan> landscape = OverlayRefreshPlanner.plan(
                TestFixtures.singleRegion(1800, 1000), profile);

        // portrait center x = 250, landscape center x = 450 — window bounds move with the region.
        assertEquals(250, (portrait.get(0).getBounds().getLeft()
                + portrait.get(0).getBounds().getRight()) / 2);
        assertEquals(450, (landscape.get(0).getBounds().getLeft()
                + landscape.get(0).getBounds().getRight()) / 2);
    }
}
