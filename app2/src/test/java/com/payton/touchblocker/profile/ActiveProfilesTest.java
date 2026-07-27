package com.payton.touchblocker.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;

import org.junit.Test;

public class ActiveProfilesTest {
    @Test
    public void matchedSelectionReturnsExistingProfile() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("inner", ProfileKind.INNER, "inner-display"));
        ActiveProfiles.Active active = ActiveProfiles.ensure(
                document, TestFixtures.snapshot("inner-display", ProfileKind.INNER));
        assertEquals("inner", active.getProfileId());
        assertFalse(active.isCreated());
        assertSame(document, active.getDocument());
    }

    @Test
    public void unknownSelectionCreatesFingerprintedProfile() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("inner", ProfileKind.INNER, "inner-display"));
        ActiveProfiles.Active active = ActiveProfiles.ensure(
                document, TestFixtures.snapshot("brand-new", ProfileKind.OUTER));

        assertTrue(active.isCreated());
        ScreenProfile created = active.getDocument().getProfiles().get(active.getProfileId());
        assertEquals(ProfileKind.OUTER, created.getKind());
        assertTrue(created.getFingerprints().contains("brand-new"));
        assertEquals(2, active.getDocument().getProfiles().size());
    }

    @Test
    public void ambiguousSelectionReturnsNull() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("a", ProfileKind.OUTER, "display-a"),
                TestFixtures.profile("b", ProfileKind.OUTER, "display-b"));
        assertNull(ActiveProfiles.ensure(
                document, TestFixtures.snapshot("unmatched", ProfileKind.OUTER)));
    }
}
