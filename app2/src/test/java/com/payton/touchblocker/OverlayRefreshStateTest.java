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

    /**
     * A partial apply must be retried even when the surviving window set happens to equal the
     * last fully-successful one. Concretely: point 1 is up, the user adds point 2, its window
     * fails to attach (OEM overlay-window caps do this), and the surviving set {@code {"1"}}
     * coincidentally matches what was last recorded as successful. Skipping then left the new
     * point listed as enabled while nothing blocked it.
     */
    @Test
    public void partialApplyIsRetriedEvenWhenSurvivingWindowsMatchTheLastSuccess() {
        OverlayRefreshState state = new OverlayRefreshState();
        Set<String> onlyFirst = keys("1");
        state.recordRefreshAttempt("stamp-0");
        state.recordAppliedWindows(onlyFirst, onlyFirst);
        assertTrue(state.shouldSkip("stamp-0", onlyFirst));

        Set<String> both = keys("1", "2");
        state.recordRefreshAttempt("stamp-1");
        state.recordAppliedWindows(both, onlyFirst);

        assertFalse(
                "an incomplete apply must never be skipped",
                state.shouldSkip("stamp-1", onlyFirst));
    }

    @Test
    public void skippingResumesOnceEveryWantedWindowIsApplied() {
        OverlayRefreshState state = new OverlayRefreshState();
        Set<String> both = keys("1", "2");
        state.recordRefreshAttempt("stamp-1");
        state.recordAppliedWindows(both, keys("1"));
        assertFalse(state.shouldSkip("stamp-1", keys("1")));

        state.recordRefreshAttempt("stamp-1");
        state.recordAppliedWindows(both, both);

        assertTrue(state.shouldSkip("stamp-1", both));
    }

    private static Set<String> keys(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
