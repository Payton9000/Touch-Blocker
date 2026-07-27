package com.payton.touchblocker.ui;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.geometry.CoordinateTransformer;
import com.payton.touchblocker.geometry.ResolvedPoint;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Builds normalized, display-relative primitives for the main-screen preview. */
public final class PreviewModelFactory {
    public static final int KIND_SCREEN = 0;
    public static final int KIND_CUTOUT = 1;
    public static final int KIND_HINGE = 2;
    public static final int KIND_POINT_ENABLED = 3;
    public static final int KIND_POINT_DISABLED = 4;

    private PreviewModelFactory() {
    }

    public static List<Item> build(DisplaySnapshot snapshot, ScreenProfile profile) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }
        if (profile == null) {
            throw new NullPointerException("profile == null");
        }

        ArrayList<Item> items = new ArrayList<>();
        float aspectRatio = snapshot.getHeightPx() > 0
                ? snapshot.getWidthPx() / (float) snapshot.getHeightPx()
                : 1f;
        items.add(new Item(0f, 0f, 1f, 1f, KIND_SCREEN, aspectRatio));
        IntRect snapshotBounds = snapshot.getBounds();
        for (UnsafeArea area : snapshot.getUnsafeAreas()) {
            int kind = unsafeKind(area.getReason());
            if (kind != -1) {
                items.add(normalize(snapshotBounds, area.getBounds(), kind));
            }
        }
        for (ProfilePoint point : profile.getPoints()) {
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride()
                    : profile.getGlobalDiameterDp();
            ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, diameterDp);
            if (resolved == null) {
                continue;
            }
            float radiusPx = resolved.getDiameterPx() / 2f;
            items.add(normalize(
                    snapshotBounds,
                    resolved.getCenterX() - radiusPx,
                    resolved.getCenterY() - radiusPx,
                    resolved.getCenterX() + radiusPx,
                    resolved.getCenterY() + radiusPx,
                    point.isEnabled() ? KIND_POINT_ENABLED : KIND_POINT_DISABLED));
        }
        return Collections.unmodifiableList(items);
    }

    private static int unsafeKind(PointDisabledReason reason) {
        if (reason == PointDisabledReason.CUTOUT) {
            return KIND_CUTOUT;
        }
        if (reason == PointDisabledReason.HINGE) {
            return KIND_HINGE;
        }
        return -1;
    }

    private static Item normalize(IntRect snapshotBounds, IntRect bounds, int kind) {
        return normalize(
                snapshotBounds,
                bounds.getLeft(),
                bounds.getTop(),
                bounds.getRight(),
                bounds.getBottom(),
                kind);
    }

    private static Item normalize(
            IntRect snapshotBounds,
            float left,
            float top,
            float right,
            float bottom,
            int kind
    ) {
        float width = snapshotBounds.width();
        float height = snapshotBounds.height();
        if (width <= 0f || height <= 0f) {
            return new Item(0f, 0f, 0f, 0f, kind, 1f);
        }
        return new Item(
                clamp((left - snapshotBounds.getLeft()) / width),
                clamp((top - snapshotBounds.getTop()) / height),
                clamp((right - snapshotBounds.getLeft()) / width),
                clamp((bottom - snapshotBounds.getTop()) / height),
                kind,
                1f);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public static final class Item {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final int kind;
        public final float aspectRatio;

        private Item(float left, float top, float right, float bottom, int kind, float aspectRatio) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.kind = kind;
            this.aspectRatio = aspectRatio;
        }
    }
}
