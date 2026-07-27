package com.payton.touchblocker;

/** Shared opacity calculation for the non-debug blocking-overlay fade. */
final class OverlayFadePolicy {
    static final long DURATION_MS = 10_000L;

    private OverlayFadePolicy() {
    }

    static int alphaAt(int initialAlpha, long elapsedMs) {
        int boundedInitial = Math.max(0, Math.min(255, initialAlpha));
        long boundedElapsed = Math.max(0L, elapsedMs);
        if (boundedElapsed >= DURATION_MS) {
            return 0;
        }
        return Math.round(boundedInitial * (DURATION_MS - boundedElapsed)
                / (float) DURATION_MS);
    }
}
