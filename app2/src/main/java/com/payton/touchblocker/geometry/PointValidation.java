package com.payton.touchblocker.geometry;

import com.payton.touchblocker.profile.PointDisabledReason;

public final class PointValidation {
    private final boolean valid;
    private final PointDisabledReason reason;

    public PointValidation(boolean valid, PointDisabledReason reason) {
        if (reason == null) {
            throw new NullPointerException("reason == null");
        }
        if (valid != (reason == PointDisabledReason.NONE)) {
            throw new IllegalArgumentException("valid and reason are inconsistent");
        }
        this.valid = valid;
        this.reason = reason;
    }

    public boolean isValid() {
        return valid;
    }

    public PointDisabledReason getReason() {
        return reason;
    }
}
