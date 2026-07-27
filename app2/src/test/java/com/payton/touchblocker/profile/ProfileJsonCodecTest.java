package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfileJsonCodecTest {
    private static final String VALID_POINT =
            "{\"id\":1,\"regionId\":\"full\",\"u\":0.5,\"v\":0.25,"
            + "\"diameterDpOverride\":36.5,\"enabled\":false,"
            + "\"disabledReason\":\"HINGE\",\"timestamp\":1001,"
            + "\"durationMs\":202,\"displayGeneration\":3}";

    private static final String VALID_REGION =
            "{\"id\":\"full\",\"bounds\":{\"left\":0,\"top\":1,"
            + "\"right\":1080,\"bottom\":2200}}";

    private static final String VALID_PROFILE =
            "{\"id\":\"inner\",\"kind\":\"INNER\",\"globalDiameterDp\":40.5,"
            + "\"fingerprints\":[\"display-A\",\"display-B\"],"
            + "\"referenceRegions\":[" + VALID_REGION + "],"
            + "\"referenceUnsafeAreas\":[{\"bounds\":{\"left\":500,\"top\":1,"
            + "\"right\":580,\"bottom\":2200},\"reason\":\"HINGE\"}],"
            + "\"points\":[" + VALID_POINT + "]}";

    private static final String VALID = documentJson(VALID_PROFILE);

    @Test
    public void roundTripsCompleteDocument() throws Exception {
        ProfileJsonCodec codec = new ProfileJsonCodec();

        ProfileJsonCodec.DecodeResult first = codec.decode(VALID);
        assertTrue(first.getFatalError(), first.isSuccess());
        assertNull(first.getFatalError());
        assertTrue(first.getWarnings().isEmpty());

        ProfileJsonCodec.DecodeResult second = codec.decode(codec.encode(first.getDocument()));

        assertTrue(second.getFatalError(), second.isSuccess());
        ProfileDocument document = second.getDocument();
        assertEquals(2, document.getSchemaVersion());
        assertEquals(Collections.singletonMap("display-A", "inner"),
                document.getManualBindings());

        ScreenProfile profile = document.getProfiles().get("inner");
        assertNotNull(profile);
        assertEquals(ProfileKind.INNER, profile.getKind());
        assertEquals(40.5f, profile.getGlobalDiameterDp(), 0f);
        assertEquals(Arrays.asList("display-A", "display-B"), profile.getFingerprints());

        assertEquals(1, profile.getPoints().size());
        ProfilePoint point = profile.getPoints().get(0);
        assertEquals(1, point.getId());
        assertEquals("full", point.getRegionId());
        assertEquals(0.5f, point.getU(), 0f);
        assertEquals(0.25f, point.getV(), 0f);
        assertEquals(36.5f, point.getDiameterDpOverride(), 0f);
        assertFalse(point.isEnabled());
        assertEquals(PointDisabledReason.HINGE, point.getDisabledReason());
        assertEquals(1001L, point.getTimestamp());
        assertEquals(202L, point.getDurationMs());
        assertEquals(3L, point.getDisplayGeneration());

        assertEquals(Collections.singletonList(new DisplayRegion(
                "full", new IntRect(0, 1, 1080, 2200))), profile.getReferenceRegions());
        assertEquals(Collections.singletonList(new UnsafeArea(
                new IntRect(500, 1, 580, 2200), PointDisabledReason.HINGE)),
                profile.getReferenceUnsafeAreas());
    }

    @Test
    public void encodeUsesCanonicalFieldsAndDocumentIterationOrder() throws Exception {
        ScreenProfile outer = emptyProfile("outer", ProfileKind.OUTER, 44f);
        ScreenProfile inner = emptyProfile("inner", ProfileKind.INNER, 40f);
        Map<String, ScreenProfile> profiles = new LinkedHashMap<>();
        profiles.put("outer", outer);
        profiles.put("inner", inner);
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("display-Z", "outer");
        bindings.put("display-A", "inner");
        ProfileDocument document = new ProfileDocument(2, profiles, bindings);
        String expected = "{\"schemaVersion\":2,\"profiles\":["
                + "{\"id\":\"outer\",\"kind\":\"OUTER\",\"globalDiameterDp\":44.0,"
                + "\"fingerprints\":[],\"referenceRegions\":[],"
                + "\"referenceUnsafeAreas\":[],\"points\":[]},"
                + "{\"id\":\"inner\",\"kind\":\"INNER\",\"globalDiameterDp\":40.0,"
                + "\"fingerprints\":[],\"referenceRegions\":[],"
                + "\"referenceUnsafeAreas\":[],\"points\":[]}],"
                + "\"manualBindings\":{\"display-Z\":\"outer\","
                + "\"display-A\":\"inner\"}}";

        ProfileJsonCodec codec = new ProfileJsonCodec();

        assertEquals(expected, codec.encode(document));
        assertEquals(expected, codec.encode(document));
    }

    @Test
    public void rejectsNullEmptyInvalidAndTrailingRawInput() {
        assertFailure(null);
        assertFailure("");
        assertFailure("   ");
        assertFailure("not-json");
        assertFailure(VALID + " trailing");
    }

    @Test
    public void rejectsNonStandardJsonSyntax() {
        assertFailure(VALID.replace("\"schemaVersion\"", "'schemaVersion'"));
        assertFailure(VALID.replace("\"schemaVersion\"", "schemaVersion"));
        assertFailure(VALID.replace(
                "\"profiles\":[" + VALID_PROFILE + "]",
                "\"profiles\":[" + VALID_PROFILE + ",]"));
        assertFailure(VALID.substring(0, VALID.length() - 1) + ",}");
        assertFailure("{\"schemaVersion\":2," + VALID.substring(1));
        assertFailure(VALID.replace("\"display-B\"", "\"bad\\xescape\""));
        String nonAsciiUnicodeEscape = "\"bad:\\" + "u" + (char) 0xFF26 + "000\"";
        assertFailure(VALID.replace("\"display-B\"", nonAsciiUnicodeEscape));
        assertFailure(VALID.replace("\"display-B\"", "\"line\nbreak\""));
        assertFailure(VALID.replace("\"timestamp\":1001", "\"timestamp\":01001"));
        assertFailure(VALID.replace("\"u\":0.5", "\"u\":+0.5"));
        assertFailure(VALID.replace("\"u\":0.5", "\"u\":.5"));
        assertFailure(VALID.replace("\"u\":0.5", "\"u\":0.5."));
    }

    @Test
    public void standardJsonEscapesRoundTrip() throws Exception {
        String escaped = VALID.replace(
                "\"display-B\"",
                "\"quote:\\\" backslash:\\\\ slash:\\/ controls:"
                        + "\\b\\f\\n\\r\\t unicode:\\u4F60\\u597D\"");
        String expected = "quote:\" backslash:\\ slash:/ controls:"
                + "\b\f\n\r\t unicode:你好";
        ProfileJsonCodec codec = new ProfileJsonCodec();

        ProfileJsonCodec.DecodeResult first = codec.decode(escaped);
        assertTrue(first.getFatalError(), first.isSuccess());
        assertEquals(expected, first.getDocument().getProfiles().get("inner")
                .getFingerprints().get(1));

        ProfileJsonCodec.DecodeResult second = codec.decode(codec.encode(first.getDocument()));
        assertTrue(second.getFatalError(), second.isSuccess());
        assertEquals(expected, second.getDocument().getProfiles().get("inner")
                .getFingerprints().get(1));
    }

    @Test
    public void rejectsNonObjectRoot() {
        assertFailure("[]");
        assertFailure("\"text\"");
        assertFailure("2");
        assertFailure("null");
    }

    @Test
    public void rejectsUnsupportedOrWrongTypeSchemaVersion() {
        assertFailure(VALID.replace("\"schemaVersion\":2", "\"schemaVersion\":3"));
        assertFailure(VALID.replace("\"schemaVersion\":2", "\"schemaVersion\":\"2\""));
        assertFailure(VALID.replace("\"schemaVersion\":2,", ""));
    }

    @Test
    public void rejectsDuplicateOrEmptyProfileIds() {
        assertFailure(documentJson(VALID_PROFILE, VALID_PROFILE));
        assertFailure(VALID.replace("\"id\":\"inner\"", "\"id\":\"\""));
    }

    @Test
    public void rejectsDuplicatePointIdsWithinProfile() {
        String duplicate = VALID.replace(
                "\"points\":[" + VALID_POINT + "]",
                "\"points\":[" + VALID_POINT + "," + VALID_POINT + "]");

        assertFailure(duplicate);
    }

    @Test
    public void rejectsProfilesWithMoreThan64PointsWithoutTruncating() {
        StringBuilder points = new StringBuilder();
        for (int id = 0; id < 65; id++) {
            if (id > 0) {
                points.append(',');
            }
            points.append(pointJson(id));
        }
        String tooMany = VALID.replace(
                "\"points\":[" + VALID_POINT + "]",
                "\"points\":[" + points + "]");

        assertFailure(tooMany);
    }

    @Test
    public void rejectsMissingOrEmptyPointRegionId() {
        assertFailure(VALID.replace("\"regionId\":\"full\",", ""));
        assertFailure(VALID.replace("\"regionId\":\"full\"", "\"regionId\":\"\""));
    }

    @Test
    public void decodeRejectsDuplicateReferenceRegionIdsAndDanglingPointRegions() {
        assertFailure(VALID.replace(
                "\"referenceRegions\":[" + VALID_REGION + "]",
                "\"referenceRegions\":[" + VALID_REGION + "," + VALID_REGION + "]"));
        assertFailure(VALID.replace(
                "\"regionId\":\"full\"", "\"regionId\":\"missing\""));
    }

    @Test
    public void emptyReferenceRegionsRemainCompatibleWithStoredPointRegionIds()
            throws Exception {
        String withoutReferenceRegions = VALID.replace(
                "\"referenceRegions\":[" + VALID_REGION + "]",
                "\"referenceRegions\":[]");
        ProfileJsonCodec codec = new ProfileJsonCodec();

        ProfileJsonCodec.DecodeResult decoded = codec.decode(withoutReferenceRegions);
        assertTrue(decoded.getFatalError(), decoded.isSuccess());

        ScreenProfile profile = profileWithPointsAndRegions(
                Collections.singletonList(ProfilePoint.enabled(
                        1, "legacy-region", 0.5f, 0.25f, 1L, 2L, 3L)),
                Collections.<DisplayRegion>emptyList()
        );
        ProfileJsonCodec.DecodeResult roundTrip = codec.decode(
                codec.encode(documentWithProfile(profile)));
        assertTrue(roundTrip.getFatalError(), roundTrip.isSuccess());
        assertEquals("legacy-region", roundTrip.getDocument().getProfiles().get("inner")
                .getPoints().get(0).getRegionId());
    }

    @Test
    public void rejectsCoordinatesOutsideUnitIntervalOrEncodedAsStrings() {
        assertFailure(VALID.replace("\"u\":0.5", "\"u\":-0.001"));
        assertFailure(VALID.replace("\"v\":0.25", "\"v\":1.001"));
        assertFailure(VALID.replace("\"v\":0.25", "\"v\":1.00000001"));
        assertFailure(VALID.replace(
                "\"u\":0.5", "\"u\":1.00000000000000001"));
        assertFailure(VALID.replace("\"u\":0.5", "\"u\":\"0.5\""));
        assertFailure(VALID.replace("\"u\":0.5", "\"u\":\"NaN\""));
        assertFailure(VALID.replace("\"v\":0.25", "\"v\":\"Infinity\""));
    }

    @Test
    public void rejectsNonFiniteOrOutOfRangeDiametersBeforeModelClamping() {
        assertFailure(VALID.replace(
                "\"globalDiameterDp\":40.5", "\"globalDiameterDp\":1e400"));
        assertFailure(VALID.replace(
                "\"globalDiameterDp\":40.5", "\"globalDiameterDp\":\"NaN\""));
        assertFailure(VALID.replace(
                "\"globalDiameterDp\":40.5", "\"globalDiameterDp\":10"));
        assertFailure(VALID.replace(
                "\"globalDiameterDp\":40.5", "\"globalDiameterDp\":200.000001"));
        assertFailure(VALID.replace(
                "\"globalDiameterDp\":40.5",
                "\"globalDiameterDp\":200.00000000000001"));
        assertFailure(VALID.replace(
                "\"diameterDpOverride\":36.5", "\"diameterDpOverride\":1e400"));
        assertFailure(VALID.replace(
                "\"diameterDpOverride\":36.5",
                "\"diameterDpOverride\":200.00000000000001"));
    }

    @Test
    public void rejectsIntegersOutsideTheirExactTargetRange() {
        assertFailure(VALID.replace("\"id\":1", "\"id\":2147483648"));
        assertFailure(VALID.replace(
                "\"timestamp\":1001", "\"timestamp\":9223372036854775808"));
        assertFailure(VALID.replace("\"durationMs\":202", "\"durationMs\":2.5"));
    }

    @Test
    public void decodeRejectsFinitePointDiameterOverridesOutsideZeroOrSupportedRange() {
        assertFailure(VALID.replace(
                "\"diameterDpOverride\":36.5", "\"diameterDpOverride\":-1"));
        assertFailure(VALID.replace(
                "\"diameterDpOverride\":36.5", "\"diameterDpOverride\":12"));
        assertFailure(VALID.replace(
                "\"diameterDpOverride\":36.5", "\"diameterDpOverride\":201"));
    }

    @Test
    public void rejectsUnknownEnumValues() {
        assertFailure(VALID.replace("\"kind\":\"INNER\"", "\"kind\":\"TABLET\""));
        assertFailure(VALID.replace(
                "\"disabledReason\":\"HINGE\"", "\"disabledReason\":\"BROKEN\""));
        assertFailure(VALID.replace(
                "\"reason\":\"HINGE\"", "\"reason\":\"BROKEN\""));
    }

    @Test
    public void rejectsBindingsToUnknownProfilesAndWrongBindingTypes() {
        assertFailure(VALID.replace(
                "\"display-A\":\"inner\"", "\"display-A\":\"outer\""));
        assertFailure(VALID.replace(
                "\"display-A\":\"inner\"", "\"display-A\":7"));
        assertFailure(VALID.replace(
                "\"manualBindings\":{\"display-A\":\"inner\"}",
                "\"manualBindings\":[]"));
    }

    @Test
    public void rejectsMissingOrWrongTypedRequiredFields() {
        assertFailure(VALID.replace("\"durationMs\":202,", ""));
        assertFailure(VALID.replace("\"id\":1,", "\"id\":\"1\","));
        assertFailure(VALID.replace("\"enabled\":false", "\"enabled\":\"false\""));
        assertFailure(VALID.replace(
                "\"fingerprints\":[\"display-A\",\"display-B\"]",
                "\"fingerprints\":{}"));
        assertFailure(VALID.replace(
                "\"referenceRegions\":[{", "\"referenceRegions\":[7,{"));
        assertFailure(VALID.replace(
                "\"bounds\":{\"left\":0,\"top\":1,\"right\":1080,\"bottom\":2200}",
                "\"bounds\":[]"));
    }

    @Test
    public void oneMalformedProfileMakesTheWholeDecodeFail() {
        ProfileJsonCodec.DecodeResult result = new ProfileJsonCodec().decode(
                documentJson(VALID_PROFILE, "{\"id\":\"outer\"}"));

        assertFalse(result.isSuccess());
        assertNull(result.getDocument());
        assertNotNull(result.getFatalError());
        assertFalse(result.getFatalError().isEmpty());
        assertWarningsAreUnmodifiable(result.getWarnings());
    }

    @Test
    public void warningsAreImmutableOnSuccessfulDecode() {
        ProfileJsonCodec.DecodeResult result = new ProfileJsonCodec().decode(VALID);

        assertTrue(result.getFatalError(), result.isSuccess());
        assertWarningsAreUnmodifiable(result.getWarnings());
    }

    @Test
    public void encodeRejectsUnsupportedOrInvalidDocuments() throws Exception {
        ProfileJsonCodec codec = new ProfileJsonCodec();
        ScreenProfile validProfile = profileWithPoint(ProfilePoint.enabled(
                1, "full", 0.5f, 0.25f, 1L, 2L, 3L));
        Map<String, ScreenProfile> profiles = new LinkedHashMap<>();
        profiles.put("inner", validProfile);

        assertEncodeFails(codec, new ProfileDocument(
                3, profiles, Collections.<String, String>emptyMap()));
        assertEncodeFails(codec, new ProfileDocument(
                2, profiles, Collections.singletonMap("display-A", "missing")));

        ScreenProfile invalidCoordinate = profileWithPoint(ProfilePoint.enabled(
                1, "full", Float.NaN, 0.25f, 1L, 2L, 3L));
        assertEncodeFails(codec, documentWithProfile(invalidCoordinate));

        ScreenProfile emptyRegion = profileWithPoint(ProfilePoint.enabled(
                1, "", 0.5f, 0.25f, 1L, 2L, 3L));
        assertEncodeFails(codec, documentWithProfile(emptyRegion));

        List<ProfilePoint> tooManyPoints = new ArrayList<>();
        for (int id = 0; id < 65; id++) {
            tooManyPoints.add(ProfilePoint.enabled(
                    id, "full", 0.5f, 0.25f, 1L, 2L, 3L));
        }
        assertEncodeFails(codec, documentWithProfile(profileWithPoints(tooManyPoints)));
    }

    @Test
    public void encodeRejectsFinitePointDiameterOverridesOutsideZeroOrSupportedRange()
            throws Exception {
        ProfileJsonCodec codec = new ProfileJsonCodec();
        float[] invalidOverrides = {-1f, 12f, 201f};

        for (float invalidOverride : invalidOverrides) {
            ProfilePoint point = ProfilePoint.enabled(
                    1, "full", 0.5f, 0.25f, 1L, 2L, 3L)
                    .withDiameterDpOverride(invalidOverride);
            assertEncodeFails(codec, documentWithProfile(profileWithPoint(point)));
        }
    }

    @Test
    public void encodeRejectsDuplicateReferenceRegionIdsAndDanglingPointRegions()
            throws Exception {
        ProfileJsonCodec codec = new ProfileJsonCodec();
        DisplayRegion full = new DisplayRegion("full", new IntRect(0, 0, 100, 200));
        List<DisplayRegion> duplicates = Arrays.asList(
                full,
                new DisplayRegion("full", new IntRect(0, 0, 200, 400))
        );
        ScreenProfile duplicateRegions = profileWithPointsAndRegions(
                Collections.singletonList(ProfilePoint.enabled(
                        1, "full", 0.5f, 0.25f, 1L, 2L, 3L)),
                duplicates
        );
        ScreenProfile danglingPoint = profileWithPointsAndRegions(
                Collections.singletonList(ProfilePoint.enabled(
                        1, "missing", 0.5f, 0.25f, 1L, 2L, 3L)),
                Collections.singletonList(full)
        );

        assertEncodeFails(codec, documentWithProfile(duplicateRegions));
        assertEncodeFails(codec, documentWithProfile(danglingPoint));
    }

    @Test
    public void accepts64PointsAndOverrideBoundaryValues() throws Exception {
        List<ProfilePoint> points = new ArrayList<>();
        for (int id = 0; id < 64; id++) {
            points.add(ProfilePoint.enabled(
                    id, "full", 0.5f, 0.25f, 1L, 2L, 3L));
        }
        String exactly64 = VALID.replace(
                "\"points\":[" + VALID_POINT + "]",
                "\"points\":[" + pointsJson(64) + "]");
        ProfileJsonCodec codec = new ProfileJsonCodec();

        assertSuccess(exactly64);
        ProfileJsonCodec.DecodeResult encoded64 = codec.decode(
                codec.encode(documentWithProfile(profileWithPoints(points))));
        assertTrue(encoded64.getFatalError(), encoded64.isSuccess());
        assertEquals(64, encoded64.getDocument().getProfiles().get("inner")
                .getPoints().size());

        float[] allowedOverrides = {0f, 24f, 200f};
        for (float allowedOverride : allowedOverrides) {
            assertSuccess(VALID.replace(
                    "\"diameterDpOverride\":36.5",
                    "\"diameterDpOverride\":" + Float.toString(allowedOverride)));
            ProfilePoint point = ProfilePoint.enabled(
                    1, "full", 0.5f, 0.25f, 1L, 2L, 3L)
                    .withDiameterDpOverride(allowedOverride);
            ProfileJsonCodec.DecodeResult roundTrip = codec.decode(
                    codec.encode(documentWithProfile(profileWithPoint(point))));
            assertTrue(roundTrip.getFatalError(), roundTrip.isSuccess());
            assertEquals(allowedOverride, roundTrip.getDocument().getProfiles().get("inner")
                    .getPoints().get(0).getDiameterDpOverride(), 0f);
        }
    }

    private static String documentJson(String... profiles) {
        StringBuilder result = new StringBuilder("{\"schemaVersion\":2,\"profiles\":[");
        for (int index = 0; index < profiles.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(profiles[index]);
        }
        return result.append("],\"manualBindings\":{\"display-A\":\"inner\"}}")
                .toString();
    }

    @Test
    public void roundTripsNaturalAnchorWhileStillAcceptingOlderPointsWithoutOne() throws Exception {
        ProfileJsonCodec codec = new ProfileJsonCodec();
        ProfilePoint anchored = ProfilePoint.enabledWithNaturalAnchor(
                1, "full", 0.2f, 0.3f, 0.4f, 0.5f, 1L, 2L, 3L);

        String encoded = codec.encode(documentWithProfile(profileWithPoint(anchored)));
        ProfileJsonCodec.DecodeResult decoded = codec.decode(encoded);

        assertTrue(decoded.getFatalError(), decoded.isSuccess());
        ProfilePoint restored = decoded.getDocument().getProfiles().get("inner")
                .getPoints().get(0);
        assertTrue(restored.hasNaturalAnchor());
        assertEquals(0.4f, restored.getNaturalU(), 0f);
        assertEquals(0.5f, restored.getNaturalV(), 0f);
        ProfilePoint legacy = ProfilePoint.enabled(
                2, "full", 0.2f, 0.3f, 1L, 2L, 3L);
        ProfileJsonCodec.DecodeResult decodedLegacy = codec.decode(
                codec.encode(documentWithProfile(profileWithPoint(legacy))));
        assertTrue(decodedLegacy.getFatalError(), decodedLegacy.isSuccess());
        assertFalse(decodedLegacy.getDocument().getProfiles().get("inner")
                .getPoints().get(0).hasNaturalAnchor());
    }

    private static ProfileDocument documentWithProfile(ScreenProfile profile) {
        Map<String, ScreenProfile> profiles = new LinkedHashMap<>();
        profiles.put(profile.getId(), profile);
        return new ProfileDocument(2, profiles, Collections.<String, String>emptyMap());
    }

    private static ScreenProfile profileWithPoint(ProfilePoint point) {
        return profileWithPoints(Collections.singletonList(point));
    }

    private static ScreenProfile profileWithPoints(List<ProfilePoint> points) {
        List<DisplayRegion> regions = Collections.singletonList(
                new DisplayRegion("full", new IntRect(0, 0, 100, 200)));
        return profileWithPointsAndRegions(points, regions);
    }

    private static ScreenProfile profileWithPointsAndRegions(
            List<ProfilePoint> points,
            List<DisplayRegion> regions
    ) {
        List<UnsafeArea> unsafeAreas = Collections.singletonList(
                new UnsafeArea(new IntRect(40, 0, 60, 200), PointDisabledReason.HINGE));
        return new ScreenProfile(
                "inner",
                ProfileKind.INNER,
                40f,
                points,
                new ArrayList<>(Collections.singletonList("display-A")),
                regions,
                unsafeAreas
        );
    }

    private static String pointJson(int id) {
        return "{\"id\":" + id + ",\"regionId\":\"full\",\"u\":0.5,\"v\":0.25,"
                + "\"diameterDpOverride\":0,\"enabled\":true,"
                + "\"disabledReason\":\"NONE\",\"timestamp\":1,"
                + "\"durationMs\":2,\"displayGeneration\":3}";
    }

    private static String pointsJson(int count) {
        StringBuilder points = new StringBuilder();
        for (int id = 0; id < count; id++) {
            if (id > 0) {
                points.append(',');
            }
            points.append(pointJson(id));
        }
        return points.toString();
    }

    private static ScreenProfile emptyProfile(String id, ProfileKind kind, float diameterDp) {
        return new ScreenProfile(
                id,
                kind,
                diameterDp,
                Collections.<ProfilePoint>emptyList(),
                Collections.<String>emptyList(),
                Collections.<DisplayRegion>emptyList(),
                Collections.<UnsafeArea>emptyList()
        );
    }

    private static void assertFailure(String raw) {
        ProfileJsonCodec.DecodeResult result = new ProfileJsonCodec().decode(raw);
        assertFalse("Expected decode failure for: " + raw, result.isSuccess());
        assertNull(result.getDocument());
        assertNotNull(result.getFatalError());
        assertFalse(result.getFatalError().isEmpty());
    }

    private static void assertSuccess(String raw) {
        ProfileJsonCodec.DecodeResult result = new ProfileJsonCodec().decode(raw);
        assertTrue(result.getFatalError(), result.isSuccess());
        assertNotNull(result.getDocument());
        assertNull(result.getFatalError());
    }

    private static void assertWarningsAreUnmodifiable(List<String> warnings) {
        try {
            warnings.add("unexpected");
            fail("Expected warnings to be unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void assertEncodeFails(
            ProfileJsonCodec codec,
            ProfileDocument document
    ) throws Exception {
        try {
            codec.encode(document);
            fail("Expected encode to fail");
        } catch (JSONException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
