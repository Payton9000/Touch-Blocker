package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.payton.touchblocker.geometry.OverlayClusterPlanner;

import java.util.ArrayList;
import java.util.List;

/** Draws and tests the exact rectangular touch windows used by the overlay service. */
public class TestBlockView extends View {
    private static final int MAX_HIT_HISTORY = 50;

    private final Paint planFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint planStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockedMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint passedMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<OverlayClusterPlanner.WindowPlan> plans = new ArrayList<>();
    private final List<TestHit> hits = new ArrayList<>();
    private final int[] windowLocation = new int[2];

    private int touchSlop;
    private float downX;
    private float downY;
    private boolean movedBeyondSlop;
    private int windowOffsetX;
    private int windowOffsetY;
    private int blockedCount;
    private int passedCount;
    private OnStatsChangedListener onStatsChangedListener;

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
        float density = context.getResources().getDisplayMetrics().density;
        int primary = resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary);
        int error = resolveThemeColor(context, com.google.android.material.R.attr.colorError);

        planFillPaint.setStyle(Paint.Style.FILL);
        planFillPaint.setColor(withAlpha(primary, 41));
        planStrokePaint.setStyle(Paint.Style.STROKE);
        planStrokePaint.setStrokeWidth(2f * density);
        planStrokePaint.setColor(primary);
        planStrokePaint.setPathEffect(new DashPathEffect(
                new float[]{6f * density, 4f * density}, 0f));
        blockedMarkerPaint.setStyle(Paint.Style.STROKE);
        blockedMarkerPaint.setStrokeCap(Paint.Cap.ROUND);
        blockedMarkerPaint.setStrokeWidth(3f * density);
        blockedMarkerPaint.setColor(0xFF2E7D32);
        passedMarkerPaint.setStyle(Paint.Style.STROKE);
        passedMarkerPaint.setStrokeCap(Paint.Cap.ROUND);
        passedMarkerPaint.setStrokeWidth(3f * density);
        passedMarkerPaint.setColor(error);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setPlans(List<OverlayClusterPlanner.WindowPlan> newPlans) {
        plans.clear();
        if (newPlans != null) {
            plans.addAll(newPlans);
        }
        invalidate();
    }

    public void setOnStatsChangedListener(OnStatsChangedListener listener) {
        onStatsChangedListener = listener;
        notifyStatsChanged();
    }

    public void clearResults() {
        hits.clear();
        blockedCount = 0;
        passedCount = 0;
        notifyStatsChanged();
        invalidate();
    }

    static boolean isBlockedByPlans(
            List<OverlayClusterPlanner.WindowPlan> plans,
            float screenX,
            float screenY
    ) {
        for (OverlayClusterPlanner.WindowPlan plan : plans) {
            com.payton.touchblocker.display.IntRect bounds = plan.getBounds();
            if (bounds.getLeft() <= screenX && screenX < bounds.getRight()
                    && bounds.getTop() <= screenY && screenY < bounds.getBottom()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateWindowOffset();
        float cornerRadius = 12f * getResources().getDisplayMetrics().density;
        for (OverlayClusterPlanner.WindowPlan plan : plans) {
            com.payton.touchblocker.display.IntRect bounds = plan.getBounds();
            float left = toLocalX(bounds.getLeft());
            float top = toLocalY(bounds.getTop());
            float right = toLocalX(bounds.getRight());
            float bottom = toLocalY(bounds.getBottom());
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, planFillPaint);
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, planStrokePaint);
        }
        for (TestHit hit : hits) {
            drawHitMarker(canvas, hit);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            movedBeyondSlop = false;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(event.getX() - downX) > touchSlop
                    || Math.abs(event.getY() - downY) > touchSlop) {
                movedBeyondSlop = true;
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (!movedBeyondSlop) {
                updateWindowOffset();
                float screenX = toScreenX(downX);
                float screenY = toScreenY(downY);
                boolean blocked = isBlockedByPlans(plans, screenX, screenY);
                performClick();
                announceForAccessibility(getContext().getString(
                        blocked ? R.string.a11y_blocked : R.string.a11y_passed));
                addHit(screenX, screenY, blocked);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void addHit(float screenX, float screenY, boolean blocked) {
        if (hits.size() == MAX_HIT_HISTORY) {
            hits.remove(0);
        }
        hits.add(new TestHit(screenX, screenY, blocked));
        if (blocked) {
            blockedCount++;
        } else {
            passedCount++;
        }
        notifyStatsChanged();
        invalidate();
    }

    private void drawHitMarker(Canvas canvas, TestHit hit) {
        float x = toLocalX(hit.screenX);
        float y = toLocalY(hit.screenY);
        float radius = 8f * getResources().getDisplayMetrics().density;
        if (hit.blocked) {
            canvas.drawLine(x - radius, y, x - radius / 4f, y + radius, blockedMarkerPaint);
            canvas.drawLine(x - radius / 4f, y + radius, x + radius, y - radius, blockedMarkerPaint);
        } else {
            canvas.drawLine(x - radius, y - radius, x + radius, y + radius, passedMarkerPaint);
            canvas.drawLine(x + radius, y - radius, x - radius, y + radius, passedMarkerPaint);
        }
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

    private float toScreenX(float localX) {
        return localX + windowOffsetX;
    }

    private float toScreenY(float localY) {
        return localY + windowOffsetY;
    }

    private void notifyStatsChanged() {
        if (onStatsChangedListener != null) {
            onStatsChangedListener.onStatsChanged(blockedCount, passedCount);
        }
    }

    private static int resolveThemeColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            return 0;
        }
        return value.data;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public interface OnStatsChangedListener {
        void onStatsChanged(int blocked, int passed);
    }

    private static final class TestHit {
        private final float screenX;
        private final float screenY;
        private final boolean blocked;

        private TestHit(float screenX, float screenY, boolean blocked) {
            this.screenX = screenX;
            this.screenY = screenY;
            this.blocked = blocked;
        }
    }
}
