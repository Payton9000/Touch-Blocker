package com.payton.touchblocker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.geometry.OverlayClusterPlanner;

import java.util.ArrayList;
import java.util.List;

public class PointOverlayView extends View {
    private static final String TAG = "PointOverlayView";
    private static final int DEFAULT_OVERLAY_ALPHA = 180;
    private static final int MAX_BASE_ALPHA = 24;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<DebugHit> debugHits = new ArrayList<>();
    private final List<OverlayClusterPlanner.PlannedCircle> circles = new ArrayList<>();
    private final List<Shader> circleGradients = new ArrayList<>();
    private IntRect windowBounds;
    private int overlayAlpha = DEFAULT_OVERLAY_ALPHA;
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean movedBeyondSlop;
    private boolean debugEnabled;
    private int debugCounter = 1;
    private ValueAnimator fadeAnimator;

    public PointOverlayView(Context context) {
        super(context);
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setColor(0xFFFF0000);
        basePaint.setAlpha(MAX_BASE_ALPHA);
        circlePaint.setStyle(Paint.Style.FILL);
        debugPaint.setStyle(Paint.Style.FILL);
        debugPaint.setColor(0x8800CC00);
        debugTextPaint.setColor(0xFF000000);
        debugTextPaint.setTextSize(20f);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        circlePaint.setAlpha(overlayAlpha);
    }

    /**
     * Replaces the circles drawn by this window. {@code bounds} is this window's on-screen
     * position, used to translate each circle's screen-space center into window-local
     * coordinates. Radial gradient shaders are pre-built here (one per circle, same index as
     * {@code circles}) so {@link #onDraw} performs zero allocation.
     */
    public void setCircles(List<OverlayClusterPlanner.PlannedCircle> newCircles, IntRect bounds) {
        circles.clear();
        circles.addAll(newCircles);
        windowBounds = bounds;
        circleGradients.clear();
        for (OverlayClusterPlanner.PlannedCircle circle : circles) {
            float radius = circle.getDiameterPx() / 2f;
            float localCx = circle.getCenterX() - windowBounds.getLeft();
            float localCy = circle.getCenterY() - windowBounds.getTop();
            circleGradients.add(new RadialGradient(
                    localCx, localCy, radius, 0xFFFF0000, 0x00FF0000, Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    /**
     * Applies debug mode to this window. Only a real change of {@code enabled} touches the
     * opacity: repeating the mode already in effect must leave a completed or in-flight fade
     * alone, because every geometry refresh re-applies the current mode to the windows it
     * reuses, and resetting the alpha there would make the circles permanently visible.
     */
    public void setDebugEnabled(boolean enabled) {
        if (debugEnabled == enabled) {
            return;
        }
        debugEnabled = enabled;
        if (enabled) {
            stopFade();
            setOverlayAlphas(255, MAX_BASE_ALPHA);
        } else {
            debugHits.clear();
            setOverlayAlphas(DEFAULT_OVERLAY_ALPHA, MAX_BASE_ALPHA);
            startFadeOut();
        }
    }

    /**
     * Puts this window straight into the fully faded-out state, skipping the reveal. Used for
     * windows rebuilt by a refresh the user did not ask for — a rotation, a fold, or any other
     * display change — so already-invisible blockers do not flash back into view.
     */
    public void hideWithoutFade() {
        if (debugEnabled) {
            return;
        }
        stopFade();
        setOverlayAlphas(0, 0);
    }

    public void startFadeOut() {
        if (debugEnabled) {
            return;
        }
        stopFade();
        fadeAnimator = ValueAnimator.ofInt(0, (int) OverlayFadePolicy.DURATION_MS);
        fadeAnimator.setDuration(OverlayFadePolicy.DURATION_MS);
        fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int elapsedMs = (Integer) animation.getAnimatedValue();
                setOverlayAlphas(
                        OverlayFadePolicy.alphaAt(DEFAULT_OVERLAY_ALPHA, elapsedMs),
                        OverlayFadePolicy.alphaAt(MAX_BASE_ALPHA, elapsedMs));
            }
        });
        fadeAnimator.start();
    }

    public void dispose() {
        stopFade();
    }

    private void stopFade() {
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), basePaint);

        for (int i = 0; i < circles.size(); i++) {
            OverlayClusterPlanner.PlannedCircle circle = circles.get(i);
            float radius = circle.getDiameterPx() / 2f;
            float localCx = circle.getCenterX() - windowBounds.getLeft();
            float localCy = circle.getCenterY() - windowBounds.getTop();
            circlePaint.setShader(circleGradients.get(i));
            canvas.drawCircle(localCx, localCy, radius, circlePaint);
            if (debugEnabled) {
                canvas.drawText("#" + circle.getPointId(), localCx - 6f, localCy - 6f, debugTextPaint);
            }
        }

        if (debugEnabled) {
            for (DebugHit hit : debugHits) {
                canvas.drawCircle(hit.x, hit.y, 8f, debugPaint);
                canvas.drawText(String.valueOf(hit.index), hit.x + 10f, hit.y - 10f, debugTextPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            movedBeyondSlop = false;
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            float dx = Math.abs(event.getRawX() - downX);
            float dy = Math.abs(event.getRawY() - downY);
            if (dx > touchSlop || dy > touchSlop) {
                movedBeyondSlop = true;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            if (!movedBeyondSlop) {
                performClick();
                Log.d(TAG, "blocked tap x=" + downX + " y=" + downY);
                if (debugEnabled) {
                    debugHits.add(new DebugHit(debugCounter++, event.getX(), event.getY()));
                    trimDebugHits();
                    invalidate();
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            return true;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        dispose();
        super.onDetachedFromWindow();
    }

    /**
     * Applies the paint alphas, redrawing only when a value actually changed.
     *
     * <p>The fade animator ticks once per display frame -- 600 times over the 10-second fade on a
     * 60Hz panel -- but alpha is an 8-bit value that only takes {@value #DEFAULT_OVERLAY_ALPHA}
     * distinct steps on the way down. Redrawing on every tick therefore repainted the same pixels
     * several times per visible change, once per overlay window, which is pure waste: with seven
     * windows the fade cost about 11% of total CPU. Skipping no-op frames keeps the fade visually
     * identical while cutting the redraws to the number of steps the user can actually see.
     */
    private void setOverlayAlphas(int alpha, int baseAlpha) {
        int boundedAlpha = Math.max(0, Math.min(255, alpha));
        int boundedBase = Math.max(0, Math.min(MAX_BASE_ALPHA, baseAlpha));
        if (boundedAlpha == overlayAlpha && boundedBase == basePaint.getAlpha()) {
            return;
        }
        overlayAlpha = boundedAlpha;
        circlePaint.setAlpha(boundedAlpha);
        basePaint.setAlpha(boundedBase);
        invalidate();
    }

    private void trimDebugHits() {
        int max = 25;
        if (debugHits.size() > max) {
            debugHits.subList(0, debugHits.size() - max).clear();
        }
    }

    private static class DebugHit {
        private final int index;
        private final float x;
        private final float y;

        private DebugHit(int index, float x, float y) {
            this.index = index;
            this.x = x;
            this.y = y;
        }
    }
}
