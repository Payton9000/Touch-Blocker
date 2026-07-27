package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.geometry.PointValidation;
import com.payton.touchblocker.geometry.PointValidator;
import com.payton.touchblocker.geometry.ResolvedPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LegacyProfileMigrator {
    private static final int SCHEMA_VERSION = 2;
    private static final String LEGACY_FULL_REGION_ID = "legacy-full";
    private static final float MIN_DIAMETER_DP = 24f;
    private static final float MAX_DIAMETER_DP = 200f;

    public MigrationResult migrate(
            List<LegacyPointRecord> legacyPoints,
            int legacyGlobalPx,
            DisplaySnapshot snapshot,
            ProfileKind kind
    ) {
        ArrayList<String> warnings = new ArrayList<>();
        if (legacyPoints == null) {
            return MigrationResult.failure("legacyPoints == null");
        }
        if (snapshot == null) {
            return MigrationResult.failure("snapshot == null");
        }
        if (kind == null) {
            return MigrationResult.failure("kind == null");
        }
        if (!isFinite(snapshot.getDensity()) || snapshot.getDensity() <= 0f) {
            return MigrationResult.failure("snapshot density must be positive and finite");
        }
        if (snapshot.getWidthPx() <= 0 || snapshot.getHeightPx() <= 0) {
            return MigrationResult.failure("snapshot bounds must be non-empty");
        }
        if (snapshot.getRegions().isEmpty()) {
            return MigrationResult.failure("snapshot has no display regions");
        }
        if (legacyPoints.size() > ScreenProfile.MAX_POINTS) {
            return MigrationResult.failure(
                    "legacy points exceed maximum of " + ScreenProfile.MAX_POINTS);
        }

        float globalDiameterDp = pixelsToDp(legacyGlobalPx, snapshot.getDensity());
        ArrayList<ProfilePoint> migratedPoints = new ArrayList<>(legacyPoints.size());
        ArrayList<DisplayRegion> referenceRegions = new ArrayList<>(snapshot.getRegions());
        DisplayRegion syntheticFullRegion = null;
        Set<Integer> pointIds = new LinkedHashSet<>();
        for (int index = 0; index < legacyPoints.size(); index++) {
            LegacyPointRecord legacy = legacyPoints.get(index);
            if (legacy == null) {
                return MigrationResult.failure("legacyPoints[" + index + "] == null");
            }
            if (!pointIds.add(legacy.getId())) {
                return MigrationResult.failure("duplicate legacy point id: " + legacy.getId());
            }

            MigratedPosition position = resolvePosition(legacy, snapshot);
            if (position == null) {
                return MigrationResult.failure(
                        "legacy point " + legacy.getId() + " has no finite position");
            }
            DisplayRegion region = isInsideUnsafeArea(
                    snapshot.getUnsafeAreas(), position.x, position.y)
                    ? null
                    : findContainingRegion(snapshot.getRegions(), position.x, position.y);
            boolean usesSyntheticRegion = region == null;
            if (usesSyntheticRegion) {
                if (syntheticFullRegion == null) {
                    syntheticFullRegion = new DisplayRegion(
                            LEGACY_FULL_REGION_ID, snapshot.getBounds());
                    referenceRegions.add(syntheticFullRegion);
                }
                region = syntheticFullRegion;
            }
            IntRect regionBounds = region.getBounds();
            if (regionBounds.width() <= 0 || regionBounds.height() <= 0) {
                return MigrationResult.failure("display region is empty: " + region.getId());
            }

            float rawU = (position.x - regionBounds.getLeft()) / regionBounds.width();
            float rawV = (position.y - regionBounds.getTop()) / regionBounds.height();
            boolean constrainedForSchema = !isUnit(rawU) || !isUnit(rawV);
            float u = clampUnit(rawU);
            float v = clampUnit(rawV);
            float overrideDp = legacy.getSizeOverridePx() <= 0
                    ? 0f
                    : pixelsToDp(legacy.getSizeOverridePx(), snapshot.getDensity());
            ProfilePoint point = new ProfilePoint(
                    legacy.getId(),
                    region.getId(),
                    u,
                    v,
                    overrideDp,
                    true,
                    PointDisabledReason.NONE,
                    legacy.getTimestamp(),
                    legacy.getDurationMs(),
                    snapshot.getGeneration()
            );

            PointDisabledReason disabledReason = PointDisabledReason.NONE;
            if (!position.hasReliableBaseGeometry || !legacy.isEnabled()) {
                disabledReason = PointDisabledReason.NEEDS_REVIEW;
            } else {
                float diameterDp = overrideDp > 0f ? overrideDp : globalDiameterDp;
                float diameterPx = diameterDp * snapshot.getDensity();
                if (usesSyntheticRegion) {
                    disabledReason = validateUnassignedPosition(
                            snapshot, position.x, position.y, diameterPx);
                } else {
                    PointValidation validation = PointValidator.validate(
                            snapshot,
                            point,
                            new ResolvedPoint(position.x, position.y, diameterPx)
                    );
                    if (!validation.isValid()) {
                        disabledReason = validation.getReason();
                    }
                }
            }
            if (disabledReason != PointDisabledReason.NONE) {
                point = point.disabled(disabledReason);
                if (constrainedForSchema) {
                    warnings.add("Point " + legacy.getId()
                            + " stored at schema boundary and disabled: " + disabledReason);
                } else {
                    warnings.add("Point " + legacy.getId()
                            + " disabled: " + disabledReason);
                }
            }
            migratedPoints.add(point);
        }

        String profileId = "legacy-" + kind.name().toLowerCase(Locale.US);
        ScreenProfile profile = new ScreenProfile(
                profileId,
                kind,
                globalDiameterDp,
                migratedPoints,
                Collections.singletonList(snapshot.getStableKey()),
                referenceRegions,
                snapshot.getUnsafeAreas()
        );
        LinkedHashMap<String, ScreenProfile> profiles = new LinkedHashMap<>();
        profiles.put(profileId, profile);
        ProfileDocument document = new ProfileDocument(
                SCHEMA_VERSION,
                profiles,
                Collections.<String, String>emptyMap()
        );
        return MigrationResult.success(document, warnings);
    }

    private static MigratedPosition resolvePosition(
            LegacyPointRecord point,
            DisplaySnapshot snapshot
    ) {
        int baseRotation = normalizedRotation(point.getBaseRotation());
        boolean validBaseGeometry = baseRotation >= 0
                && point.getBaseWidthPx() > 0
                && point.getBaseHeightPx() > 0
                && isFinite(point.getX())
                && isFinite(point.getY());
        if (validBaseGeometry) {
            int baseNaturalWidth = isQuarterTurn(baseRotation)
                    ? point.getBaseHeightPx() : point.getBaseWidthPx();
            int baseNaturalHeight = isQuarterTurn(baseRotation)
                    ? point.getBaseWidthPx() : point.getBaseHeightPx();
            float[] baseNatural = unrotateToNatural(
                    point.getX(), point.getY(), baseRotation,
                    baseNaturalWidth, baseNaturalHeight);

            int currentRotation = normalizedRotation(snapshot.getRotation());
            if (currentRotation < 0) {
                return null;
            }
            int currentNaturalWidth = isQuarterTurn(currentRotation)
                    ? snapshot.getHeightPx() : snapshot.getWidthPx();
            int currentNaturalHeight = isQuarterTurn(currentRotation)
                    ? snapshot.getWidthPx() : snapshot.getHeightPx();
            float naturalX = baseNatural[0]
                    * currentNaturalWidth / (float) baseNaturalWidth;
            float naturalY = baseNatural[1]
                    * currentNaturalHeight / (float) baseNaturalHeight;
            float[] rotated = rotateFromNatural(
                    naturalX, naturalY, currentRotation,
                    currentNaturalWidth, currentNaturalHeight);
            return new MigratedPosition(
                    snapshot.getBounds().getLeft() + rotated[0],
                    snapshot.getBounds().getTop() + rotated[1],
                    true
            );
        }

        float x;
        float y;
        if (isUnit(point.getNormalizedX()) && isUnit(point.getNormalizedY())) {
            x = snapshot.getBounds().getLeft()
                    + point.getNormalizedX() * snapshot.getWidthPx();
            y = snapshot.getBounds().getTop()
                    + point.getNormalizedY() * snapshot.getHeightPx();
        } else if (isFinite(point.getX()) && isFinite(point.getY())) {
            x = snapshot.getBounds().getLeft() + point.getX();
            y = snapshot.getBounds().getTop() + point.getY();
        } else {
            return null;
        }
        return new MigratedPosition(x, y, false);
    }

    private static DisplayRegion findContainingRegion(
            List<DisplayRegion> regions,
            float x,
            float y
    ) {
        for (DisplayRegion region : regions) {
            IntRect bounds = region.getBounds();
            if (x >= bounds.getLeft() && x < bounds.getRight()
                    && y >= bounds.getTop() && y < bounds.getBottom()) {
                return region;
            }
        }
        return null;
    }

    private static boolean isInsideUnsafeArea(
            List<UnsafeArea> unsafeAreas,
            float x,
            float y
    ) {
        for (UnsafeArea unsafeArea : unsafeAreas) {
            IntRect bounds = unsafeArea.getBounds();
            if (x >= bounds.getLeft() && x < bounds.getRight()
                    && y >= bounds.getTop() && y < bounds.getBottom()) {
                return true;
            }
        }
        return false;
    }

    private static PointDisabledReason validateUnassignedPosition(
            DisplaySnapshot snapshot,
            float centerX,
            float centerY,
            float diameterPx
    ) {
        IntRect displayBounds = snapshot.getBounds();
        float radius = diameterPx / 2f;
        float left = centerX - radius;
        float top = centerY - radius;
        float right = centerX + radius;
        float bottom = centerY + radius;
        if (centerX < displayBounds.getLeft()
                || centerX >= displayBounds.getRight()
                || centerY < displayBounds.getTop()
                || centerY >= displayBounds.getBottom()
                || left < displayBounds.getLeft()
                || top < displayBounds.getTop()
                || right > displayBounds.getRight()
                || bottom > displayBounds.getBottom()) {
            return PointDisabledReason.OUT_OF_BOUNDS;
        }
        return PointDisabledReason.NEEDS_REVIEW;
    }

    private static float pixelsToDp(int pixels, float density) {
        return Math.max(MIN_DIAMETER_DP, Math.min(MAX_DIAMETER_DP, pixels / density));
    }

    private static float clampUnit(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static boolean isUnit(float value) {
        return isFinite(value) && value >= 0f && value <= 1f;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static int normalizedRotation(int rotation) {
        if (rotation >= 0 && rotation <= 3) {
            return rotation;
        }
        if (rotation == 90) {
            return 1;
        }
        if (rotation == 180) {
            return 2;
        }
        if (rotation == 270) {
            return 3;
        }
        return -1;
    }

    private static boolean isQuarterTurn(int rotation) {
        return rotation == 1 || rotation == 3;
    }

    private static float[] rotateFromNatural(
            float x,
            float y,
            int rotation,
            int naturalWidth,
            int naturalHeight
    ) {
        if (rotation == 1) {
            return new float[]{y, naturalWidth - x};
        }
        if (rotation == 2) {
            return new float[]{naturalWidth - x, naturalHeight - y};
        }
        if (rotation == 3) {
            return new float[]{naturalHeight - y, x};
        }
        return new float[]{x, y};
    }

    private static float[] unrotateToNatural(
            float x,
            float y,
            int rotation,
            int naturalWidth,
            int naturalHeight
    ) {
        if (rotation == 1) {
            return new float[]{naturalWidth - y, x};
        }
        if (rotation == 2) {
            return new float[]{naturalWidth - x, naturalHeight - y};
        }
        if (rotation == 3) {
            return new float[]{y, naturalHeight - x};
        }
        return new float[]{x, y};
    }

    private static final class MigratedPosition {
        private final float x;
        private final float y;
        private final boolean hasReliableBaseGeometry;

        private MigratedPosition(float x, float y, boolean hasReliableBaseGeometry) {
            this.x = x;
            this.y = y;
            this.hasReliableBaseGeometry = hasReliableBaseGeometry;
        }
    }

    public static final class MigrationResult {
        private final ProfileDocument document;
        private final List<String> warnings;

        private MigrationResult(ProfileDocument document, List<String> warnings) {
            this.document = document;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        private static MigrationResult success(
                ProfileDocument document,
                List<String> warnings
        ) {
            return new MigrationResult(document, warnings);
        }

        private static MigrationResult failure(String warning) {
            return new MigrationResult(null, Collections.singletonList(warning));
        }

        public boolean isSuccess() {
            return document != null;
        }

        public ProfileDocument getDocument() {
            return document;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
