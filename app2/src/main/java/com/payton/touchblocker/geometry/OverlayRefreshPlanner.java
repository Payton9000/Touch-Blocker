package com.payton.touchblocker.geometry;

import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a {@link ScreenProfile}'s enabled points against the current {@link DisplaySnapshot}
 * and clusters the results into the overlay window plans the service should render.
 */
public final class OverlayRefreshPlanner {
    private OverlayRefreshPlanner() {
    }

    public static List<OverlayClusterPlanner.WindowPlan> plan(
            DisplaySnapshot snapshot, ScreenProfile profile) {
        if (snapshot == null) {
            throw new NullPointerException("snapshot == null");
        }
        if (profile == null) {
            throw new NullPointerException("profile == null");
        }

        List<OverlayClusterPlanner.PlannedCircle> circles = new ArrayList<>();
        for (ProfilePoint point : profile.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride()
                    : profile.getGlobalDiameterDp();
            ResolvedPoint resolved = CoordinateTransformer.resolve(snapshot, point, diameterDp);
            if (resolved == null) {
                continue;
            }
            circles.add(new OverlayClusterPlanner.PlannedCircle(
                    point.getId(),
                    resolved.getCenterX(),
                    resolved.getCenterY(),
                    resolved.getDiameterPx()));
        }
        return OverlayClusterPlanner.plan(circles, snapshot.getDensity());
    }
}
