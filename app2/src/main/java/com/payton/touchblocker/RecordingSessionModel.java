package com.payton.touchblocker;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.geometry.CoordinateTransformer;
import com.payton.touchblocker.geometry.ResolvedPoint;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Holds the unsaved points collected during one recording session. */
public final class RecordingSessionModel {
    public enum Outcome {
        NEW_POINT,
        MERGED,
        COVERED_EXISTING,
        LIMIT_REACHED,
        OUTSIDE_SAFE_REGION
    }

    private final ScreenProfile profile;
    private final DisplaySnapshot snapshot;
    private final float globalDiameterPx;
    private final ArrayList<DraftPoint> drafts = new ArrayList<>();
    private int skippedCount;

    public RecordingSessionModel(ScreenProfile profile, DisplaySnapshot snapshot) {
        if (profile == null) {
            throw new NullPointerException("profile == null");
        }
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }
        this.profile = profile;
        this.snapshot = snapshot;
        this.globalDiameterPx = profile.getGlobalDiameterDp() * snapshot.getDensity();
    }

    public Outcome onTap(float rawX, float rawY, long timestamp, long durationMs) {
        if (!isInsideRecordingRegion(rawX, rawY)) {
            skippedCount++;
            return Outcome.OUTSIDE_SAFE_REGION;
        }
        if (isCoveredByExistingPoint(rawX, rawY)) {
            skippedCount++;
            return Outcome.COVERED_EXISTING;
        }

        DraftPoint mergeTarget = findMergeTarget(rawX, rawY);
        if (mergeTarget != null) {
            mergeTarget.merge(rawX, rawY, globalDiameterPx);
            skippedCount++;
            return Outcome.MERGED;
        }

        if (profile.getPoints().size() + drafts.size() >= ScreenProfile.MAX_POINTS) {
            return Outcome.LIMIT_REACHED;
        }

        drafts.add(new DraftPoint(rawX, rawY, globalDiameterPx, timestamp, durationMs));
        return Outcome.NEW_POINT;
    }

    public int getNewPointCount() {
        return drafts.size();
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public boolean undoLast() {
        if (drafts.isEmpty()) {
            return false;
        }
        drafts.remove(drafts.size() - 1);
        return true;
    }

    /** Returns defensive copies of {screenX, screenY, radiusPx} for recording feedback. */
    public List<float[]> getSessionCentersAndRadiiPx() {
        ArrayList<float[]> values = new ArrayList<>(drafts.size());
        for (DraftPoint draft : drafts) {
            values.add(new float[]{draft.cx, draft.cy, draft.diameterPx / 2f});
        }
        return Collections.unmodifiableList(values);
    }

    public ScreenProfile buildUpdatedProfile() {
        ArrayList<ProfilePoint> points = new ArrayList<>(profile.getPoints());
        int nextId = maxExistingId() + 1;
        for (DraftPoint draft : drafts) {
            DisplayRegion region = findRegionFor(draft.cx, draft.cy);
            IntRect bounds = region.getBounds();
            float u = clamp((draft.cx - bounds.getLeft()) / bounds.width(), 0f, 1f);
            float v = clamp((draft.cy - bounds.getTop()) / bounds.height(), 0f, 1f);
            float[] naturalAnchor = CoordinateTransformer.naturalAnchor(
                    snapshot, draft.cx, draft.cy);
            if (naturalAnchor == null) {
                points.add(ProfilePoint.enabled(nextId++, region.getId(), u, v,
                        draft.timestamp, draft.durationMs, snapshot.getGeneration()));
            } else {
                points.add(ProfilePoint.enabledWithNaturalAnchor(
                        nextId++, region.getId(), u, v, naturalAnchor[0], naturalAnchor[1],
                        draft.timestamp, draft.durationMs, snapshot.getGeneration()));
            }
        }
        return new ScreenProfile(
                profile.getId(),
                profile.getKind(),
                profile.getGlobalDiameterDp(),
                points,
                profile.getFingerprints(),
                profile.getReferenceRegions(),
                profile.getReferenceUnsafeAreas());
    }

    private boolean isCoveredByExistingPoint(float rawX, float rawY) {
        int x = Math.round(rawX);
        int y = Math.round(rawY);
        for (ProfilePoint point : profile.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride() : profile.getGlobalDiameterDp();
            ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, diameterDp);
            if (resolved == null) {
                continue;
            }
            int diameterPx = Math.max(1, Math.round(resolved.getDiameterPx()));
            if (OverlayWindowGeometry.centeredAt(
                    resolved.getCenterX(), resolved.getCenterY(), diameterPx)
                    .getBounds().contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private DraftPoint findMergeTarget(float rawX, float rawY) {
        for (DraftPoint draft : drafts) {
            float dx = rawX - draft.cx;
            float dy = rawY - draft.cy;
            float distance = (float) Math.hypot(dx, dy);
            float threshold = Math.max(
                    0.6f * (draft.diameterPx / 2f + globalDiameterPx / 2f),
                    16f * snapshot.getDensity());
            if (distance <= threshold) {
                return draft;
            }
        }
        return null;
    }

    private int maxExistingId() {
        int maxId = 0;
        for (ProfilePoint point : profile.getPoints()) {
            maxId = Math.max(maxId, point.getId());
        }
        return maxId;
    }

    private DisplayRegion findRegionFor(float x, float y) {
        int roundedX = Math.round(x);
        int roundedY = Math.round(y);
        DisplayRegion nearest = null;
        float nearestDistanceSquared = Float.MAX_VALUE;
        for (DisplayRegion region : snapshot.getRegions()) {
            IntRect bounds = region.getBounds();
            if (bounds.contains(roundedX, roundedY)) {
                return region;
            }
            float centerX = bounds.getLeft() + bounds.width() / 2f;
            float centerY = bounds.getTop() + bounds.height() / 2f;
            float dx = x - centerX;
            float dy = y - centerY;
            float distanceSquared = dx * dx + dy * dy;
            if (nearest == null || distanceSquared < nearestDistanceSquared) {
                nearest = region;
                nearestDistanceSquared = distanceSquared;
            }
        }
        if (nearest == null) {
            throw new IllegalStateException("snapshot has no display regions");
        }
        return nearest;
    }

    private boolean isInsideRecordingRegion(float x, float y) {
        int roundedX = Math.round(x);
        int roundedY = Math.round(y);
        for (UnsafeArea unsafeArea : snapshot.getUnsafeAreas()) {
            if (unsafeArea.getBounds().contains(roundedX, roundedY)) {
                return false;
            }
        }
        for (DisplayRegion region : snapshot.getRegions()) {
            if (region.getBounds().contains(roundedX, roundedY)) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class DraftPoint {
        private float cx;
        private float cy;
        private float diameterPx;
        private int tapCount = 1;
        private final long timestamp;
        private final long durationMs;

        private DraftPoint(float cx, float cy, float diameterPx, long timestamp, long durationMs) {
            this.cx = cx;
            this.cy = cy;
            this.diameterPx = diameterPx;
            this.timestamp = timestamp;
            this.durationMs = durationMs;
        }

        private void merge(float rawX, float rawY, float globalDiameterPx) {
            float dx = rawX - cx;
            float dy = rawY - cy;
            float distance = (float) Math.hypot(dx, dy);
            cx = (cx * tapCount + rawX) / (tapCount + 1);
            cy = (cy * tapCount + rawY) / (tapCount + 1);
            diameterPx = Math.max(diameterPx, 2f * distance + globalDiameterPx);
            tapCount++;
        }
    }
}
