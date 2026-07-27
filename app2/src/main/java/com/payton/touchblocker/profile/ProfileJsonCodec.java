package com.payton.touchblocker.profile;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileJsonCodec {
    private static final int SUPPORTED_SCHEMA_VERSION = 2;
    private static final int MAX_POINTS_PER_PROFILE = 64;
    private static final float MIN_GLOBAL_DIAMETER_DP = 24f;
    private static final float MAX_GLOBAL_DIAMETER_DP = 200f;
    private static final float MIN_POINT_DIAMETER_OVERRIDE_DP = 24f;
    private static final float MAX_POINT_DIAMETER_OVERRIDE_DP = 200f;

    public String encode(ProfileDocument document) throws JSONException {
        if (document == null) {
            throw error("document == null");
        }
        if (document.getSchemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw error("Unsupported schemaVersion: " + document.getSchemaVersion());
        }

        StringBuilder output = new StringBuilder();
        output.append('{')
                .append("\"schemaVersion\":")
                .append(SUPPORTED_SCHEMA_VERSION)
                .append(",\"profiles\":[");

        Set<String> profileIds = new LinkedHashSet<>();
        int profileIndex = 0;
        for (Map.Entry<String, ScreenProfile> entry : document.getProfiles().entrySet()) {
            ScreenProfile profile = entry.getValue();
            if (profile == null) {
                throw error("$.profiles contains null");
            }
            String path = "$.profiles[" + profileIndex + "]";
            String profileId = requireNonEmptyForEncode(profile.getId(), path + ".id");
            if (!entry.getKey().equals(profileId)) {
                throw error(path + ".id does not match its document map key");
            }
            if (!profileIds.add(profileId)) {
                throw error("Duplicate profile id: " + profileId);
            }
            if (profileIndex > 0) {
                output.append(',');
            }
            appendProfile(output, profile, path);
            profileIndex++;
        }

        output.append("],\"manualBindings\":{");
        int bindingIndex = 0;
        for (Map.Entry<String, String> entry : document.getManualBindings().entrySet()) {
            String targetProfileId = entry.getValue();
            if (!profileIds.contains(targetProfileId)) {
                throw error("Manual binding targets unknown profile: " + targetProfileId);
            }
            if (bindingIndex > 0) {
                output.append(',');
            }
            output.append(JSONObject.quote(entry.getKey()))
                    .append(':')
                    .append(JSONObject.quote(targetProfileId));
            bindingIndex++;
        }
        return output.append("}}").toString();
    }

    public DecodeResult decode(String raw) {
        if (raw == null) {
            return DecodeResult.failure("raw == null");
        }

        try {
            Object rootValue = StrictJsonParser.parse(raw);
            if (!(rootValue instanceof JSONObject)) {
                throw error("Root must be an object");
            }

            JSONObject root = (JSONObject) rootValue;
            int schemaVersion = requireInt(root, "schemaVersion", "$.schemaVersion");
            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw error("Unsupported schemaVersion: " + schemaVersion);
            }

            JSONArray encodedProfiles = requireArray(root, "profiles", "$.profiles");
            LinkedHashMap<String, ScreenProfile> profiles = new LinkedHashMap<>();
            for (int index = 0; index < encodedProfiles.length(); index++) {
                String path = "$.profiles[" + index + "]";
                JSONObject encodedProfile = requireObject(encodedProfiles, index, path);
                ScreenProfile profile = decodeProfile(encodedProfile, path);
                if (profiles.containsKey(profile.getId())) {
                    throw error("Duplicate profile id: " + profile.getId());
                }
                profiles.put(profile.getId(), profile);
            }

            JSONObject encodedBindings = requireObject(
                    root, "manualBindings", "$.manualBindings");
            LinkedHashMap<String, String> manualBindings = new LinkedHashMap<>();
            Iterator<String> bindingKeys = encodedBindings.keys();
            while (bindingKeys.hasNext()) {
                String stableKey = bindingKeys.next();
                String path = "$.manualBindings[" + JSONObject.quote(stableKey) + "]";
                String profileId = requireString(encodedBindings, stableKey, path);
                if (!profiles.containsKey(profileId)) {
                    throw error(path + " targets unknown profile: " + profileId);
                }
                manualBindings.put(stableKey, profileId);
            }

            ProfileDocument document = new ProfileDocument(
                    schemaVersion,
                    profiles,
                    manualBindings
            );
            return DecodeResult.success(document);
        } catch (Exception failure) {
            String message = failure.getMessage();
            if (message == null || message.isEmpty()) {
                message = failure.getClass().getSimpleName();
            }
            return DecodeResult.failure(message);
        }
    }

    private static void appendProfile(
            StringBuilder output,
            ScreenProfile profile,
            String path
    ) throws JSONException {
        if (profile.getKind() == null) {
            throw error(path + ".kind == null");
        }
        float globalDiameterDp = requireFiniteForEncode(
                profile.getGlobalDiameterDp(), path + ".globalDiameterDp");
        if (globalDiameterDp < MIN_GLOBAL_DIAMETER_DP
                || globalDiameterDp > MAX_GLOBAL_DIAMETER_DP) {
            throw error(path + ".globalDiameterDp must be in [24, 200]");
        }
        if (profile.getPoints().size() > MAX_POINTS_PER_PROFILE) {
            throw error(path + ".points must contain at most "
                    + MAX_POINTS_PER_PROFILE + " entries");
        }

        output.append('{')
                .append("\"id\":").append(JSONObject.quote(profile.getId()))
                .append(",\"kind\":").append(JSONObject.quote(profile.getKind().name()))
                .append(",\"globalDiameterDp\":").append(Float.toString(globalDiameterDp))
                .append(",\"fingerprints\":[");
        appendStrings(output, profile.getFingerprints(), path + ".fingerprints");

        output.append("],\"referenceRegions\":[");
        Set<String> referenceRegionIds = new LinkedHashSet<>();
        for (int index = 0; index < profile.getReferenceRegions().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            DisplayRegion region = profile.getReferenceRegions().get(index);
            String regionPath = path + ".referenceRegions[" + index + "]";
            if (region == null) {
                throw error(regionPath + " == null");
            }
            String regionId = requireNonEmptyForEncode(region.getId(), regionPath + ".id");
            if (!referenceRegionIds.add(regionId)) {
                throw error("Duplicate reference region id in profile "
                        + profile.getId() + ": " + regionId);
            }
            appendRegion(
                    output,
                    region,
                    regionPath
            );
        }

        output.append("],\"referenceUnsafeAreas\":[");
        for (int index = 0; index < profile.getReferenceUnsafeAreas().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            appendUnsafeArea(
                    output,
                    profile.getReferenceUnsafeAreas().get(index),
                    path + ".referenceUnsafeAreas[" + index + "]"
            );
        }

        output.append("],\"points\":[");
        Set<Integer> pointIds = new LinkedHashSet<>();
        for (int index = 0; index < profile.getPoints().size(); index++) {
            if (index > 0) {
                output.append(',');
            }
            ProfilePoint point = profile.getPoints().get(index);
            String pointPath = path + ".points[" + index + "]";
            if (point == null) {
                throw error(pointPath + " == null");
            }
            if (!pointIds.add(point.getId())) {
                throw error("Duplicate point id in profile " + profile.getId()
                        + ": " + point.getId());
            }
            String pointRegionId = requireNonEmptyForEncode(
                    point.getRegionId(), pointPath + ".regionId");
            if (!referenceRegionIds.isEmpty()
                    && !referenceRegionIds.contains(pointRegionId)) {
                throw error(pointPath + ".regionId targets unknown reference region: "
                        + pointRegionId);
            }
            appendPoint(output, point, pointPath);
        }
        output.append("]}");
    }

    private static void appendStrings(
            StringBuilder output,
            List<String> values,
            String path
    ) throws JSONException {
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value == null) {
                throw error(path + "[" + index + "] == null");
            }
            if (index > 0) {
                output.append(',');
            }
            output.append(JSONObject.quote(value));
        }
    }

    private static void appendRegion(
            StringBuilder output,
            DisplayRegion region,
            String path
    ) throws JSONException {
        if (region == null) {
            throw error(path + " == null");
        }
        String id = requireNonEmptyForEncode(region.getId(), path + ".id");
        output.append("{\"id\":")
                .append(JSONObject.quote(id))
                .append(",\"bounds\":");
        appendBounds(output, region.getBounds(), path + ".bounds");
        output.append('}');
    }

    private static void appendUnsafeArea(
            StringBuilder output,
            UnsafeArea unsafeArea,
            String path
    ) throws JSONException {
        if (unsafeArea == null) {
            throw error(path + " == null");
        }
        if (unsafeArea.getReason() == null) {
            throw error(path + ".reason == null");
        }
        output.append("{\"bounds\":");
        appendBounds(output, unsafeArea.getBounds(), path + ".bounds");
        output.append(",\"reason\":")
                .append(JSONObject.quote(unsafeArea.getReason().name()))
                .append('}');
    }

    private static void appendBounds(
            StringBuilder output,
            IntRect bounds,
            String path
    ) throws JSONException {
        if (bounds == null) {
            throw error(path + " == null");
        }
        output.append("{\"left\":").append(bounds.getLeft())
                .append(",\"top\":").append(bounds.getTop())
                .append(",\"right\":").append(bounds.getRight())
                .append(",\"bottom\":").append(bounds.getBottom())
                .append('}');
    }

    private static void appendPoint(
            StringBuilder output,
            ProfilePoint point,
            String path
    ) throws JSONException {
        String regionId = requireNonEmptyForEncode(point.getRegionId(), path + ".regionId");
        float u = requireUnitForEncode(point.getU(), path + ".u");
        float v = requireUnitForEncode(point.getV(), path + ".v");
        float diameterDpOverride = requireDiameterOverrideForEncode(
                point.getDiameterDpOverride(), path + ".diameterDpOverride");
        if (point.getDisabledReason() == null) {
            throw error(path + ".disabledReason == null");
        }

        output.append("{\"id\":").append(point.getId())
                .append(",\"regionId\":").append(JSONObject.quote(regionId))
                .append(",\"u\":").append(Float.toString(u))
                .append(",\"v\":").append(Float.toString(v))
                .append(",\"diameterDpOverride\":")
                .append(Float.toString(diameterDpOverride))
                .append(",\"enabled\":").append(point.isEnabled())
                .append(",\"disabledReason\":")
                .append(JSONObject.quote(point.getDisabledReason().name()))
                .append(",\"timestamp\":").append(point.getTimestamp())
                .append(",\"durationMs\":").append(point.getDurationMs())
                .append(",\"displayGeneration\":").append(point.getDisplayGeneration())
                .append('}');
    }

    private static ScreenProfile decodeProfile(JSONObject encoded, String path)
            throws JSONException {
        String id = requireNonEmptyString(encoded, "id", path + ".id");
        ProfileKind kind = requireEnum(
                encoded, "kind", path + ".kind", ProfileKind.class);
        float globalDiameterDp = requireRangedFloat(
                encoded,
                "globalDiameterDp",
                path + ".globalDiameterDp",
                MIN_GLOBAL_DIAMETER_DP,
                MAX_GLOBAL_DIAMETER_DP
        );

        JSONArray encodedFingerprints = requireArray(
                encoded, "fingerprints", path + ".fingerprints");
        ArrayList<String> fingerprints = new ArrayList<>(encodedFingerprints.length());
        for (int index = 0; index < encodedFingerprints.length(); index++) {
            fingerprints.add(requireString(
                    encodedFingerprints,
                    index,
                    path + ".fingerprints[" + index + "]"
            ));
        }

        JSONArray encodedRegions = requireArray(
                encoded, "referenceRegions", path + ".referenceRegions");
        ArrayList<DisplayRegion> regions = new ArrayList<>(encodedRegions.length());
        Set<String> referenceRegionIds = new LinkedHashSet<>();
        for (int index = 0; index < encodedRegions.length(); index++) {
            String regionPath = path + ".referenceRegions[" + index + "]";
            DisplayRegion region = decodeRegion(
                    requireObject(encodedRegions, index, regionPath),
                    regionPath
            );
            if (!referenceRegionIds.add(region.getId())) {
                throw error("Duplicate reference region id in profile "
                        + id + ": " + region.getId());
            }
            regions.add(region);
        }

        JSONArray encodedUnsafeAreas = requireArray(
                encoded, "referenceUnsafeAreas", path + ".referenceUnsafeAreas");
        ArrayList<UnsafeArea> unsafeAreas = new ArrayList<>(encodedUnsafeAreas.length());
        for (int index = 0; index < encodedUnsafeAreas.length(); index++) {
            String areaPath = path + ".referenceUnsafeAreas[" + index + "]";
            unsafeAreas.add(decodeUnsafeArea(
                    requireObject(encodedUnsafeAreas, index, areaPath),
                    areaPath
            ));
        }

        JSONArray encodedPoints = requireArray(encoded, "points", path + ".points");
        if (encodedPoints.length() > MAX_POINTS_PER_PROFILE) {
            throw error(path + ".points must contain at most "
                    + MAX_POINTS_PER_PROFILE + " entries");
        }
        ArrayList<ProfilePoint> points = new ArrayList<>(encodedPoints.length());
        Set<Integer> pointIds = new LinkedHashSet<>();
        for (int index = 0; index < encodedPoints.length(); index++) {
            String pointPath = path + ".points[" + index + "]";
            ProfilePoint point = decodePoint(
                    requireObject(encodedPoints, index, pointPath),
                    pointPath
            );
            if (!pointIds.add(point.getId())) {
                throw error("Duplicate point id in profile " + id + ": " + point.getId());
            }
            if (!referenceRegionIds.isEmpty()
                    && !referenceRegionIds.contains(point.getRegionId())) {
                throw error(pointPath + ".regionId targets unknown reference region: "
                        + point.getRegionId());
            }
            points.add(point);
        }

        return new ScreenProfile(
                id,
                kind,
                globalDiameterDp,
                points,
                fingerprints,
                regions,
                unsafeAreas
        );
    }

    private static DisplayRegion decodeRegion(JSONObject encoded, String path)
            throws JSONException {
        String id = requireNonEmptyString(encoded, "id", path + ".id");
        JSONObject bounds = requireObject(encoded, "bounds", path + ".bounds");
        return new DisplayRegion(id, decodeBounds(bounds, path + ".bounds"));
    }

    private static UnsafeArea decodeUnsafeArea(JSONObject encoded, String path)
            throws JSONException {
        JSONObject bounds = requireObject(encoded, "bounds", path + ".bounds");
        PointDisabledReason reason = requireEnum(
                encoded,
                "reason",
                path + ".reason",
                PointDisabledReason.class
        );
        return new UnsafeArea(decodeBounds(bounds, path + ".bounds"), reason);
    }

    private static IntRect decodeBounds(JSONObject encoded, String path) throws JSONException {
        return new IntRect(
                requireInt(encoded, "left", path + ".left"),
                requireInt(encoded, "top", path + ".top"),
                requireInt(encoded, "right", path + ".right"),
                requireInt(encoded, "bottom", path + ".bottom")
        );
    }

    private static ProfilePoint decodePoint(JSONObject encoded, String path)
            throws JSONException {
        return new ProfilePoint(
                requireInt(encoded, "id", path + ".id"),
                requireNonEmptyString(encoded, "regionId", path + ".regionId"),
                requireUnitFloat(encoded, "u", path + ".u"),
                requireUnitFloat(encoded, "v", path + ".v"),
                requireDiameterOverride(
                        encoded, "diameterDpOverride", path + ".diameterDpOverride"),
                requireBoolean(encoded, "enabled", path + ".enabled"),
                requireEnum(
                        encoded,
                        "disabledReason",
                        path + ".disabledReason",
                        PointDisabledReason.class
                ),
                requireLong(encoded, "timestamp", path + ".timestamp"),
                requireLong(encoded, "durationMs", path + ".durationMs"),
                requireLong(encoded, "displayGeneration", path + ".displayGeneration")
        );
    }

    private static Object requireValue(JSONObject object, String name, String path)
            throws JSONException {
        if (!object.has(name)) {
            throw error("Missing required field: " + path);
        }
        Object value = object.get(name);
        if (value == JSONObject.NULL) {
            throw error(path + " must not be null");
        }
        return value;
    }

    private static JSONObject requireObject(JSONObject object, String name, String path)
            throws JSONException {
        Object value = requireValue(object, name, path);
        if (!(value instanceof JSONObject)) {
            throw error(path + " must be an object");
        }
        return (JSONObject) value;
    }

    private static JSONObject requireObject(JSONArray array, int index, String path)
            throws JSONException {
        Object value = array.get(index);
        if (!(value instanceof JSONObject)) {
            throw error(path + " must be an object");
        }
        return (JSONObject) value;
    }

    private static JSONArray requireArray(JSONObject object, String name, String path)
            throws JSONException {
        Object value = requireValue(object, name, path);
        if (!(value instanceof JSONArray)) {
            throw error(path + " must be an array");
        }
        return (JSONArray) value;
    }

    private static String requireString(JSONObject object, String name, String path)
            throws JSONException {
        return requireStringValue(requireValue(object, name, path), path);
    }

    private static String requireString(JSONArray array, int index, String path)
            throws JSONException {
        return requireStringValue(array.get(index), path);
    }

    private static String requireStringValue(Object value, String path) throws JSONException {
        if (!(value instanceof String)) {
            throw error(path + " must be a string");
        }
        return (String) value;
    }

    private static String requireNonEmptyString(
            JSONObject object,
            String name,
            String path
    ) throws JSONException {
        String value = requireString(object, name, path);
        if (value.trim().isEmpty()) {
            throw error(path + " must not be empty");
        }
        return value;
    }

    private static boolean requireBoolean(JSONObject object, String name, String path)
            throws JSONException {
        Object value = requireValue(object, name, path);
        if (!(value instanceof Boolean)) {
            throw error(path + " must be a boolean");
        }
        return (Boolean) value;
    }

    private static int requireInt(JSONObject object, String name, String path)
            throws JSONException {
        long value = requireIntegralNumber(object, name, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw error(path + " is outside the integer range");
        }
        return (int) value;
    }

    private static long requireLong(JSONObject object, String name, String path)
            throws JSONException {
        return requireIntegralNumber(object, name, path);
    }

    private static long requireIntegralNumber(JSONObject object, String name, String path)
            throws JSONException {
        try {
            return requireExactNumber(object, name, path).longValueExact();
        } catch (ArithmeticException invalid) {
            throw error(path + " must be an exact integer");
        }
    }

    private static float requireFiniteFloat(JSONObject object, String name, String path)
            throws JSONException {
        return toFiniteFloat(requireExactNumber(object, name, path), path);
    }

    private static BigDecimal requireExactNumber(
            JSONObject object,
            String name,
            String path
    )
            throws JSONException {
        Object value = requireValue(object, name, path);
        if (!(value instanceof Number)) {
            throw error(path + " must be a JSON number");
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException invalid) {
            throw error(path + " must be a finite JSON number");
        }
    }

    private static float toFiniteFloat(BigDecimal exactValue, String path)
            throws JSONException {
        float floatValue = exactValue.floatValue();
        if (Float.isNaN(floatValue) || Float.isInfinite(floatValue)) {
            throw error(path + " must fit in a finite float");
        }
        if (exactValue.signum() != 0 && floatValue == 0f) {
            throw error(path + " is too small to preserve as a float");
        }
        return floatValue;
    }

    private static float requireUnitFloat(JSONObject object, String name, String path)
            throws JSONException {
        return requireRangedFloat(object, name, path, 0d, 1d);
    }

    private static float requireRangedFloat(
            JSONObject object,
            String name,
            String path,
            double minimum,
            double maximum
    ) throws JSONException {
        BigDecimal value = requireExactNumber(object, name, path);
        if (value.compareTo(BigDecimal.valueOf(minimum)) < 0
                || value.compareTo(BigDecimal.valueOf(maximum)) > 0) {
            throw error(path + " must be in [" + minimum + ", " + maximum + "]");
        }
        return toFiniteFloat(value, path);
    }

    private static float requireDiameterOverride(
            JSONObject object,
            String name,
            String path
    ) throws JSONException {
        BigDecimal value = requireExactNumber(object, name, path);
        if (value.signum() != 0
                && (value.compareTo(BigDecimal.valueOf(
                        MIN_POINT_DIAMETER_OVERRIDE_DP)) < 0
                || value.compareTo(BigDecimal.valueOf(
                        MAX_POINT_DIAMETER_OVERRIDE_DP)) > 0)) {
            throw error(path + " must be 0 or in [24, 200]");
        }
        return toFiniteFloat(value, path);
    }

    private static <T extends Enum<T>> T requireEnum(
            JSONObject object,
            String name,
            String path,
            Class<T> enumClass
    ) throws JSONException {
        String value = requireString(object, name, path);
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException invalid) {
            throw error(path + " has unknown enum value: " + value);
        }
    }

    private static String requireNonEmptyForEncode(String value, String path)
            throws JSONException {
        if (value == null || value.trim().isEmpty()) {
            throw error(path + " must not be empty");
        }
        return value;
    }

    private static float requireFiniteForEncode(float value, String path)
            throws JSONException {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw error(path + " must be finite");
        }
        return value;
    }

    private static float requireDiameterOverrideForEncode(float value, String path)
            throws JSONException {
        requireFiniteForEncode(value, path);
        if (value != 0f && (value < MIN_POINT_DIAMETER_OVERRIDE_DP
                || value > MAX_POINT_DIAMETER_OVERRIDE_DP)) {
            throw error(path + " must be 0 or in [24, 200]");
        }
        return value;
    }

    private static float requireUnitForEncode(float value, String path)
            throws JSONException {
        requireFiniteForEncode(value, path);
        if (value < 0f || value > 1f) {
            throw error(path + " must be in [0, 1]");
        }
        return value;
    }

    private static JSONException error(String message) {
        return new JSONException(message);
    }

    public static final class DecodeResult {
        private final ProfileDocument document;
        private final String fatalError;
        private final List<String> warnings;

        private DecodeResult(
                ProfileDocument document,
                String fatalError,
                List<String> warnings
        ) {
            this.document = document;
            this.fatalError = fatalError;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        private static DecodeResult success(ProfileDocument document) {
            return new DecodeResult(document, null, Collections.<String>emptyList());
        }

        private static DecodeResult failure(String fatalError) {
            return new DecodeResult(null, fatalError, Collections.<String>emptyList());
        }

        public boolean isSuccess() {
            return document != null && fatalError == null;
        }

        public ProfileDocument getDocument() {
            return document;
        }

        public String getFatalError() {
            return fatalError;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
