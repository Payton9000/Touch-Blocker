package com.payton.touchblocker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.geometry.OverlayClusterPlanner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class PointOverlayViewBehaviorTest {
    private static final int SIZE_PX = 40;
    private static final int POINT_ID = 1;

    @Test
    public void drawsEveryCornerVisiblyUsingTheRequestedDiameter() throws Exception {
        AtomicReference<Bitmap> bitmapReference = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            setSingleCircle(view, SIZE_PX);
            bitmapReference.set(render(view, SIZE_PX));
        });

        Bitmap bitmap = bitmapReference.get();
        try {
            int centerAlpha = Color.alpha(bitmap.getPixel(SIZE_PX / 2, SIZE_PX / 2));
            assertTrue(centerAlpha > 150);
            assertDimCorner(bitmap, 0, 0, centerAlpha);
            assertDimCorner(bitmap, SIZE_PX - 1, 0, centerAlpha);
            assertDimCorner(bitmap, 0, SIZE_PX - 1, centerAlpha);
            assertDimCorner(bitmap, SIZE_PX - 1, SIZE_PX - 1, centerAlpha);
        } finally {
            bitmap.recycle();
        }
    }

    @Test
    public void reusesShaderDuringAlphaAnimationAndRebuildsItForDiameterChanges()
            throws Exception {
        Field circleGradientsField = PointOverlayView.class.getDeclaredField("circleGradients");
        circleGradientsField.setAccessible(true);
        Field fadeAnimatorField = PointOverlayView.class.getDeclaredField("fadeAnimator");
        fadeAnimatorField.setAccessible(true);

        AtomicReference<Shader> initialGradient = new AtomicReference<>();
        AtomicReference<Shader> afterDebugAlpha = new AtomicReference<>();
        AtomicReference<Shader> duringFade = new AtomicReference<>();
        AtomicReference<Shader> afterResize = new AtomicReference<>();
        AtomicReference<Bitmap> opaqueBitmap = new AtomicReference<>();
        AtomicReference<Bitmap> fadedBitmap = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            setSingleCircle(view, SIZE_PX);
            initialGradient.set(firstGradient(circleGradientsField, view));

            view.setDebugEnabled(true);
            afterDebugAlpha.set(firstGradient(circleGradientsField, view));
            opaqueBitmap.set(render(view, SIZE_PX));

            view.setDebugEnabled(false);
            view.startFadeOut();
            ValueAnimator animator = (ValueAnimator) fadeAnimatorField.get(view);
            assertNotNull(animator);
            animator.setCurrentPlayTime(5_000L);
            duringFade.set(firstGradient(circleGradientsField, view));
            fadedBitmap.set(render(view, SIZE_PX));

            view.dispose();
            setSingleCircle(view, SIZE_PX * 2);
            afterResize.set(firstGradient(circleGradientsField, view));
        });

        try {
            assertSame(initialGradient.get(), afterDebugAlpha.get());
            assertSame(initialGradient.get(), duringFade.get());
            assertNotSame(initialGradient.get(), afterResize.get());
            int center = SIZE_PX / 2;
            assertTrue(Color.alpha(opaqueBitmap.get().getPixel(center, center))
                    > Color.alpha(fadedBitmap.get().getPixel(center, center)));
            assertVisible(fadedBitmap.get(), 0, 0);
        } finally {
            opaqueBitmap.get().recycle();
            fadedBitmap.get().recycle();
        }
    }

    @Test
    public void disposeCancelsAndClearsTheFadeAnimator() throws Exception {
        Field fadeAnimatorField = PointOverlayView.class.getDeclaredField("fadeAnimator");
        fadeAnimatorField.setAccessible(true);
        AtomicReference<ValueAnimator> animatorReference = new AtomicReference<>();
        AtomicReference<Object> animatorAfterDispose = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            view.startFadeOut();
            animatorReference.set((ValueAnimator) fadeAnimatorField.get(view));
            view.dispose();
            animatorAfterDispose.set(fadeAnimatorField.get(view));
        });

        assertNotNull(animatorReference.get());
        assertFalse(animatorReference.get().isStarted());
        assertNull(animatorAfterDispose.get());
    }

    @Test
    public void nonDebugFadeMakesBothTheCircleAndTheBaseTransparent() throws Exception {
        Field fadeAnimatorField = PointOverlayView.class.getDeclaredField("fadeAnimator");
        fadeAnimatorField.setAccessible(true);
        AtomicReference<Bitmap> bitmapReference = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            setSingleCircle(view, SIZE_PX);
            view.setDebugEnabled(false);
            view.startFadeOut();
            ValueAnimator animator = (ValueAnimator) fadeAnimatorField.get(view);
            assertNotNull(animator);
            animator.setCurrentPlayTime(10_000L);
            bitmapReference.set(render(view, SIZE_PX));
        });

        try {
            int center = SIZE_PX / 2;
            assertEquals(0, Color.alpha(bitmapReference.get().getPixel(center, center)));
            assertEquals(0, Color.alpha(bitmapReference.get().getPixel(0, 0)));
        } finally {
            bitmapReference.get().recycle();
        }
    }

    @Test
    public void turningDebugOffStartsTheTenSecondFadeForAnExistingOverlay() throws Exception {
        Field fadeAnimatorField = PointOverlayView.class.getDeclaredField("fadeAnimator");
        fadeAnimatorField.setAccessible(true);
        AtomicReference<ValueAnimator> animatorReference = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            setSingleCircle(view, SIZE_PX);
            view.setDebugEnabled(true);
            view.setDebugEnabled(false);
            animatorReference.set((ValueAnimator) fadeAnimatorField.get(view));
        });

        assertNotNull(animatorReference.get());
        assertTrue(animatorReference.get().isStarted());
    }

    @Test
    public void detachingFromWindowDisposesTheFadeAnimator() throws Exception {
        Field fadeAnimatorField = PointOverlayView.class.getDeclaredField("fadeAnimator");
        fadeAnimatorField.setAccessible(true);
        AtomicReference<Object> animatorAfterDetach = new AtomicReference<>();

        runOnMainSync(() -> {
            DetachablePointOverlayView view = new DetachablePointOverlayView();
            view.startFadeOut();
            view.detachForTest();
            animatorAfterDetach.set(fadeAnimatorField.get(view));
        });

        assertNull(animatorAfterDetach.get());
    }

    @Test
    public void squareWindowConsumesCornerCenterAndEdgeTouches()
            throws Exception {
        AtomicReference<Boolean> cornerDown = new AtomicReference<>();
        AtomicReference<Boolean> centerDown = new AtomicReference<>();
        AtomicReference<Boolean> centerUp = new AtomicReference<>();
        AtomicReference<Boolean> edgeDown = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            setSingleCircle(view, SIZE_PX);
            view.layout(0, 0, SIZE_PX, SIZE_PX);
            long now = SystemClock.uptimeMillis();
            cornerDown.set(view.onTouchEvent(event(
                    now, now, MotionEvent.ACTION_DOWN, 0f, 0f)));
            centerDown.set(view.onTouchEvent(event(
                    now, now + 1, MotionEvent.ACTION_DOWN,
                    SIZE_PX / 2f, SIZE_PX / 2f)));
            centerUp.set(view.onTouchEvent(event(
                    now, now + 2, MotionEvent.ACTION_UP,
                    SIZE_PX / 2f, SIZE_PX / 2f)));
            edgeDown.set(view.onTouchEvent(event(
                    now, now + 3, MotionEvent.ACTION_DOWN,
                    SIZE_PX - 1f, SIZE_PX / 2f)));
        });

        assertTrue(cornerDown.get());
        assertTrue(centerDown.get());
        assertTrue(centerUp.get());
        assertTrue(edgeDown.get());
    }

    @Test
    public void swipeStartingInsideWindowRemainsConsumedWithoutRecordingTap()
            throws Exception {
        Field debugHitsField = PointOverlayView.class.getDeclaredField("debugHits");
        debugHitsField.setAccessible(true);
        AtomicReference<Boolean> moveConsumed = new AtomicReference<>();
        AtomicReference<Boolean> upConsumed = new AtomicReference<>();
        AtomicReference<Integer> hitCount = new AtomicReference<>();

        runOnMainSync(() -> {
            PointOverlayView view = new PointOverlayView(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            setSingleCircle(view, SIZE_PX);
            view.setDebugEnabled(true);
            view.layout(0, 0, SIZE_PX, SIZE_PX);
            long now = SystemClock.uptimeMillis();
            view.onTouchEvent(event(now, now, MotionEvent.ACTION_DOWN,
                    SIZE_PX / 2f, SIZE_PX / 2f));
            moveConsumed.set(view.onTouchEvent(event(
                    now, now + 10, MotionEvent.ACTION_MOVE,
                    SIZE_PX * 2f, SIZE_PX / 2f)));
            upConsumed.set(view.onTouchEvent(event(
                    now, now + 20, MotionEvent.ACTION_UP,
                    SIZE_PX * 2f, SIZE_PX / 2f)));
            hitCount.set(((List<?>) debugHitsField.get(view)).size());
        });

        assertTrue(moveConsumed.get());
        assertTrue(upConsumed.get());
        assertEquals(0, hitCount.get().intValue());
    }

    /**
     * Configures {@code view} with a single circle centered in a window sized exactly
     * {@code diameterPx} square at the origin — the direct equivalent of the pre-{@code
     * setCircles} {@code setDiameterPx(diameterPx)}, which always centered its one implicit
     * circle at {@code (radius, radius)} of a same-sized window.
     */
    private static void setSingleCircle(PointOverlayView view, int diameterPx) {
        view.setCircles(
                Collections.singletonList(new OverlayClusterPlanner.PlannedCircle(
                        POINT_ID, diameterPx / 2f, diameterPx / 2f, diameterPx)),
                new IntRect(0, 0, diameterPx, diameterPx));
    }

    private static Shader firstGradient(Field circleGradientsField, PointOverlayView view)
            throws IllegalAccessException {
        List<?> gradients = (List<?>) circleGradientsField.get(view);
        return (Shader) gradients.get(0);
    }

    private static Bitmap render(PointOverlayView view, int sizePx) {
        view.layout(0, 0, sizePx, sizePx);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void assertVisible(Bitmap bitmap, int x, int y) {
        assertTrue("expected visible pixel at (" + x + "," + y + ")",
                Color.alpha(bitmap.getPixel(x, y)) > 0);
    }

    /**
     * A corner sits outside the circle's radius, so it must show only the constant, faint
     * {@code basePaint} tint: visible (alpha > 0, the assertion's original intent) but capped
     * well under 100 and well under the circle's own center alpha. This catches regressions
     * where a shader-less full-rect draw washes the entire square window in a flat, high-alpha
     * fill instead of leaving everything outside the circles nearly transparent.
     */
    private static void assertDimCorner(Bitmap bitmap, int x, int y, int centerAlpha) {
        int cornerAlpha = Color.alpha(bitmap.getPixel(x, y));
        assertTrue("expected a faintly visible corner pixel at (" + x + "," + y + "), was alpha="
                + cornerAlpha, cornerAlpha > 0 && cornerAlpha < 100);
        assertTrue("expected corner alpha (" + cornerAlpha + ") well below center alpha ("
                + centerAlpha + ")", cornerAlpha < centerAlpha);
    }

    private static MotionEvent event(
            long downTime,
            long eventTime,
            int action,
            float x,
            float y
    ) {
        return MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
    }

    private static void runOnMainSync(ThrowingRunnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        rethrow(failure.get());
    }

    private static void rethrow(Throwable throwable) throws Exception {
        if (throwable == null) {
            return;
        }
        if (throwable instanceof Exception) {
            throw (Exception) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new AssertionError(throwable);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class DetachablePointOverlayView extends PointOverlayView {
        private DetachablePointOverlayView() {
            super(InstrumentationRegistry.getInstrumentation().getTargetContext());
        }

        private void detachForTest() {
            super.onDetachedFromWindow();
        }
    }
}
