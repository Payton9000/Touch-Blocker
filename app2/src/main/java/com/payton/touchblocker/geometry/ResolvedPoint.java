package com.payton.touchblocker.geometry;

public final class ResolvedPoint {
    private final float centerX;
    private final float centerY;
    private final float diameterPx;

    public ResolvedPoint(float centerX, float centerY, float diameterPx) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.diameterPx = diameterPx;
    }

    public float getCenterX() {
        return centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public float getDiameterPx() {
        return diameterPx;
    }
}
