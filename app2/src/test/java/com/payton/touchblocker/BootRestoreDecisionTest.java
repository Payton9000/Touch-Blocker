package com.payton.touchblocker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BootRestoreDecisionTest {

    @Test
    public void allowsBootRestoreWhenAllRequirementsAreMet() {
        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                true,
                true,
                true,
                true
        );

        assertTrue(decision.shouldStart());
        assertEquals(BootRestoreDecision.FailureReason.NONE, decision.getFailureReason());
    }

    @Test
    public void skipsBootRestoreWhenFeatureIsDisabled() {
        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                false,
                true,
                true,
                true
        );

        assertEquals(BootRestoreDecision.FailureReason.AUTO_START_DISABLED, decision.getFailureReason());
    }

    @Test
    public void skipsBootRestoreWhenOverlayWasManuallyTurnedOff() {
        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                true,
                false,
                true,
                true
        );

        assertEquals(BootRestoreDecision.FailureReason.OVERLAY_DISABLED, decision.getFailureReason());
    }

    @Test
    public void reportsMissingPermissionBeforeCheckingPoints() {
        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                true,
                true,
                false,
                false
        );

        assertEquals(BootRestoreDecision.FailureReason.MISSING_PERMISSION, decision.getFailureReason());
    }

    @Test
    public void reportsMissingEnabledPointsWhenPermissionIsAvailable() {
        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                true,
                true,
                true,
                false
        );

        assertEquals(BootRestoreDecision.FailureReason.NO_ENABLED_POINTS, decision.getFailureReason());
    }
}
