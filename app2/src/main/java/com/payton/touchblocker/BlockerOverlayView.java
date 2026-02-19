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

import java.util.ArrayList;
import java.util.List;

public class BlockerOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<TouchPoint> points = new ArrayList<>();
    private final List<DebugHit> debugHits = new ArrayList<>();
    private int globalSizePx = 120;
    private int overlayAlpha = 180;
    private boolean debugEnabled = false;
    private int debugCounter = 1;
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean trackingBlocked;
    private boolean movedBeyondSlop;
    private ValueAnimator fadeAnimator;

    private static final String TAG = "BlockerOverlayView";

    public BlockerOverlayView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
        debugPaint.setStyle(Paint.Style.FILL);
        debugPaint.setColor(0x99FF0000);
        debugTextPaint.setColor(0xFF000000);
        debugTextPaint.setTextSize(24f);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPoints(List<TouchPoint> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        invalidate();
    }

    public void setGlobalSizePx(int sizePx) {
        globalSizePx = Math.max(30, sizePx);
        invalidate();
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
                overlayAlpha = (Integer) animation.getAnimatedValue();
                invalidate();
            }
        });
        fadeAnimator.start();
    }

    public void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            stopFade();
            overlayAlpha = 255;
        } else {
            debugHits.clear();
            overlayAlpha = 180;
        }
        invalidate();
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
        for (TouchPoint point : points) {
            if (!point.isEnabled()) {
                continue;
            }
            int size = point.getSizeOverridePx() > 0 ? point.getSizeOverridePx() : globalSizePx;
            float radius = size / 2f;
            float px = PointStore.resolveX(getContext(), point);
            float py = PointStore.resolveY(getContext(), point);
            int colorCenter = (overlayAlpha << 24) | 0x00FF0000;
            int colorEdge = (Math.max(0, overlayAlpha - 120) << 24) | 0x00FF0000;
            RadialGradient gradient = new RadialGradient(
                    px,
                    py,
                    radius,
                    colorCenter,
                    colorEdge,
                    Shader.TileMode.CLAMP
            );
            paint.setShader(gradient);
            canvas.drawCircle(px, py, radius, paint);
        }

        if (debugEnabled) {
            for (DebugHit hit : debugHits) {
                canvas.drawCircle(hit.x, hit.y, 12f, debugPaint);
                canvas.drawText(String.valueOf(hit.index), hit.x + 14f, hit.y - 14f, debugTextPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
            trackingBlocked = isBlocked(downX, downY);
            movedBeyondSlop = false;
            return trackingBlocked;
        }

        if (!trackingBlocked) {
            return false;
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
                Log.d(TAG, "blocked tap x=" + downX + " y=" + downY);
                if (debugEnabled) {
                    debugHits.add(new DebugHit(debugCounter++, downX, downY));
                    trimDebugHits();
                    invalidate();
                }
            }
            trackingBlocked = false;
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            trackingBlocked = false;
            return true;
        }

        return true;
    }

    public boolean isBlocked(float x, float y) {
        for (TouchPoint point : points) {
            if (!point.isEnabled()) {
                continue;
            }
            int size = point.getSizeOverridePx() > 0 ? point.getSizeOverridePx() : globalSizePx;
            float radius = size / 2f;
            float px = PointStore.resolveX(getContext(), point);
            float py = PointStore.resolveY(getContext(), point);
            float dx = x - px;
            float dy = y - py;
            if ((dx * dx + dy * dy) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    private void trimDebugHits() {
        int max = 50;
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
