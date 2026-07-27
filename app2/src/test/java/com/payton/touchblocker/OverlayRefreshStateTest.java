package com.payton.touchblocker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class OverlayRefreshStateTest {
    @Test
    public void sameStampDoesNotSkipAfterPartialWindowApplication() {
        OverlayRefreshState state = new OverlayRefreshState();
        Set<String> wanted = keys("1", "2");

        state.recordRefreshAttempt("same-stamp");
        state.recordAppliedWindows(wanted, wanted);
        assertTrue(state.shouldSkip("same-stamp", wanted));

        Set<String> survivingWindow = keys("1");
        state.recordRefreshAttempt("same-stamp");
        state.recordAppliedWindows(wanted, survivingWindow);

        assertFalse(state.shouldSkip("same-stamp", survivingWindow));
    }

    private static Set<String> keys(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
