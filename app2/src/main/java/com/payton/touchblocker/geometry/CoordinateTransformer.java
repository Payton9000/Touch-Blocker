package com.payton.touchblocker.geometry;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.ProfilePoint;

public final class CoordinateTransformer {
    private CoordinateTransformer() {
    }

    public static ResolvedPoint resolve(
            DisplaySnapshot snapshot, ProfilePoint point, float diameterDp) {
        if (snapshot == null || point == null) {
            return null;
        }
        if (point.hasNaturalAnchor()) {
            return resolveNaturalAnchor(snapshot, point, diameterDp);
        }
        DisplayRegion selectedRegion = null;
        for (DisplayRegion region : snapshot.getRegions()) {
            if (region.getId().equals(point.getRegionId())) {
                selectedRegion = region;
                break;
            }
        }
        if (selectedRegion == null) {
            return null;
        }

        IntRect bounds = selectedRegion.getBounds();
        float x = bounds.getLeft() + point.getU() * bounds.width();
        float y = bounds.getTop() + point.getV() * bounds.height();
        float diameterPx = diameterDp * snapshot.getDensity();
        return new ResolvedPoint(x, y, diameterPx);
    }

    /** Converts a screen-space coordinate to the display's rotation-independent natural space. */
    public static float[] naturalAnchor(DisplaySnapshot snapshot, float screenX, float screenY) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }
        IntRect bounds = snapshot.getBounds();
        int rotation = normalizedRotation(snapshot.getRotation());
        if (rotation < 0 || bounds.width() <= 0 || bounds.height() <= 0) {
            return null;
        }
        int naturalWidth = isQuarterTurn(rotation) ? bounds.height() : bounds.width();
        int naturalHeight = isQuarterTurn(rotation) ? bounds.width() : bounds.height();
        float[] natural = unrotateToNatural(
                screenX - bounds.getLeft(), screenY - bounds.getTop(), rotation,
                naturalWidth, naturalHeight);
        return new float[]{
                clamp(natural[0] / naturalWidth, 0f, 1f),
                clamp(natural[1] / naturalHeight, 0f, 1f)};
    }

    private static ResolvedPoint resolveNaturalAnchor(
            DisplaySnapshot snapshot, ProfilePoint point, float diameterDp) {
        IntRect bounds = snapshot.getBounds();
        int rotation = normalizedRotation(snapshot.getRotation());
        if (rotation < 0 || bounds.width() <= 0 || bounds.height() <= 0) {
            return null;
        }
        int naturalWidth = isQuarterTurn(rotation) ? bounds.height() : bounds.width();
        int naturalHeight = isQuarterTurn(rotation) ? bounds.width() : bounds.height();
        float[] rotated = rotateFromNatural(
                point.getNaturalU() * naturalWidth,
                point.getNaturalV() * naturalHeight,
                rotation,
                naturalWidth,
                naturalHeight);
        float x = bounds.getLeft() + rotated[0];
        float y = bounds.getTop() + rotated[1];
        if (!isInsideSafeRegion(snapshot, x, y)) {
            return null;
        }
        return new ResolvedPoint(x, y, diameterDp * snapshot.getDensity());
    }

    private static boolean isInsideSafeRegion(DisplaySnapshot snapshot, float x, float y) {
        for (UnsafeArea unsafeArea : snapshot.getUnsafeAreas()) {
            IntRect bounds = unsafeArea.getBounds();
            if (x >= bounds.getLeft() && x < bounds.getRight()
                    && y >= bounds.getTop() && y < bounds.getBottom()) {
                return false;
            }
        }
        for (DisplayRegion region : snapshot.getRegions()) {
            IntRect bounds = region.getBounds();
            if (x >= bounds.getLeft() && x < bounds.getRight()
                    && y >= bounds.getTop() && y < bounds.getBottom()) {
                return true;
            }
        }
        return false;
    }

    private static int normalizedRotation(int rotation) {
        return rotation >= 0 && rotation <= 3 ? rotation : -1;
    }

    private static boolean isQuarterTurn(int rotation) {
        return rotation == 1 || rotation == 3;
    }

    private static float[] rotateFromNatural(
            float x, float y, int rotation, int naturalWidth, int naturalHeight) {
        if (rotation == 1) {
            return new float[]{y, naturalWidth - x};
        }
        if (rotation == 2) {
            return new float[]{naturalWidth - x, naturalHeight - y};
        }
        if (rotation == 3) {
            return new float[]{naturalHeight - y, x};
        }
        return new float[]{x, y};
    }

    private static float[] unrotateToNatural(
            float x, float y, int rotation, int naturalWidth, int naturalHeight) {
        if (rotation == 1) {
            return new float[]{naturalWidth - y, x};
        }
        if (rotation == 2) {
            return new float[]{naturalWidth - x, naturalHeight - y};
        }
        if (rotation == 3) {
            return new float[]{y, naturalHeight - x};
        }
        return new float[]{x, y};
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
