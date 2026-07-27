package com.payton.touchblocker.display;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CutoutInsetsCache {
    private static final CutoutInsetsCache INSTANCE = new CutoutInsetsCache();

    private final Map<Integer, List<IntRect>> cutoutsByDisplayId = new HashMap<>();

    CutoutInsetsCache() {
    }

    public static CutoutInsetsCache instance() {
        return INSTANCE;
    }

    public synchronized void update(int displayId, List<IntRect> cutoutBounds) {
        cutoutsByDisplayId.put(displayId, new ArrayList<>(cutoutBounds));
    }

    public synchronized List<IntRect> get(int displayId) {
        List<IntRect> cutoutBounds = cutoutsByDisplayId.get(displayId);
        return cutoutBounds == null ? new ArrayList<IntRect>() : new ArrayList<>(cutoutBounds);
    }
}
