package com.payton.touchblocker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps the recording marker feedback visible only for a short, fading interval. */
final class RecordingFeedback {
    static final long FADE_DURATION_MS = 10_000L;
    static final int MAX_ALPHA = 0xCC;

    private final ArrayList<float[]> markers = new ArrayList<>();
    private long shownAtMs;

    void replace(List<float[]> values, long nowMs) {
        markers.clear();
        if (values != null) {
            for (float[] value : values) {
                if (value != null && value.length >= 3) {
                    markers.add(new float[]{value[0], value[1], value[2]});
                }
            }
        }
        shownAtMs = nowMs;
    }

    boolean hasVisibleMarkers(long nowMs) {
        return !markers.isEmpty() && alphaAt(nowMs) > 0;
    }

    int alphaAt(long nowMs) {
        if (markers.isEmpty()) {
            return 0;
        }
        long elapsedMs = Math.max(0L, nowMs - shownAtMs);
        if (elapsedMs >= FADE_DURATION_MS) {
            return 0;
        }
        return Math.round(MAX_ALPHA * (FADE_DURATION_MS - elapsedMs)
                / (float) FADE_DURATION_MS);
    }

    List<float[]> markers(long nowMs) {
        if (!hasVisibleMarkers(nowMs)) {
            return Collections.emptyList();
        }
        ArrayList<float[]> copies = new ArrayList<>(markers.size());
        for (float[] marker : markers) {
            copies.add(new float[]{marker[0], marker[1], marker[2]});
        }
        return Collections.unmodifiableList(copies);
    }
}
