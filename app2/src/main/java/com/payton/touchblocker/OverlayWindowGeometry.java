package com.payton.touchblocker;

import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.UnsafeArea;

public final class OverlayWindowGeometry {
    private final IntRect bounds;
    private final int sizePx;

    private OverlayWindowGeometry(IntRect bounds, int sizePx) {
        this.bounds = bounds;
        this.sizePx = sizePx;
    }

    public static OverlayWindowGeometry centeredAt(float centerX, float centerY, int sizePx) {
        if (sizePx <= 0) {
            throw new IllegalArgumentException("sizePx must be positive");
        }
        if (!isFinite(centerX) || !isFinite(centerY)) {
            throw new IllegalArgumentException("center coordinates must be finite");
        }

        int left = roundedOrigin(centerX, sizePx);
        int top = roundedOrigin(centerY, sizePx);
        int right = checkedFarEdge(left, sizePx);
        int bottom = checkedFarEdge(top, sizePx);
        return new OverlayWindowGeometry(new IntRect(left, top, right, bottom), sizePx);
    }

    public IntRect getBounds() {
        return bounds;
    }

    public int getSizePx() {
        return sizePx;
    }

    /**
     * Returns whether this window is fully contained by one safe display region and
     * does not intersect a cutout, hinge, or mandatory gesture area.
     */
    public boolean isSafeFor(DisplaySnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (!contains(snapshot.getBounds(), bounds)) {
            return false;
        }
        for (UnsafeArea unsafeArea : snapshot.getUnsafeAreas()) {
            if (bounds.intersects(unsafeArea.getBounds())) {
                return false;
            }
        }
        for (DisplayRegion region : snapshot.getRegions()) {
            if (contains(region.getBounds(), bounds)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(IntRect outer, IntRect inner) {
        return inner.getLeft() >= outer.getLeft()
                && inner.getTop() >= outer.getTop()
                && inner.getRight() <= outer.getRight()
                && inner.getBottom() <= outer.getBottom();
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static int roundedOrigin(float center, int sizePx) {
        double exactOrigin = (double) center - ((double) sizePx / 2d);
        long roundedOrigin = Math.round(exactOrigin);
        if (roundedOrigin < Integer.MIN_VALUE || roundedOrigin > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("overlay origin is outside integer coordinates");
        }
        return (int) roundedOrigin;
    }

    private static int checkedFarEdge(int origin, int sizePx) {
        long farEdge = (long) origin + sizePx;
        if (farEdge > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("overlay bounds overflow integer coordinates");
        }
        return (int) farEdge;
    }
}
