package com.payton.touchblocker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ManagePointsActivityTest {
    @Test
    public void globalSizeSliderValueSnapsLegacyFractionalDpToItsWholeDpStep() {
        assertEquals(30f, ManagePointsActivity.sliderValueForGlobalDiameter(30.095238f), 0f);
        assertEquals(24f, ManagePointsActivity.sliderValueForGlobalDiameter(1f), 0f);
        assertEquals(200f, ManagePointsActivity.sliderValueForGlobalDiameter(500f), 0f);
    }
}
