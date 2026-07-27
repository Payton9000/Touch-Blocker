package com.payton.touchblocker.display;

public final class FoldFeatureData {
    private final IntRect bounds;
    private final boolean separating;
    private final boolean fullOcclusion;

    public FoldFeatureData(IntRect bounds, boolean separating) {
        this(bounds, separating, true);
    }

    public FoldFeatureData(IntRect bounds, boolean separating, boolean fullOcclusion) {
        if (bounds == null) {
            throw new NullPointerException("bounds == null");
        }
        this.bounds = bounds;
        this.separating = separating;
        this.fullOcclusion = fullOcclusion;
    }

    public IntRect getBounds() {
        return bounds;
    }

    public boolean isSeparating() {
        return separating;
    }

    public boolean isFullOcclusion() {
        return fullOcclusion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FoldFeatureData)) {
            return false;
        }
        FoldFeatureData that = (FoldFeatureData) other;
        return separating == that.separating
                && fullOcclusion == that.fullOcclusion
                && bounds.equals(that.bounds);
    }

    @Override
    public int hashCode() {
        int result = bounds.hashCode();
        result = 31 * result + (separating ? 1 : 0);
        result = 31 * result + (fullOcclusion ? 1 : 0);
        return result;
    }
}
