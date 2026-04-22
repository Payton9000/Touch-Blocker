package com.payton.touchblocker;

public class BootRestoreDecision {
    public enum FailureReason {
        NONE,
        AUTO_START_DISABLED,
        OVERLAY_DISABLED,
        MISSING_PERMISSION,
        NO_ENABLED_POINTS
    }

    private final FailureReason failureReason;

    private BootRestoreDecision(FailureReason failureReason) {
        this.failureReason = failureReason;
    }

    public static BootRestoreDecision evaluate(
            boolean autoStartEnabled,
            boolean overlayShouldBeEnabled,
            boolean hasOverlayPermission,
            boolean hasEnabledPoints
    ) {
        if (!autoStartEnabled) {
            return new BootRestoreDecision(FailureReason.AUTO_START_DISABLED);
        }
        if (!overlayShouldBeEnabled) {
            return new BootRestoreDecision(FailureReason.OVERLAY_DISABLED);
        }
        if (!hasOverlayPermission) {
            return new BootRestoreDecision(FailureReason.MISSING_PERMISSION);
        }
        if (!hasEnabledPoints) {
            return new BootRestoreDecision(FailureReason.NO_ENABLED_POINTS);
        }
        return new BootRestoreDecision(FailureReason.NONE);
    }

    public boolean shouldStart() {
        return failureReason == FailureReason.NONE;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }
}
