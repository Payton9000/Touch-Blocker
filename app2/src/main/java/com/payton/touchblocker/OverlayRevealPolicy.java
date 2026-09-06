package com.payton.touchblocker;

/**
 * Decides whether a refresh is allowed to make newly created overlay windows visible for the
 * 10-second fade-out reveal.
 *
 * <p>The reveal exists so the user can see where the blockers landed right after asking for
 * them. A refresh the user did not ask for — a rotation, a fold, a configuration change, or the
 * system restarting the service — must not replay it, or the red dots reappear on every screen
 * change and never go away.
 */
final class OverlayRevealPolicy {
    private OverlayRevealPolicy() {
    }

    /**
     * @param action the {@code OverlayService} intent action being handled, or {@code null} for a
     *     restart with no intent.
     * @return {@code true} only for the user-initiated actions: starting the overlay and
     *     refreshing after a point edit.
     */
    static boolean shouldRevealNewWindows(String action) {
        return OverlayService.ACTION_START_OVERLAY.equals(action)
                || OverlayService.ACTION_REFRESH_POINTS.equals(action);
    }
}
