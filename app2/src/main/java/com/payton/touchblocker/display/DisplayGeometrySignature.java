package com.payton.touchblocker.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DisplayGeometrySignature {
    private final IntRect bounds;
    private final int rotation;
    private final EdgeInsets safeInsets;
    private final FoldFeatureData foldFeature;
    private final List<IntRect> cutouts;

    public DisplayGeometrySignature(
            IntRect bounds,
            int rotation,
            EdgeInsets safeInsets,
            FoldFeatureData foldFeature,
            List<IntRect> cutouts
    ) {
        if (bounds == null) {
            throw new NullPointerException("bounds == null");
        }
        if (safeInsets == null) {
            throw new NullPointerException("safeInsets == null");
        }
        if (cutouts == null) {
            throw new NullPointerException("cutouts == null");
        }
        this.bounds = bounds;
        this.rotation = rotation;
        this.safeInsets = safeInsets;
        this.foldFeature = foldFeature;
        ArrayList<IntRect> cutoutCopy = new ArrayList<>(cutouts.size());
        for (IntRect cutout : cutouts) {
            if (cutout == null) {
                throw new NullPointerException("cutouts contains null");
            }
            cutoutCopy.add(cutout);
        }
        this.cutouts = Collections.unmodifiableList(cutoutCopy);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisplayGeometrySignature)) {
            return false;
        }
        DisplayGeometrySignature that = (DisplayGeometrySignature) other;
        if (rotation != that.rotation
                || !bounds.equals(that.bounds)
                || !safeInsets.equals(that.safeInsets)
                || !cutouts.equals(that.cutouts)) {
            return false;
        }
        return foldFeature == null ? that.foldFeature == null : foldFeature.equals(that.foldFeature);
    }

    @Override
    public int hashCode() {
        int result = bounds.hashCode();
        result = 31 * result + rotation;
        result = 31 * result + safeInsets.hashCode();
        result = 31 * result + (foldFeature == null ? 0 : foldFeature.hashCode());
        result = 31 * result + cutouts.hashCode();
        return result;
    }
}
