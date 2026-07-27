package com.payton.touchblocker.geometry;

import static org.junit.Assert.*;

import com.payton.touchblocker.display.IntRect;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OverlayClusterPlannerTest {
    private static OverlayClusterPlanner.PlannedCircle circle(int id, float cx, float cy, float d) {
        return new OverlayClusterPlanner.PlannedCircle(id, cx, cy, d);
    }

    @Test
    public void emptyInputYieldsNoWindows() {
        assertTrue(OverlayClusterPlanner.plan(
                Collections.<OverlayClusterPlanner.PlannedCircle>emptyList(), 2f).isEmpty());
    }

    @Test
    public void singleCircleYieldsItsOwnSquare() {
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayClusterPlanner.plan(
                Collections.singletonList(circle(1, 100f, 100f, 40f)), 2f);
        assertEquals(1, plans.size());
        assertEquals(new IntRect(80, 80, 120, 120), plans.get(0).getBounds());
        assertEquals("1", plans.get(0).key());
    }

    @Test
    public void overlappingCirclesMergeIntoOneWindow() {
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayClusterPlanner.plan(
                Arrays.asList(circle(1, 100f, 100f, 40f), circle(2, 130f, 100f, 40f)), 2f);
        assertEquals(1, plans.size());
        assertEquals(new IntRect(80, 80, 150, 120), plans.get(0).getBounds());
        assertEquals("1,2", plans.get(0).key());
    }

    @Test
    public void gapWithinFourDpMerges() {
        // density 2 → 4dp = 8px. Squares [80,120] and [126,166): gap 6px < 8px.
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayClusterPlanner.plan(
                Arrays.asList(circle(1, 100f, 100f, 40f), circle(2, 146f, 100f, 40f)), 2f);
        assertEquals(1, plans.size());
    }

    @Test
    public void gapBeyondFourDpDoesNotMerge() {
        // gap = 20px > 8px
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayClusterPlanner.plan(
                Arrays.asList(circle(1, 100f, 100f, 40f), circle(2, 160f, 100f, 40f)), 2f);
        assertEquals(2, plans.size());
    }

    @Test
    public void wastefulClusterFallsBackToIndividualWindows() {
        // L-shape: three 40px squares chained diagonally-adjacent so they connect,
        // but bbox 120x120=14400 > 1.25 * union(3*1600 - overlaps) → fallback.
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayClusterPlanner.plan(
                Arrays.asList(
                        circle(1, 20f, 20f, 40f),
                        circle(2, 58f, 58f, 40f),
                        circle(3, 96f, 96f, 40f)), 1f);
        assertEquals(3, plans.size());
    }

    @Test
    public void chainMergesTransitively() {
        // A overlaps B, B overlaps C, A far from C → still one cluster (waste ok: straight line).
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayClusterPlanner.plan(
                Arrays.asList(
                        circle(1, 100f, 100f, 40f),
                        circle(2, 135f, 100f, 40f),
                        circle(3, 170f, 100f, 40f)), 2f);
        assertEquals(1, plans.size());
        assertEquals("1,2,3", plans.get(0).key());
    }
}
