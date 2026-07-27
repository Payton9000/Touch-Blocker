package com.payton.touchblocker.ui;

import static org.junit.Assert.assertEquals;

import com.payton.touchblocker.TestFixtures;
import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.UnsafeArea;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ScreenProfile;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class PreviewModelFactoryTest {
    @Test
    public void normalizesScreenCutoutAndPoints() {
        DisplaySnapshot snapshot = new DisplaySnapshot(
                "d", 0, new IntRect(0, 0, 1000, 2000), 0, 2f, 1L, ProfileKind.INNER,
                Collections.singletonList(new DisplayRegion("full", new IntRect(0, 0, 1000, 2000))),
                Collections.singletonList(new UnsafeArea(
                        new IntRect(400, 0, 600, 100), PointDisabledReason.CUTOUT)));
        ScreenProfile profile = TestFixtures.profileWithPoint(
                ProfilePoint.enabled(1, "full", 0.5f, 0.5f, 0L, 0L, 1L));

        List<PreviewModelFactory.Item> items = PreviewModelFactory.build(snapshot, profile);

        assertEquals(0, items.get(0).kind);
        PreviewModelFactory.Item cutout = items.get(1);
        assertEquals(1, cutout.kind);
        assertEquals(0.4f, cutout.left, 0.001f);
        assertEquals(0.05f, cutout.bottom, 0.001f);
        PreviewModelFactory.Item point = items.get(items.size() - 1);
        assertEquals(3, point.kind);
        assertEquals(0.5f, (point.left + point.right) / 2f, 0.01f);
    }
}
