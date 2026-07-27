package com.payton.touchblocker.display;

public final class DisplayRegion {
    private final String id;
    private final IntRect bounds;

    public DisplayRegion(String id, IntRect bounds) {
        if (id == null) {
            throw new NullPointerException("id == null");
        }
        if (bounds == null) {
            throw new NullPointerException("bounds == null");
        }
        this.id = id;
        this.bounds = bounds;
    }

    public String getId() {
        return id;
    }

    public IntRect getBounds() {
        return bounds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisplayRegion)) {
            return false;
        }
        DisplayRegion that = (DisplayRegion) other;
        return id.equals(that.id) && bounds.equals(that.bounds);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + bounds.hashCode();
        return result;
    }
}
