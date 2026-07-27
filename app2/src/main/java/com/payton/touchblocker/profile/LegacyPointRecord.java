package com.payton.touchblocker.profile;

public final class LegacyPointRecord {
    private final int id;
    private final float x;
    private final float y;
    private final long timestamp;
    private final long durationMs;
    private final boolean enabled;
    private final int sizeOverridePx;
    private final float normalizedX;
    private final float normalizedY;
    private final int baseRotation;
    private final int baseWidthPx;
    private final int baseHeightPx;

    public LegacyPointRecord(
            int id,
            float x,
            float y,
            long timestamp,
            long durationMs,
            boolean enabled,
            int sizeOverridePx,
            float normalizedX,
            float normalizedY,
            int baseRotation,
            int baseWidthPx,
            int baseHeightPx
    ) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.enabled = enabled;
        this.sizeOverridePx = sizeOverridePx;
        this.normalizedX = normalizedX;
        this.normalizedY = normalizedY;
        this.baseRotation = baseRotation;
        this.baseWidthPx = baseWidthPx;
        this.baseHeightPx = baseHeightPx;
    }

    public int getId() {
        return id;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSizeOverridePx() {
        return sizeOverridePx;
    }

    public float getNormalizedX() {
        return normalizedX;
    }

    public float getNormalizedY() {
        return normalizedY;
    }

    public int getBaseRotation() {
        return baseRotation;
    }

    public int getBaseWidthPx() {
        return baseWidthPx;
    }

    public int getBaseHeightPx() {
        return baseHeightPx;
    }
}
