package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

public class TestBlockView extends View {
    private final Paint areaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint missPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<TouchPoint> blockPoints = new ArrayList<>();
    private final List<TestHit> hits = new ArrayList<>();
    private int globalSizePx = 120;
    private static final String TAG = "TestBlockView";
    private int touchSlop;
    private float downX;
    private float downY;
    private boolean trackingBlocked;
    private boolean movedBeyondSlop;

    private boolean debugOverlayEnabled;
    private final int[] windowLocation = new int[2];
    private int windowOffsetX;
    private int windowOffsetY;

    public TestBlockView(Context context) {
        super(context);
        init(context);
    }

    public TestBlockView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TestBlockView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        areaPaint.setStyle(Paint.Style.STROKE);
        areaPaint.setStrokeWidth(4f);
        areaPaint.setColor(0x88FF0000);
        hitPaint.setStyle(Paint.Style.FILL);
        hitPaint.setColor(0x8800CC00);
        missPaint.setStyle(Paint.Style.FILL);
        missPaint.setColor(0x88FF0000);
        textPaint.setTextSize(24f);
        textPaint.setColor(0xFF000000);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setBlockPoints(List<TouchPoint> points) {
        blockPoints.clear();
        if (points != null) {
            blockPoints.addAll(points);
        }
        invalidate();
    }

    public void setGlobalSizePx(int sizePx) {
        globalSizePx = Math.max(30, sizePx);
        invalidate();
    }

    public void setDebugOverlayEnabled(boolean enabled) {
        if (debugOverlayEnabled != enabled) {
            debugOverlayEnabled = enabled;
            invalidate();
        }
    }

    public boolean isDebugOverlayEnabled() {
        return debugOverlayEnabled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateWindowOffset();
        if (debugOverlayEnabled) {
            for (TouchPoint point : blockPoints) {
                if (!point.isEnabled()) {
                    continue;
                }
                int size = point.getSizeOverridePx() > 0 ? point.getSizeOverridePx() : globalSizePx;
                float radius = size / 2f;
                float px = toLocalX(PointStore.resolveX(getContext(), point));
                float py = toLocalY(PointStore.resolveY(getContext(), point));
                canvas.drawCircle(px, py, radius, areaPaint);
            }
        }
        for (TestHit hit : hits) {
            Paint paint = hit.blocked ? hitPaint : missPaint;
            TouchPoint resolved = hit.toTouchPoint();
            float lx = toLocalX(PointStore.resolveX(getContext(), resolved));
            float ly = toLocalY(PointStore.resolveY(getContext(), resolved));
            canvas.drawCircle(lx, ly, 10f, paint);
            canvas.drawText(String.valueOf(hit.index), lx + 12f, ly - 12f, textPaint);
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
                boolean blocked = isBlocked(downX, downY);
                TestHit hit = buildHit(downX, downY, blocked);
                hits.add(hit);
                Log.d(TAG, "tap x=" + downX + " y=" + downY + " blocked=" + blocked);
                invalidate();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            return true;
        }

        return true;
    }

    private boolean isBlocked(float x, float y) {
        for (TouchPoint point : blockPoints) {
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

    private void updateWindowOffset() {
        getLocationOnScreen(windowLocation);
        windowOffsetX = windowLocation[0];
        windowOffsetY = windowLocation[1];
    }

    private float toLocalX(float screenX) {
        return screenX - windowOffsetX;
    }

    private float toLocalY(float screenY) {
        return screenY - windowOffsetY;
    }

    private TestHit buildHit(float x, float y, boolean blocked) {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        android.view.WindowManager wm = (android.view.WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        float nx = metrics.widthPixels > 0 ? x / metrics.widthPixels : -1f;
        float ny = metrics.heightPixels > 0 ? y / metrics.heightPixels : -1f;
        int rotation = wm != null ? wm.getDefaultDisplay().getRotation() : android.view.Surface.ROTATION_0;
        return new TestHit(hits.size() + 1, x, y, blocked, nx, ny, rotation, metrics.widthPixels, metrics.heightPixels);
    }

    private static class TestHit {
        private final int index;
        private final float x;
        private final float y;
        private final boolean blocked;
        private final float normalizedX;
        private final float normalizedY;
        private final int baseRotation;
        private final int baseWidthPx;
        private final int baseHeightPx;

        private TestHit(int index, float x, float y, boolean blocked,
                        float normalizedX, float normalizedY,
                        int baseRotation, int baseWidthPx, int baseHeightPx) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.blocked = blocked;
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.baseRotation = baseRotation;
            this.baseWidthPx = baseWidthPx;
            this.baseHeightPx = baseHeightPx;
        }

        private TouchPoint toTouchPoint() {
            TouchPoint point = new TouchPoint(-1, x, y, 0L, 0L);
            point.setNormalizedX(normalizedX);
            point.setNormalizedY(normalizedY);
            point.setBaseRotation(baseRotation);
            point.setBaseWidthPx(baseWidthPx);
            point.setBaseHeightPx(baseHeightPx);
            return point;
        }
    }
}
