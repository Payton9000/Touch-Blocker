package com.payton.touchblocker.display;

import com.payton.touchblocker.profile.PointDisabledReason;

public final class UnsafeArea {
    private final IntRect bounds;
    private final PointDisabledReason reason;

    public UnsafeArea(IntRect bounds, PointDisabledReason reason) {
        if (bounds == null) {
            throw new NullPointerException("bounds == null");
        }
        if (reason == null) {
            throw new NullPointerException("reason == null");
        }
        if (reason == PointDisabledReason.NONE) {
            throw new IllegalArgumentException("reason == NONE");
        }
        this.bounds = bounds;
        this.reason = reason;
    }

    public IntRect getBounds() {
        return bounds;
    }

    public PointDisabledReason getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UnsafeArea)) {
            return false;
        }
        UnsafeArea that = (UnsafeArea) other;
        return bounds.equals(that.bounds) && reason == that.reason;
    }

    @Override
    public int hashCode() {
        int result = bounds.hashCode();
        result = 31 * result + reason.hashCode();
        return result;
    }
}
