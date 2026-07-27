package com.payton.touchblocker.display;

import com.payton.touchblocker.profile.ProfileKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DisplaySnapshot {
    private final String stableKey;
    private final int displayId;
    private final IntRect bounds;
    private final int rotation;
    private final float density;
    private final long generation;
    private final ProfileKind suggestedKind;
    private final List<DisplayRegion> regions;
    private final List<UnsafeArea> unsafeAreas;

    public DisplaySnapshot(
            String stableKey,
            int displayId,
            int widthPx,
            int heightPx,
            int rotation,
            float density,
            long generation,
            ProfileKind suggestedKind,
            List<DisplayRegion> regions,
            List<UnsafeArea> unsafeAreas
    ) {
        this(
                stableKey,
                displayId,
                new IntRect(0, 0, widthPx, heightPx),
                rotation,
                density,
                generation,
                suggestedKind,
                regions,
                unsafeAreas
        );
    }

    public DisplaySnapshot(
            String stableKey,
            int displayId,
            IntRect bounds,
            int rotation,
            float density,
            long generation,
            ProfileKind suggestedKind,
            List<DisplayRegion> regions,
            List<UnsafeArea> unsafeAreas
    ) {
        if (stableKey == null) {
            throw new NullPointerException("stableKey == null");
        }
        if (bounds == null) {
            throw new NullPointerException("bounds == null");
        }
        this.stableKey = stableKey;
        this.displayId = displayId;
        this.bounds = bounds;
        this.rotation = rotation;
        this.density = density;
        this.generation = generation;
        this.suggestedKind = suggestedKind;
        this.regions = immutableCopy(regions, "regions");
        this.unsafeAreas = immutableCopy(unsafeAreas, "unsafeAreas");
    }

    public String getStableKey() {
        return stableKey;
    }

    public int getDisplayId() {
        return displayId;
    }

    public int getWidthPx() {
        return bounds.width();
    }

    public int getHeightPx() {
        return bounds.height();
    }

    public IntRect getBounds() {
        return bounds;
    }

    public int getRotation() {
        return rotation;
    }

    public float getDensity() {
        return density;
    }

    public long getGeneration() {
        return generation;
    }

    public ProfileKind getSuggestedKind() {
        return suggestedKind;
    }

    public List<DisplayRegion> getRegions() {
        return regions;
    }

    public List<UnsafeArea> getUnsafeAreas() {
        return unsafeAreas;
    }

    private static <T> List<T> immutableCopy(List<T> source, String name) {
        if (source == null) {
            throw new NullPointerException(name + " == null");
        }
        ArrayList<T> copy = new ArrayList<>(source.size());
        for (T value : source) {
            if (value == null) {
                throw new NullPointerException(name + " contains null");
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }
}
