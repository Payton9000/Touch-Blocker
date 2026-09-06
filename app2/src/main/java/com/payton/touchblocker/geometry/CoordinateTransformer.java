package com.payton.touchblocker.geometry;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.PointDisabledReason;
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
        return resolveNormalized(snapshot, point, diameterDp);
    }

    /**
     * Resolves {@code point} to its screen position without rejecting it for landing in a cutout
     * or on a fold hinge.
     *
     * <p>{@link #resolve} collapses "outside every region" and "inside an unsafe area" into the
     * same {@code null}, which cost the caller the reason for the rejection: every such point was
     * then recorded as {@code OUT_OF_BOUNDS}, and {@link
     * com.payton.touchblocker.profile.ProfileRevalidator} only ever re-enables points disabled
     * for {@code CUTOUT} or {@code HINGE}. A blocker that a rotation pushed under the camera hole
     * was therefore disabled permanently and never came back when the user rotated away again.
     * Validation uses this to classify the rejection instead of guessing.
     */
    public static ResolvedPoint resolveIgnoringUnsafeAreas(
            DisplaySnapshot snapshot, ProfilePoint point, float diameterDp) {
        if (snapshot == null || point == null) {
            return null;
        }
        if (!point.hasNaturalAnchor()) {
            return resolveNormalized(snapshot, point, diameterDp);
        }
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
        return new ResolvedPoint(
                bounds.getLeft() + rotated[0],
                bounds.getTop() + rotated[1],
                diameterDp * snapshot.getDensity());
    }

    /**
     * Returns the reason {@code x, y} is unusable, or {@code null} when the position is fine.
     * An unsafe area (cutout, hinge) is reported with its own reason so the point can be
     * re-enabled automatically once the display geometry moves it back into a usable spot.
     */
    public static PointDisabledReason rejectionReasonAt(
            DisplaySnapshot snapshot, float x, float y) {
        if (snapshot == null) {
            return PointDisabledReason.INVALID_DATA;
        }
        for (UnsafeArea unsafeArea : snapshot.getUnsafeAreas()) {
            IntRect bounds = unsafeArea.getBounds();
            if (x >= bounds.getLeft() && x < bounds.getRight()
                    && y >= bounds.getTop() && y < bounds.getBottom()) {
                return unsafeArea.getReason();
            }
        }
        for (DisplayRegion region : snapshot.getRegions()) {
            IntRect bounds = region.getBounds();
            if (x >= bounds.getLeft() && x < bounds.getRight()
                    && y >= bounds.getTop() && y < bounds.getBottom()) {
                return null;
            }
        }
        return PointDisabledReason.OUT_OF_BOUNDS;
    }

    private static ResolvedPoint resolveNormalized(
            DisplaySnapshot snapshot, ProfilePoint point, float diameterDp) {
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
