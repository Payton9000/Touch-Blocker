package com.payton.touchblocker;

import com.payton.touchblocker.geometry.OverlayClusterPlanner;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestBlockViewTest {
    @Test
    public void windowPlanBoundsDefineBlockedTapArea() {
        OverlayClusterPlanner.WindowPlan plan = OverlayClusterPlanner.plan(
                Collections.singletonList(new OverlayClusterPlanner.PlannedCircle(
                        1, 100f, 100f, 100f)),
                1f).get(0);

        assertTrue(TestBlockView.isBlockedByPlans(
                Collections.singletonList(plan), 149f, 149f));
        assertFalse(TestBlockView.isBlockedByPlans(
                Collections.singletonList(plan), 150f, 100f));
    }

    @Test
    public void floatingPointTapBeforeLeftEdgePassesThrough() {
        OverlayClusterPlanner.WindowPlan plan = OverlayClusterPlanner.plan(
                Collections.singletonList(new OverlayClusterPlanner.PlannedCircle(
                        1, 100f, 100f, 100f)),
                1f).get(0);

        assertFalse(TestBlockView.isBlockedByPlans(
                Collections.singletonList(plan), 49.5f, 100f));
    }

    @Test
    public void floatingPointTapBeforeRightEdgeRemainsBlocked() {
        OverlayClusterPlanner.WindowPlan plan = OverlayClusterPlanner.plan(
                Collections.singletonList(new OverlayClusterPlanner.PlannedCircle(
                        1, 100f, 100f, 100f)),
                1f).get(0);

        assertTrue(TestBlockView.isBlockedByPlans(
                Collections.singletonList(plan), 149.5f, 100f));
    }
}
