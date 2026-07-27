package com.payton.touchblocker.geometry;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.profile.ProfilePoint;

public final class CoordinateTransformer {
    private CoordinateTransformer() {
    }

    public static ResolvedPoint resolve(
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
}
