package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;

import com.payton.touchblocker.geometry.ResolvedPoint;
import com.payton.touchblocker.profile.ProfilePoint;

import org.junit.Test;

public class LogManagerTest {

    @Test
    public void formatPointRowIncludesProfileAndResolvedCoordinates() {
        ProfilePoint point = ProfilePoint.enabled(
                12, "full", 0.25f, 0.75f, 3456L, 78L, 9L);
        ResolvedPoint resolved = new ResolvedPoint(120.5f, 640.25f, 80f);

        assertEquals(
                "3456,outer,12,full,0.25,0.75,120.5,640.25,78\n",
                LogManager.formatPointRow("outer", point, resolved));
    }
}
