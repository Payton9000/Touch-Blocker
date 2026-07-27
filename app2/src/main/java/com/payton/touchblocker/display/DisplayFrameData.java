package com.payton.touchblocker.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DisplayFrameData {
    private final IntRect bounds;
    private final EdgeInsets safeInsets;
    private final List<IntRect> cutouts;

    private DisplayFrameData(
            IntRect bounds,
            EdgeInsets safeInsets,
            List<IntRect> cutouts
    ) {
        this.bounds = bounds;
        this.safeInsets = safeInsets;
        this.cutouts = cutouts;
    }

    public static DisplayFrameData fromWindowData(
            IntRect bounds,
            EdgeInsets systemBars,
            EdgeInsets displayCutout,
            EdgeInsets mandatoryGestures,
            List<IntRect> windowRelativeCutouts
    ) {
        if (bounds == null) {
            throw new NullPointerException("bounds == null");
        }
        if (systemBars == null) {
            throw new NullPointerException("systemBars == null");
        }
        if (displayCutout == null) {
            throw new NullPointerException("displayCutout == null");
        }
        if (mandatoryGestures == null) {
            throw new NullPointerException("mandatoryGestures == null");
        }
        if (windowRelativeCutouts == null) {
            throw new NullPointerException("windowRelativeCutouts == null");
        }

        EdgeInsets safeInsets = new EdgeInsets(
                max(systemBars.getLeft(), displayCutout.getLeft(), mandatoryGestures.getLeft()),
                max(systemBars.getTop(), displayCutout.getTop(), mandatoryGestures.getTop()),
                max(systemBars.getRight(), displayCutout.getRight(), mandatoryGestures.getRight()),
                max(systemBars.getBottom(), displayCutout.getBottom(), mandatoryGestures.getBottom()));
        ArrayList<IntRect> absoluteCutouts = new ArrayList<>(windowRelativeCutouts.size());
        for (IntRect cutout : windowRelativeCutouts) {
            if (cutout == null) {
                throw new NullPointerException("windowRelativeCutouts contains null");
            }
            absoluteCutouts.add(new IntRect(
                    bounds.getLeft() + cutout.getLeft(),
                    bounds.getTop() + cutout.getTop(),
                    bounds.getLeft() + cutout.getRight(),
                    bounds.getTop() + cutout.getBottom()));
        }
        return new DisplayFrameData(
                bounds,
                safeInsets,
                Collections.unmodifiableList(absoluteCutouts));
    }

    public IntRect getBounds() {
        return bounds;
    }

    public EdgeInsets getSafeInsets() {
        return safeInsets;
    }

    public List<IntRect> getCutouts() {
        return cutouts;
    }

    private static int max(int first, int second, int third) {
        return Math.max(first, Math.max(second, third));
    }
}
