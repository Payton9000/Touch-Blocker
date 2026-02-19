package com.payton.touchblocker;

public class TouchPoint {
    private final int id;
    private final float x;
    private final float y;
    private final long timestamp;
    private final long durationMs;
    private boolean enabled = true;
    private int sizeOverridePx = -1;
    private float normalizedX = -1f;
    private float normalizedY = -1f;
    private int baseRotation = -1;
    private int baseWidthPx = -1;
    private int baseHeightPx = -1;

    public TouchPoint(int id, float x, float y, long timestamp, long durationMs) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
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

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getSizeOverridePx() {
        return sizeOverridePx;
    }

    public void setSizeOverridePx(int sizeOverridePx) {
        this.sizeOverridePx = sizeOverridePx;
    }

    public float getNormalizedX() {
        return normalizedX;
    }

    public void setNormalizedX(float normalizedX) {
        this.normalizedX = normalizedX;
    }

    public float getNormalizedY() {
        return normalizedY;
    }

    public void setNormalizedY(float normalizedY) {
        this.normalizedY = normalizedY;
    }

    public int getBaseRotation() {
        return baseRotation;
    }

    public void setBaseRotation(int baseRotation) {
        this.baseRotation = baseRotation;
    }

    public int getBaseWidthPx() {
        return baseWidthPx;
    }

    public void setBaseWidthPx(int baseWidthPx) {
        this.baseWidthPx = baseWidthPx;
    }

    public int getBaseHeightPx() {
        return baseHeightPx;
    }

    public void setBaseHeightPx(int baseHeightPx) {
        this.baseHeightPx = baseHeightPx;
    }
}
