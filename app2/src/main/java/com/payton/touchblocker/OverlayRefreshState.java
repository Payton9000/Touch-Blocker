package com.payton.touchblocker;

import java.util.HashSet;
import java.util.Set;

/** Tracks whether a refresh can reuse the overlay windows already on screen. */
final class OverlayRefreshState {
    private String lastRefreshStamp;
    private Set<String> lastSuccessfulWindowKeys;
    private boolean lastApplyIncomplete;

    boolean shouldSkip(String stamp, Set<String> currentWindowKeys) {
        // A refresh whose previous attempt could not open every window it wanted must always be
        // retried, even when the stamp and the surviving window set both look unchanged. Without
        // this an add that failed once (OEM overlay-window caps are a real cause) left the point
        // listed as enabled while nothing blocked it, until the user happened to rotate or edit.
        return !lastApplyIncomplete
                && stamp.equals(lastRefreshStamp)
                && lastSuccessfulWindowKeys != null
                && lastSuccessfulWindowKeys.equals(currentWindowKeys);
    }

    void recordRefreshAttempt(String stamp) {
        lastRefreshStamp = stamp;
    }

    void recordAppliedWindows(Set<String> wantedKeys, Set<String> currentWindowKeys) {
        lastApplyIncomplete = !wantedKeys.equals(currentWindowKeys);
        if (!lastApplyIncomplete) {
            lastSuccessfulWindowKeys = new HashSet<>(currentWindowKeys);
        }
    }
}
