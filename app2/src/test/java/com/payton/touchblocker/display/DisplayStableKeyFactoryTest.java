package com.payton.touchblocker.display;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class DisplayStableKeyFactoryTest {
    @Test
    public void stableKeyUsesRotationInvariantConfigurationEvidence() {
        String portrait = DisplayStableKeyFactory.create(
                "local:0", 1840, 2208, 1840, 2208, 420, "built-in-inner");
        String landscape = DisplayStableKeyFactory.create(
                "local:0", 2208, 1840, 2208, 1840, 420, "built-in-inner");

        assertEquals(portrait, landscape);
        assertNotEquals(portrait, DisplayStableKeyFactory.create(
                "local:0", 1080, 2092, 1080, 2092, 420, "built-in-outer"));
        assertTrue(portrait.contains("local:0"));
        assertTrue(portrait.contains("1840x2208"));
        assertTrue(portrait.contains("420"));
        assertTrue(portrait.contains("built-in-inner"));
    }

    @Test
    public void missingUniqueIdStillProducesDeterministicKey() {
        assertEquals(
                DisplayStableKeyFactory.create(null, 1000, 1800, 1000, 1800, 320, "external"),
                DisplayStableKeyFactory.create("", 1800, 1000, 1800, 1000, 320, "external"));
    }
}
