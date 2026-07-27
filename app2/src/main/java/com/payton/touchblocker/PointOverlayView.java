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

    public void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            stopFade();
            setOverlayAlpha(255);
        } else {
            debugHits.clear();
            setOverlayAlpha(DEFAULT_OVERLAY_ALPHA);
        }
    }

    public void startFadeOut() {
        if (debugEnabled) {
            return;
        }
        stopFade();
        fadeAnimator = ValueAnimator.ofInt(overlayAlpha, 0);
        fadeAnimator.setDuration(10_000L);
        fadeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setOverlayAlpha((Integer) animation.getAnimatedValue());
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

    private void setOverlayAlpha(int alpha) {
        overlayAlpha = Math.max(0, Math.min(255, alpha));
        circlePaint.setAlpha(overlayAlpha);
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
