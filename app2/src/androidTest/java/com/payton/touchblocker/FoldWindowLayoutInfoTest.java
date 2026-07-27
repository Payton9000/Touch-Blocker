package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.Display;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Lifecycle;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowLayoutInfo;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule;

import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.FoldFeatureData;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.profile.PointDisabledReason;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class FoldWindowLayoutInfoTest {
    private static final long CALLBACK_TIMEOUT_MS = 3_000L;

    @Rule
    public final WindowLayoutInfoPublisherRule publisherRule =
            new WindowLayoutInfoPublisherRule();

    @Test
    public void injectedVerticalHingeProducesLeftAndRightPanesForContextCapture() {
        assertInjectedFoldProducesTwoPanes(FoldingFeature.Orientation.VERTICAL, "left", "right");
    }

    @Test
    public void injectedHorizontalHingeProducesTopAndBottomPanesForContextCapture() {
        assertInjectedFoldProducesTwoPanes(FoldingFeature.Orientation.HORIZONTAL, "top", "bottom");
    }

    private void assertInjectedFoldProducesTwoPanes(
            FoldingFeature.Orientation orientation,
            String firstPaneId,
            String secondPaneId
    ) {
        WindowLayoutInfoObserver observer = new WindowLayoutInfoObserver();
        AndroidDisplaySnapshotProvider provider = new AndroidDisplaySnapshotProvider(
                new DisplayGenerationTracker(), observer);
        AtomicReference<Rect> localBounds = new AtomicReference<>();
        AtomicReference<DisplaySnapshot> contextSnapshot = new AtomicReference<>();
        AtomicReference<DisplaySnapshot> activitySnapshot = new AtomicReference<>();
        AtomicReference<DisplaySnapshot> crossInstanceSnapshot = new AtomicReference<>();
        AtomicReference<DisplaySnapshot> resumedActivitySnapshot = new AtomicReference<>();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                WindowMetrics metrics = WindowMetricsCalculator.getOrCreate()
                        .computeCurrentWindowMetrics(activity);
                Rect bounds = metrics.getBounds();
                localBounds.set(new Rect(0, 0, bounds.width(), bounds.height()));
                observer.start(activity);
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Rect foldBounds = separatingFoldBounds(localBounds.get(), orientation);
            publisherRule.overrideWindowLayoutInfo(new WindowLayoutInfo(
                    Collections.<DisplayFeature>singletonList(
                            new FakeFoldingFeature(foldBounds, orientation))));
            waitForFold(observer, foldBounds);

            scenario.onActivity(activity -> {
                @SuppressWarnings("deprecation")
                Display display = activity.getWindowManager().getDefaultDisplay();
                contextSnapshot.set(provider.capture((Context) activity, display.getDisplayId()));
                activitySnapshot.set(activity.captureDisplaySnapshotForTest());
            });

            observer.stop();
            AndroidDisplaySnapshotProvider replacementProvider =
                    new AndroidDisplaySnapshotProvider(
                            new DisplayGenerationTracker(),
                            new WindowLayoutInfoObserver());
            scenario.onActivity(activity -> {
                @SuppressWarnings("deprecation")
                Display display = activity.getWindowManager().getDefaultDisplay();
                crossInstanceSnapshot.set(replacementProvider.capture(
                        (Context) activity, display.getDisplayId()));
            });

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> resumedActivitySnapshot.set(
                    activity.captureDisplaySnapshotForTest()));
        } finally {
            observer.stop();
        }

        assertFoldedSnapshot(contextSnapshot.get(), firstPaneId, secondPaneId);
        assertFoldedSnapshot(activitySnapshot.get(), firstPaneId, secondPaneId);
        assertFoldedSnapshot(crossInstanceSnapshot.get(), firstPaneId, secondPaneId);
        assertFoldedSnapshot(resumedActivitySnapshot.get(), firstPaneId, secondPaneId);
    }

    private static void assertFoldedSnapshot(
            DisplaySnapshot captured,
            String firstPaneId,
            String secondPaneId
    ) {
        assertNotNull(captured);
        assertEquals(2, captured.getRegions().size());
        assertEquals(firstPaneId, captured.getRegions().get(0).getId());
        assertEquals(secondPaneId, captured.getRegions().get(1).getId());
        assertEquals(PointDisabledReason.HINGE,
                captured.getUnsafeAreas().get(0).getReason());
    }

    private static Rect separatingFoldBounds(
            Rect bounds,
            FoldingFeature.Orientation orientation
    ) {
        if (FoldingFeature.Orientation.VERTICAL.equals(orientation)) {
            int center = bounds.width() / 2;
            return new Rect(center, 0, center + 1, bounds.height());
        }
        int center = bounds.height() / 2;
        return new Rect(0, center, bounds.width(), center + 1);
    }

    private static void waitForFold(WindowLayoutInfoObserver observer, Rect expectedBounds) {
        long deadline = SystemClock.uptimeMillis() + CALLBACK_TIMEOUT_MS;
        FoldFeatureData fold = observer.getLatestFoldFeature();
        while (!hasBounds(fold, expectedBounds) && SystemClock.uptimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(20L);
            fold = observer.getLatestFoldFeature();
        }
        assertNotNull("WindowLayoutInfo callback was not delivered", fold);
        assertEquals(expectedBounds.left, fold.getBounds().getLeft());
        assertEquals(expectedBounds.top, fold.getBounds().getTop());
        assertEquals(expectedBounds.right, fold.getBounds().getRight());
        assertEquals(expectedBounds.bottom, fold.getBounds().getBottom());
    }

    private static boolean hasBounds(FoldFeatureData fold, Rect expectedBounds) {
        return fold != null
                && fold.getBounds().getLeft() == expectedBounds.left
                && fold.getBounds().getTop() == expectedBounds.top
                && fold.getBounds().getRight() == expectedBounds.right
                && fold.getBounds().getBottom() == expectedBounds.bottom;
    }

    private static final class FakeFoldingFeature implements FoldingFeature {
        private final Rect bounds;
        private final Orientation orientation;

        private FakeFoldingFeature(Rect bounds, Orientation orientation) {
            this.bounds = new Rect(bounds);
            this.orientation = orientation;
        }

        @Override
        public Rect getBounds() {
            return new Rect(bounds);
        }

        @Override
        public boolean isSeparating() {
            return true;
        }

        @Override
        public OcclusionType getOcclusionType() {
            return OcclusionType.FULL;
        }

        @Override
        public Orientation getOrientation() {
            return orientation;
        }

        @Override
        public State getState() {
            return State.FLAT;
        }
    }
}
