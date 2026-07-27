package com.payton.touchblocker;

/**
 * Tracks one recording gesture and distinguishes a tap from a drag.
 */
public final class RecordingGestureTracker {
    private final float touchSlop;
    private boolean active;
    private boolean movedBeyondSlop;
    private float downX;
    private float downY;

    public RecordingGestureTracker(float touchSlop) {
        if (touchSlop < 0f || Float.isNaN(touchSlop) || Float.isInfinite(touchSlop)) {
            throw new IllegalArgumentException("touchSlop must be finite and non-negative");
        }
        this.touchSlop = touchSlop;
    }

    public void onDown(float x, float y) {
        active = true;
        movedBeyondSlop = false;
        downX = x;
        downY = y;
    }

    public void onMove(float x, float y) {
        if (!active) {
            return;
        }
        if (Math.abs(x - downX) > touchSlop || Math.abs(y - downY) > touchSlop) {
            movedBeyondSlop = true;
        }
    }

    public boolean finishAsTap() {
        boolean result = active && !movedBeyondSlop;
        active = false;
        movedBeyondSlop = false;
        return result;
    }

    public void cancel() {
        active = false;
        movedBeyondSlop = false;
    }
}
