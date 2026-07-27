package com.payton.touchblocker.display;

import java.util.HashMap;
import java.util.Map;

final class DisplayFoldCache {
    private final Map<Integer, FoldFeatureData> foldsByDisplayId = new HashMap<>();

    public synchronized void update(int displayId, FoldFeatureData foldFeature) {
        if (foldFeature == null) {
            foldsByDisplayId.remove(displayId);
        } else {
            foldsByDisplayId.put(displayId, foldFeature);
        }
    }

    public synchronized FoldFeatureData get(int displayId) {
        return foldsByDisplayId.get(displayId);
    }
}
