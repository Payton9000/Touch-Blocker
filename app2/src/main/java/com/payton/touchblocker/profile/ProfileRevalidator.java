package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.geometry.CoordinateTransformer;
import com.payton.touchblocker.geometry.PointValidation;
import com.payton.touchblocker.geometry.PointValidator;
import com.payton.touchblocker.geometry.ResolvedPoint;

import java.util.ArrayList;
import java.util.List;

public final class ProfileRevalidator {
    public ScreenProfile revalidate(ScreenProfile profile, DisplaySnapshot snapshot) {
        requireInputs(profile, snapshot);
        List<ProfilePoint> original = profile.getPoints();
        ArrayList<ProfilePoint> updated = new ArrayList<>(original);
        boolean changed = false;
        for (int index = 0; index < original.size(); index++) {
            ProfilePoint point = original.get(index);
            if (point.isEnabled()) {
                PointValidation validation = validate(profile, point, snapshot);
                if (!validation.isValid()) {
                    updated.set(index, point.disabled(validation.getReason()));
                    changed = true;
                }
                continue;
            }
            if (point.getDisabledReason() != PointDisabledReason.CUTOUT
                    && point.getDisabledReason() != PointDisabledReason.HINGE) {
                continue;
            }
            PointValidation validation = validate(profile, point, snapshot);
            if (validation.isValid()) {
                updated.set(index, point.withEnabled(true));
                changed = true;
            } else if (validation.getReason() != point.getDisabledReason()) {
                updated.set(index, point.disabled(validation.getReason()));
                changed = true;
            }
        }
        return changed ? copyWithPoints(profile, updated) : profile;
    }

    public ScreenProfile setPointEnabled(
            ScreenProfile profile,
            int pointId,
            boolean enabled,
            DisplaySnapshot snapshot
    ) {
        if (profile == null) {
            throw new NullPointerException("profile == null");
        }
        requireSupportedPointCount(profile);
        List<ProfilePoint> original = profile.getPoints();
        int targetIndex = -1;
        for (int index = 0; index < original.size(); index++) {
            if (original.get(index).getId() == pointId) {
                targetIndex = index;
                break;
            }
        }
        if (targetIndex < 0) {
            return profile;
        }

        ProfilePoint target = original.get(targetIndex);
        ProfilePoint replacement;
        if (!enabled) {
            if (!target.isEnabled()) {
                return profile;
            }
            replacement = target.disabled(PointDisabledReason.NEEDS_REVIEW);
        } else {
            if (snapshot == null) {
                throw new NullPointerException("snapshot == null");
            }
            PointValidation validation = validate(profile, target, snapshot);
            if (validation.isValid()) {
                if (target.isEnabled()
                        && target.getDisabledReason() == PointDisabledReason.NONE) {
                    return profile;
                }
                replacement = target.withEnabled(true);
            } else {
                if (!target.isEnabled()
                        && target.getDisabledReason() == validation.getReason()) {
                    return profile;
                }
                replacement = target.disabled(validation.getReason());
            }
        }

        ArrayList<ProfilePoint> updated = new ArrayList<>(original);
        updated.set(targetIndex, replacement);
        return copyWithPoints(profile, updated);
    }

    private static PointValidation validate(
            ScreenProfile profile,
            ProfilePoint point,
            DisplaySnapshot snapshot
    ) {
        float diameterDp = point.getDiameterDpOverride() > 0f
                ? point.getDiameterDpOverride()
                : profile.getGlobalDiameterDp();
        ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, diameterDp);
        return PointValidator.validate(snapshot, point, resolved);
    }

    private static void requireInputs(ScreenProfile profile, DisplaySnapshot snapshot) {
        if (profile == null) {
            throw new NullPointerException("profile == null");
        }
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }
        requireSupportedPointCount(profile);
    }

    private static void requireSupportedPointCount(ScreenProfile profile) {
        if (profile.getPoints().size() > ScreenProfile.MAX_POINTS) {
            throw new IllegalArgumentException(
                    "profile points exceed maximum of " + ScreenProfile.MAX_POINTS);
        }
    }

    private static ScreenProfile copyWithPoints(
            ScreenProfile profile,
            List<ProfilePoint> points
    ) {
        return new ScreenProfile(
                profile.getId(),
                profile.getKind(),
                profile.getGlobalDiameterDp(),
                points,
                profile.getFingerprints(),
                profile.getReferenceRegions(),
                profile.getReferenceUnsafeAreas()
        );
    }
}
