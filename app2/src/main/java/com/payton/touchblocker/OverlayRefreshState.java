package com.payton.touchblocker;

import java.util.HashSet;
import java.util.Set;

/** Tracks whether a refresh can reuse the overlay windows already on screen. */
final class OverlayRefreshState {
    private String lastRefreshStamp;
    private Set<String> lastSuccessfulWindowKeys;

    boolean shouldSkip(String stamp, Set<String> currentWindowKeys) {
        return stamp.equals(lastRefreshStamp)
                && lastSuccessfulWindowKeys != null
                && lastSuccessfulWindowKeys.equals(currentWindowKeys);
    }

    void recordRefreshAttempt(String stamp) {
        lastRefreshStamp = stamp;
    }

    void recordAppliedWindows(Set<String> wantedKeys, Set<String> currentWindowKeys) {
        if (wantedKeys.equals(currentWindowKeys)) {
            lastSuccessfulWindowKeys = new HashSet<>(currentWindowKeys);
        }
    }
}
