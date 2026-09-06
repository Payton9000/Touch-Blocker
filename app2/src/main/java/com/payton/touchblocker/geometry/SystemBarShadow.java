package com.payton.touchblocker.geometry;

import com.payton.touchblocker.display.EdgeInsets;
import com.payton.touchblocker.display.IntRect;

/**
 * Detects blocking points the system bars will swallow before the overlay ever sees them.
 *
 * <p>A normal app can go no higher than {@code TYPE_APPLICATION_OVERLAY}, which the window manager
 * places at layer 111000. The status bar sits at 151000 and the navigation bar/taskbar higher
 * still, so whenever such a bar is visible it wins the hit-test for every pixel it covers and the
 * overlay window underneath receives nothing. Verified on an emulator: a point centred 36px down
 * blocked 0 of 10 taps with the launcher in front, and 10 of 10 with a fullscreen activity in
 * front that hid the bar.
 *
 * <p>The app deliberately keeps its regions edge-to-edge rather than carving the bars out, because
 * its main use case -- blocking accidental touches during fullscreen video -- is exactly when the
 * bars are hidden and those pixels do work. So a point here must stay enabled; the UI just needs to
 * tell the user it only takes effect while the bar is hidden, instead of appearing to work and
 * silently doing nothing.
 */
public final class SystemBarShadow {
    private SystemBarShadow() {
    }

    /**
     * @param bounds the display bounds the point was resolved against.
     * @param systemBarInsets the system-bar insets, ignoring visibility.
     * @param centerX resolved point centre, in the same space as {@code bounds}.
     * @param centerY resolved point centre, in the same space as {@code bounds}.
     * @return {@code true} when a visible system bar would consume touches at this point.
     */
    public static boolean isShadowed(
            IntRect bounds, EdgeInsets systemBarInsets, float centerX, float centerY) {
        if (bounds == null || systemBarInsets == null) {
            return false;
        }
        if (centerX < bounds.getLeft() || centerX >= bounds.getRight()
                || centerY < bounds.getTop() || centerY >= bounds.getBottom()) {
            return false;
        }
        return centerY < bounds.getTop() + Math.max(0, systemBarInsets.getTop())
                || centerY >= bounds.getBottom() - Math.max(0, systemBarInsets.getBottom())
                || centerX < bounds.getLeft() + Math.max(0, systemBarInsets.getLeft())
                || centerX >= bounds.getRight() - Math.max(0, systemBarInsets.getRight());
    }
}
