package com.payton.touchblocker.profile;

public final class ProfilePoint {
    private static final float NO_NATURAL_ANCHOR = -1f;

    private final int id;
    private final String regionId;
    private final float u;
    private final float v;
    private final float diameterDpOverride;
    private final boolean enabled;
    private final PointDisabledReason disabledReason;
    private final long timestamp;
    private final long durationMs;
    private final long displayGeneration;
    private final float naturalU;
    private final float naturalV;

    public static ProfilePoint enabled(
            int id,
            String regionId,
            float u,
            float v,
            long timestamp,
            long durationMs,
            long displayGeneration
    ) {
        return new ProfilePoint(
                id,
                regionId,
                u,
                v,
                0f,
                true,
                PointDisabledReason.NONE,
                timestamp,
                durationMs,
                displayGeneration,
                NO_NATURAL_ANCHOR,
                NO_NATURAL_ANCHOR
        );
    }

    /** Creates a point anchored to the display's natural (unrotated) coordinate space. */
    public static ProfilePoint enabledWithNaturalAnchor(
            int id,
            String regionId,
            float u,
            float v,
            float naturalU,
            float naturalV,
            long timestamp,
            long durationMs,
            long displayGeneration
    ) {
        return new ProfilePoint(
                id, regionId, u, v, 0f, true, PointDisabledReason.NONE,
                timestamp, durationMs, displayGeneration, naturalU, naturalV);
    }

    public ProfilePoint(
            int id,
            String regionId,
            float u,
            float v,
            float diameterDpOverride,
            boolean enabled,
            PointDisabledReason disabledReason,
            long timestamp,
            long durationMs,
            long displayGeneration
    ) {
        this(
                id, regionId, u, v, diameterDpOverride, enabled, disabledReason,
                timestamp, durationMs, displayGeneration,
                NO_NATURAL_ANCHOR, NO_NATURAL_ANCHOR);
    }

    public ProfilePoint(
            int id,
            String regionId,
            float u,
            float v,
            float diameterDpOverride,
            boolean enabled,
            PointDisabledReason disabledReason,
            long timestamp,
            long durationMs,
            long displayGeneration,
            float naturalU,
            float naturalV
    ) {
        if (regionId == null) {
            throw new NullPointerException("regionId == null");
        }
        if (disabledReason == null) {
            throw new NullPointerException("disabledReason == null");
        }
        this.id = id;
        this.regionId = regionId;
        this.u = u;
        this.v = v;
        this.diameterDpOverride = diameterDpOverride;
        this.enabled = enabled;
        this.disabledReason = disabledReason;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.displayGeneration = displayGeneration;
        this.naturalU = naturalU;
        this.naturalV = naturalV;
    }

    /**
     * Returns a copy moved to a new position, in both region-relative and natural coordinates.
     *
     * <p>The natural anchor is what keeps a point on the same physical spot on the glass when the
     * screen rotates, so moving a point requires recomputing it -- use
     * {@link com.payton.touchblocker.geometry.CoordinateTransformer#naturalAnchor}. It is a required
     * parameter on purpose: the earlier signature took only {@code u}/{@code v} and silently reset
     * the anchor, which would have made any moved point drift across the screen on every rotation.
     * Pass {@code naturalU}/{@code naturalV} outside 0..1 only when the anchor genuinely cannot be
     * computed, which downgrades the point to the non-rotation-invariant path.
     */
    public ProfilePoint withPosition(
            String regionId,
            float u,
            float v,
            float naturalU,
            float naturalV,
            long generation) {
        return new ProfilePoint(
                id,
                regionId,
                u,
                v,
                diameterDpOverride,
                enabled,
                disabledReason,
                timestamp,
                durationMs,
                generation,
                naturalU,
                naturalV
        );
    }

    public ProfilePoint withDiameterDpOverride(float diameterDpOverride) {
        return new ProfilePoint(
                id,
                regionId,
                u,
                v,
                diameterDpOverride,
                enabled,
                disabledReason,
                timestamp,
                durationMs,
                displayGeneration,
                naturalU,
                naturalV
        );
    }

    public ProfilePoint withEnabled(boolean enabled) {
        return new ProfilePoint(
                id,
                regionId,
                u,
                v,
                diameterDpOverride,
                enabled,
                enabled ? PointDisabledReason.NONE : disabledReason,
                timestamp,
                durationMs,
                displayGeneration,
                naturalU,
                naturalV
        );
    }

    public ProfilePoint disabled(PointDisabledReason reason) {
        return new ProfilePoint(
                id,
                regionId,
                u,
                v,
                diameterDpOverride,
                false,
                reason,
                timestamp,
                durationMs,
                displayGeneration,
                naturalU,
                naturalV
        );
    }

    public int getId() {
        return id;
    }

    public String getRegionId() {
        return regionId;
    }

    public float getU() {
        return u;
    }

    public float getV() {
        return v;
    }

    public float getDiameterDpOverride() {
        return diameterDpOverride;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PointDisabledReason getDisabledReason() {
        return disabledReason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getDisplayGeneration() {
        return displayGeneration;
    }

    public boolean hasNaturalAnchor() {
        return isUnit(naturalU) && isUnit(naturalV);
    }

    public float getNaturalU() {
        return naturalU;
    }

    public float getNaturalV() {
        return naturalV;
    }

    private static boolean isUnit(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value)
                && value >= 0f && value <= 1f;
    }
}
