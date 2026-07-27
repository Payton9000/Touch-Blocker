package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.UnsafeArea;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScreenProfile {
    public static final int MAX_POINTS = 64;

    private static final float MIN_DIAMETER_DP = 24f;
    private static final float MAX_DIAMETER_DP = 200f;

    private final String id;
    private final ProfileKind kind;
    private final float globalDiameterDp;
    private final List<ProfilePoint> points;
    private final List<String> fingerprints;
    private final List<DisplayRegion> referenceRegions;
    private final List<UnsafeArea> referenceUnsafeAreas;

    public ScreenProfile(
            String id,
            ProfileKind kind,
            float globalDiameterDp,
            List<ProfilePoint> points,
            List<String> fingerprints,
            List<DisplayRegion> referenceRegions,
            List<UnsafeArea> referenceUnsafeAreas
    ) {
        if (id == null) {
            throw new NullPointerException("id == null");
        }
        if (kind == null) {
            throw new NullPointerException("kind == null");
        }
        this.id = id;
        this.kind = kind;
        this.globalDiameterDp = Math.max(
                MIN_DIAMETER_DP,
                Math.min(MAX_DIAMETER_DP, globalDiameterDp)
        );
        this.points = immutableCopy(points, "points");
        this.fingerprints = immutableCopy(fingerprints, "fingerprints");
        this.referenceRegions = immutableCopy(referenceRegions, "referenceRegions");
        this.referenceUnsafeAreas = immutableCopy(
                referenceUnsafeAreas,
                "referenceUnsafeAreas"
        );
    }

    public String getId() {
        return id;
    }

    public ProfileKind getKind() {
        return kind;
    }

    public float getGlobalDiameterDp() {
        return globalDiameterDp;
    }

    public List<ProfilePoint> getPoints() {
        return points;
    }

    public List<String> getFingerprints() {
        return fingerprints;
    }

    public List<DisplayRegion> getReferenceRegions() {
        return referenceRegions;
    }

    public List<UnsafeArea> getReferenceUnsafeAreas() {
        return referenceUnsafeAreas;
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
