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

public class PointOverlayView extends View {
    private static final String TAG = "PointOverlayView";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint debugTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<DebugHit> debugHits = new ArrayList<>();
    private int overlayAlpha = 180;
    private int diameterPx = 120;
    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean movedBeyondSlop;
    private int pointId;
    private boolean debugEnabled;
    private int debugCounter = 1;
    private ValueAnimator fadeAnimator;

    public PointOverlayView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
        debugPaint.setStyle(Paint.Style.FILL);
        debugPaint.setColor(0x8800CC00);
        debugTextPaint.setColor(0xFF000000);
        debugTextPaint.setTextSize(20f);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setDiameterPx(int diameterPx) {
        this.diameterPx = Math.max(30, diameterPx);
        invalidate();
    }

    public void setPointId(int pointId) {
        this.pointId = pointId;
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

    private void stopFade() {
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = diameterPx / 2f;
        float cx = radius;
        float cy = radius;
        int colorCenter = (overlayAlpha << 24) | 0x00FF0000;
        int colorEdge = (Math.max(0, overlayAlpha - 120) << 24) | 0x00FF0000;
        RadialGradient gradient = new RadialGradient(
                cx,
                cy,
                radius,
                colorCenter,
                colorEdge,
                Shader.TileMode.CLAMP
        );
        paint.setShader(gradient);
        canvas.drawCircle(cx, cy, radius, paint);

        if (debugEnabled) {
            canvas.drawText("#" + pointId, cx - 6f, cy - 6f, debugTextPaint);
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
                Log.d(TAG, "blocked tap pointId=" + pointId + " x=" + downX + " y=" + downY);
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
