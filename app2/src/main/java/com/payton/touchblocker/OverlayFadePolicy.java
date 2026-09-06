package com.payton.touchblocker;

/** Shared opacity calculation for the non-debug blocking-overlay fade. */
final class OverlayFadePolicy {
    static final long DURATION_MS = 10_000L;

    /**
     * Number of distinct opacity levels the fade passes through.
     *
     * <p>The animator ticks once per display frame, so a 10-second fade on a 60Hz panel produces
     * about 600 ticks per overlay window. Nobody can see 600 -- or even 180 -- separate opacity
     * levels spread over ten seconds, but every distinct level costs a redraw of every window.
     * Quantising to this many levels makes the fade cost proportional to what is actually visible
     * instead of to the panel's refresh rate: one step every ~400ms, which still reads as a smooth
     * fade because each step moves the alpha by only a few units.
     */
    static final int STEPS = 24;

    private OverlayFadePolicy() {
    }

    static int alphaAt(int initialAlpha, long elapsedMs) {
        int boundedInitial = Math.max(0, Math.min(255, initialAlpha));
        long boundedElapsed = Math.max(0L, elapsedMs);
        if (boundedElapsed >= DURATION_MS) {
            return 0;
        }
        // Round the remaining fraction down onto a step boundary so repeated animator ticks
        // within one step return an identical value and the view can skip the redraw.
        long stepIndex = (boundedElapsed * STEPS) / DURATION_MS;
        long remainingSteps = STEPS - stepIndex;
        return Math.round(boundedInitial * remainingSteps / (float) STEPS);
    }
}
