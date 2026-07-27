package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplaySnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class ProfileSelectorTest {
    @Test
    public void manualBindingOverridesAutomaticSelection() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "display-A");
        ScreenProfile outer = TestFixtures.profile(
                "outer", ProfileKind.OUTER, "outer-display");
        ProfileDocument document = TestFixtures.document(inner, outer)
                .withManualBinding("display-A", "outer");

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("display-A", ProfileKind.INNER), document);

        assertEquals(ProfileSelection.Status.MATCHED, selection.getStatus());
        assertEquals(ProfileSelection.Reason.MANUAL_BINDING, selection.getReason());
        assertEquals("outer", selection.getProfileId());
    }

    @Test
    public void exactFingerprintSelectsKnownProfile() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "inner-display");
        ScreenProfile outer = TestFixtures.profile(
                "outer", ProfileKind.OUTER, "outer-display");

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("inner-display", ProfileKind.OUTER),
                TestFixtures.document(inner, outer));

        assertEquals(ProfileSelection.Status.MATCHED, selection.getStatus());
        assertEquals(ProfileSelection.Reason.EXACT_FINGERPRINT, selection.getReason());
        assertEquals("inner", selection.getProfileId());
    }

    @Test
    public void uniqueSuggestedKindSelectsOuterProfile() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "inner-display");
        ScreenProfile outer = TestFixtures.profile(
                "outer", ProfileKind.OUTER, "outer-display");

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("new-display", ProfileKind.OUTER),
                TestFixtures.document(inner, outer));

        assertEquals(ProfileSelection.Status.MATCHED, selection.getStatus());
        assertEquals(ProfileSelection.Reason.SUGGESTED_KIND, selection.getReason());
        assertEquals("outer", selection.getProfileId());
    }

    @Test
    public void multipleSuggestedKindCandidatesReturnAmbiguousWithNullProfileId() {
        ScreenProfile outerOne = TestFixtures.profile(
                "outer-one", ProfileKind.OUTER, "outer-display-one");
        ScreenProfile outerTwo = TestFixtures.profile(
                "outer-two", ProfileKind.OUTER, "outer-display-two");

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("new-display", ProfileKind.OUTER),
                TestFixtures.document(outerOne, outerTwo));

        assertEquals(ProfileSelection.Status.AMBIGUOUS, selection.getStatus());
        assertEquals(ProfileSelection.Reason.AMBIGUOUS, selection.getReason());
        assertNull(selection.getProfileId());
    }

    @Test
    public void noCandidateReturnsUnknown() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("inner", ProfileKind.INNER, "inner-display"));

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("new-display", ProfileKind.OUTER), document);

        assertEquals(ProfileSelection.Status.UNKNOWN, selection.getStatus());
        assertEquals(ProfileSelection.Reason.NO_MATCH, selection.getReason());
        assertNull(selection.getProfileId());
    }

    @Test
    public void withoutManualBindingRemovesOnlyRequestedBindingAndLeavesOriginalUnchanged() {
        Map<String, String> bindings = new LinkedHashMap<>();
        bindings.put("display-A", "inner");
        bindings.put("display-B", "outer");
        ProfileDocument original = new ProfileDocument(
                2, Collections.<String, ScreenProfile>emptyMap(), bindings);

        ProfileDocument restoredAutomatic = original.withoutManualBinding("display-A");

        assertEquals("inner", original.getManualBindings().get("display-A"));
        assertEquals("outer", original.getManualBindings().get("display-B"));
        assertFalse(restoredAutomatic.getManualBindings().containsKey("display-A"));
        assertEquals("outer", restoredAutomatic.getManualBindings().get("display-B"));
        assertNotSame(original.getManualBindings(), restoredAutomatic.getManualBindings());
    }

    @Test(expected = NullPointerException.class)
    public void withoutManualBindingRejectsNullStableKey() {
        TestFixtures.document().withoutManualBinding(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void documentRejectsProfileMapKeyThatDiffersFromProfileId() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "inner-display");
        Map<String, ScreenProfile> profiles = new LinkedHashMap<>();
        profiles.put("wrong-key", inner);

        new ProfileDocument(2, profiles, Collections.<String, String>emptyMap());
    }

    @Test
    public void danglingManualBindingFallsBackToExactFingerprint() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "display-A");
        ProfileDocument document = TestFixtures.document(inner)
                .withManualBinding("display-A", "missing-profile");

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("display-A", ProfileKind.OUTER), document);

        assertEquals(ProfileSelection.Status.MATCHED, selection.getStatus());
        assertEquals(ProfileSelection.Reason.EXACT_FINGERPRINT, selection.getReason());
        assertEquals("inner", selection.getProfileId());
    }

    @Test
    public void duplicateExactFingerprintsReturnAmbiguous() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "shared-display");
        ScreenProfile outer = TestFixtures.profile(
                "outer", ProfileKind.OUTER, "shared-display");

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("shared-display", ProfileKind.INNER),
                TestFixtures.document(inner, outer));

        assertEquals(ProfileSelection.Status.AMBIGUOUS, selection.getStatus());
        assertEquals(ProfileSelection.Reason.AMBIGUOUS, selection.getReason());
        assertNull(selection.getProfileId());
    }

    @Test
    public void nullSuggestedKindReturnsUnknown() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("inner", ProfileKind.INNER, "inner-display"));

        ProfileSelection selection = new ProfileSelector().select(
                TestFixtures.snapshot("new-display", null), document);

        assertEquals(ProfileSelection.Status.UNKNOWN, selection.getStatus());
        assertEquals(ProfileSelection.Reason.NO_MATCH, selection.getReason());
        assertNull(selection.getProfileId());
    }

    @Test
    public void selectDoesNotMutateDocument() {
        ScreenProfile inner = TestFixtures.profile(
                "inner", ProfileKind.INNER, "inner-display");
        ProfileDocument document = TestFixtures.document(inner)
                .withManualBinding("display-A", "missing-profile");
        Map<String, ScreenProfile> profilesBefore = document.getProfiles();
        Map<String, String> bindingsBefore = document.getManualBindings();

        new ProfileSelector().select(
                TestFixtures.snapshot("display-A", ProfileKind.INNER), document);

        assertSame(profilesBefore, document.getProfiles());
        assertSame(bindingsBefore, document.getManualBindings());
        assertSame(inner, document.getProfiles().get("inner"));
        assertEquals("missing-profile", document.getManualBindings().get("display-A"));
        assertTrue(document.getProfiles().get("inner").getFingerprints()
                .contains("inner-display"));
    }
}
