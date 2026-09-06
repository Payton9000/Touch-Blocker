package com.payton.touchblocker.display;

import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfileKind;

import java.util.ArrayList;
import java.util.List;

public final class DisplaySnapshotMapper {
    private DisplaySnapshotMapper() {
    }

    public static DisplaySnapshot map(
            String stableKey,
            int displayId,
            IntRect windowBounds,
            int rotation,
            float density,
            long generation,
            ProfileKind suggestedKind,
            EdgeInsets safeInsets,
            List<IntRect> cutouts,
            FoldFeatureData foldFeature
    ) {
        if (windowBounds == null) {
            throw new NullPointerException("windowBounds == null");
        }
        if (safeInsets == null) {
            throw new NullPointerException("safeInsets == null");
        }
        if (cutouts == null) {
            throw new NullPointerException("cutouts == null");
        }

        // TouchBlocker is deliberately edge-to-edge: transient status/navigation bars must not
        // turn an entire edge into an unrecordable strip. Precise physical exclusions (cutouts
        // and hinges) are represented below as UnsafeAreas instead.
        IntRect safeBounds = windowBounds;
        ArrayList<DisplayRegion> regions = new ArrayList<>();
        ArrayList<UnsafeArea> unsafeAreas = new ArrayList<>();
        for (IntRect cutout : cutouts) {
            if (cutout == null) {
                throw new NullPointerException("cutouts contains null");
            }
            unsafeAreas.add(new UnsafeArea(cutout, PointDisabledReason.CUTOUT));
        }

        if (foldFeature != null && foldFeature.isSeparating()) {
            IntRect hinge = ensureOccludingSize(foldFeature.getBounds(), windowBounds);
            if (!addSeparatedRegions(regions, safeBounds, hinge)) {
                regions.add(new DisplayRegion("full", safeBounds));
            }
            unsafeAreas.add(new UnsafeArea(hinge, PointDisabledReason.HINGE));
        } else {
            regions.add(new DisplayRegion("full", safeBounds));
            if (foldFeature != null && foldFeature.isFullOcclusion()) {
                unsafeAreas.add(new UnsafeArea(
                        ensureOccludingSize(foldFeature.getBounds(), windowBounds),
                        PointDisabledReason.HINGE));
            }
        }

        return new DisplaySnapshot(
                stableKey,
                displayId,
                windowBounds,
                rotation,
                density,
                generation,
                suggestedKind,
                regions,
                unsafeAreas,
                safeInsets);
    }

    private static boolean addSeparatedRegions(
            List<DisplayRegion> regions,
            IntRect safeBounds,
            IntRect hinge
    ) {
        if (hinge.height() >= hinge.width()) {
            int splitLeft = Math.max(safeBounds.getLeft(), Math.min(safeBounds.getRight(), hinge.getLeft()));
            int splitRight = Math.max(safeBounds.getLeft(), Math.min(safeBounds.getRight(), hinge.getRight()));
            if (splitLeft <= safeBounds.getLeft() || splitRight >= safeBounds.getRight()) {
                return false;
            }
            regions.add(new DisplayRegion("left", new IntRect(
                    safeBounds.getLeft(), safeBounds.getTop(), splitLeft, safeBounds.getBottom())));
            regions.add(new DisplayRegion("right", new IntRect(
                    splitRight, safeBounds.getTop(), safeBounds.getRight(), safeBounds.getBottom())));
            return true;
        }

        int splitTop = Math.max(safeBounds.getTop(), Math.min(safeBounds.getBottom(), hinge.getTop()));
        int splitBottom = Math.max(safeBounds.getTop(), Math.min(safeBounds.getBottom(), hinge.getBottom()));
        if (splitTop <= safeBounds.getTop() || splitBottom >= safeBounds.getBottom()) {
            return false;
        }
        regions.add(new DisplayRegion("top", new IntRect(
                safeBounds.getLeft(), safeBounds.getTop(), safeBounds.getRight(), splitTop)));
        regions.add(new DisplayRegion("bottom", new IntRect(
                safeBounds.getLeft(), splitBottom, safeBounds.getRight(), safeBounds.getBottom())));
        return true;
    }

    private static IntRect ensureOccludingSize(IntRect bounds, IntRect windowBounds) {
        int left = bounds.getLeft();
        int top = bounds.getTop();
        int right = bounds.getRight();
        int bottom = bounds.getBottom();
        if (right <= left) {
            if (left < windowBounds.getRight()) {
                right = left + 1;
            } else {
                left = Math.max(windowBounds.getLeft(), left - 1);
                right = left + 1;
            }
        }
        if (bottom <= top) {
            if (top < windowBounds.getBottom()) {
                bottom = top + 1;
            } else {
                top = Math.max(windowBounds.getTop(), top - 1);
                bottom = top + 1;
            }
        }
        return new IntRect(left, top, right, bottom);
    }
}
