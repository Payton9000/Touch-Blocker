package com.payton.touchblocker.profile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.IntRect;

import org.junit.Test;

public class FoldableUiTest {
    @Test
    public void singleProfileFlatPhoneHidesControls() {
        assertFalse(FoldableUi.shouldShowProfileControls(
                TestFixtures.document(TestFixtures.profile("p", ProfileKind.INNER, "d")),
                TestFixtures.singleRegion(1000, 1800)));
    }

    @Test
    public void twoProfilesShowControls() {
        assertTrue(FoldableUi.shouldShowProfileControls(
                TestFixtures.document(
                        TestFixtures.profile("inner", ProfileKind.INNER, "a"),
                        TestFixtures.profile("outer", ProfileKind.OUTER, "b")),
                TestFixtures.singleRegion(1000, 1800)));
    }

    @Test
    public void hingePresenceShowsControls() {
        assertTrue(FoldableUi.shouldShowProfileControls(
                TestFixtures.document(TestFixtures.profile("p", ProfileKind.INNER, "d")),
                TestFixtures.withUnsafeRect(2020, 1800,
                        new IntRect(1000, 0, 1020, 1800), PointDisabledReason.HINGE)));
    }
}
