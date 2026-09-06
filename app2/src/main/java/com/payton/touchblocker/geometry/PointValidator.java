package com.payton.touchblocker.geometry;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfilePoint;

public final class PointValidator {
    /**
     * Diameter used only to re-resolve a rejected point's centre when classifying why it was
     * rejected. The centre does not depend on the diameter, so any positive value works.
     */
    private static final float DEFAULT_PROBE_DIAMETER_DP = 1f;

    private PointValidator() {
    }

    public static PointValidation validate(
            DisplaySnapshot snapshot, ProfilePoint point, ResolvedPoint resolved) {
        if (snapshot == null || point == null) {
            return invalid(PointDisabledReason.INVALID_DATA);
        }
        if (!isFinite(point.getU())
                || !isFinite(point.getV())
                || !isFinite(snapshot.getDensity())
                || snapshot.getDensity() <= 0f
                || snapshot.getWidthPx() <= 0
                || snapshot.getHeightPx() <= 0) {
            return invalid(PointDisabledReason.INVALID_DATA);
        }
        if (point.hasNaturalAnchor() && resolved == null) {
            // resolve() returns null both for "outside every region" and for "inside a cutout or
            // on the hinge". Re-resolve without the safety check so the real reason survives:
            // recording it as CUTOUT/HINGE is what lets ProfileRevalidator switch the point back
            // on once the display geometry moves it somewhere usable again. Reporting
            // OUT_OF_BOUNDS here disabled such points permanently.
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride()
                    : DEFAULT_PROBE_DIAMETER_DP;
            ResolvedPoint unchecked = CoordinateTransformer.resolveIgnoringUnsafeAreas(
                    snapshot, point, diameterDp);
            if (unchecked == null) {
                return invalid(PointDisabledReason.OUT_OF_BOUNDS);
            }
            PointDisabledReason reason = CoordinateTransformer.rejectionReasonAt(
                    snapshot, unchecked.getCenterX(), unchecked.getCenterY());
            return invalid(reason == null ? PointDisabledReason.OUT_OF_BOUNDS : reason);
        }
        DisplayRegion selectedRegion = point.hasNaturalAnchor()
                ? findContainingRegion(snapshot, resolved)
                : findRegion(snapshot, point.getRegionId());
        if (selectedRegion == null) {
            return invalid(PointDisabledReason.OUT_OF_BOUNDS);
        }
        if (resolved == null
                || !isFinite(resolved.getCenterX())
                || !isFinite(resolved.getCenterY())
                || !isFinite(resolved.getDiameterPx())
                || resolved.getDiameterPx() <= 0f) {
            return invalid(PointDisabledReason.INVALID_DATA);
        }

        float centerX = resolved.getCenterX();
        float centerY = resolved.getCenterY();
        IntRect regionBounds = selectedRegion.getBounds();
        if (centerX < regionBounds.getLeft()
                || centerX >= regionBounds.getRight()
                || centerY < regionBounds.getTop()
                || centerY >= regionBounds.getBottom()) {
            return invalid(PointDisabledReason.OUT_OF_BOUNDS);
        }

        return new PointValidation(true, PointDisabledReason.NONE);
    }

    private static DisplayRegion findRegion(DisplaySnapshot snapshot, String regionId) {
        for (DisplayRegion region : snapshot.getRegions()) {
            if (region.getId().equals(regionId)) {
                return region;
            }
        }
        return null;
    }

    private static DisplayRegion findContainingRegion(
            DisplaySnapshot snapshot, ResolvedPoint resolved) {
        if (resolved == null) {
            return null;
        }
        float centerX = resolved.getCenterX();
        float centerY = resolved.getCenterY();
        for (DisplayRegion region : snapshot.getRegions()) {
            IntRect bounds = region.getBounds();
            if (centerX >= bounds.getLeft() && centerX < bounds.getRight()
                    && centerY >= bounds.getTop() && centerY < bounds.getBottom()) {
                return region;
            }
        }
        return null;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static PointValidation invalid(PointDisabledReason reason) {
        return new PointValidation(false, reason);
    }
}
