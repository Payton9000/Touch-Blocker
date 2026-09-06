package com.payton.touchblocker;

import com.payton.touchblocker.profile.PointDisabledReason;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class ManagePointsActivityTest {
    @Test
    public void globalSizeSliderValueSnapsLegacyFractionalDpToItsWholeDpStep() {
        assertEquals(30f, ManagePointsActivity.sliderValueForGlobalDiameter(30.095238f), 0f);
        assertEquals(24f, ManagePointsActivity.sliderValueForGlobalDiameter(1f), 0f);
        assertEquals(200f, ManagePointsActivity.sliderValueForGlobalDiameter(500f), 0f);
    }

    /**
     * A point covered by a cutout or by the fold comes back on its own once the display geometry
     * moves; a genuinely off-display point does not. Sharing one message told the user to re-record
     * something that would have fixed itself, so these three reasons must read differently.
     */
    @Test
    public void recoverableAndPermanentDisableReasonsReadDifferently() {
        int cutout = ManagePointsActivity.disabledReasonString(PointDisabledReason.CUTOUT);
        int hinge = ManagePointsActivity.disabledReasonString(PointDisabledReason.HINGE);
        int outOfBounds =
                ManagePointsActivity.disabledReasonString(PointDisabledReason.OUT_OF_BOUNDS);

        assertNotEquals(0, cutout);
        assertNotEquals(0, hinge);
        assertNotEquals(0, outOfBounds);
        assertNotEquals("cutout must not reuse the out-of-bounds text", outOfBounds, cutout);
        assertNotEquals("hinge must not reuse the out-of-bounds text", outOfBounds, hinge);
        assertNotEquals("cutout and hinge are different situations", cutout, hinge);
    }

    @Test
    public void anEnabledPointHasNoDisableMessage() {
        assertEquals(0, ManagePointsActivity.disabledReasonString(PointDisabledReason.NONE));
    }

    @Test
    public void invalidDataAndManualDisableKeepTheirOwnMessages() {
        int invalid = ManagePointsActivity.disabledReasonString(PointDisabledReason.INVALID_DATA);
        int manual = ManagePointsActivity.disabledReasonString(PointDisabledReason.NEEDS_REVIEW);

        assertNotEquals(0, invalid);
        assertNotEquals(0, manual);
        assertNotEquals(invalid, manual);
    }
}
