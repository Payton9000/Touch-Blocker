package com.payton.touchblocker.profile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.IntRect;

import org.junit.Test;

public class ServiceStartCheckTest {
    @Test
    public void matchedProfileWithResolvablePointStarts() {
        ScreenProfile profile = TestFixtures.profileWithPoint(
                ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L));
        assertTrue(ServiceStartCheck.hasBlockablePoints(
                TestFixtures.document(profile), TestFixtures.snapshot(
                        new DisplayRegion("full", new IntRect(0, 0, 1000, 1800)))));
    }

    @Test
    public void unknownSelectionDoesNotStart() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("inner", ProfileKind.INNER, "inner-display"));
        assertFalse(ServiceStartCheck.hasBlockablePoints(
                document, TestFixtures.snapshot("new-display", ProfileKind.OUTER)));
    }

    @Test
    public void matchedProfileWithoutEnabledPointsDoesNotStart() {
        ProfileDocument document = TestFixtures.document(
                TestFixtures.profile("inner", ProfileKind.INNER, "test-display"));
        assertFalse(ServiceStartCheck.hasBlockablePoints(
                document, TestFixtures.snapshot("test-display", ProfileKind.INNER)));
    }
}
