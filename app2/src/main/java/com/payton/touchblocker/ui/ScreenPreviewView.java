package com.payton.touchblocker.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.payton.touchblocker.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Draws the normalized primitives supplied by {@link PreviewModelFactory}. */
public final class ScreenPreviewView extends View {
    private final Paint outlinePaint;
    private final Paint cutoutPaint;
    private final Paint hingePaint;
    private final Paint enabledFillPaint;
    private final Paint enabledStrokePaint;
    private final Paint disabledPaint;
    private final RectF screenRect = new RectF();
    private final RectF itemRect = new RectF();
    private List<PreviewModelFactory.Item> items = Collections.emptyList();

    public ScreenPreviewView(Context context) {
        this(context, null);
    }

    public ScreenPreviewView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScreenPreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;
        float strokeWidth = 2f * density;
        DashPathEffect dash = new DashPathEffect(new float[]{6f * density, 4f * density}, 0f);

        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setColor(ContextCompat.getColor(context, R.color.preview_outline));
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(strokeWidth);

        cutoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cutoutPaint.setColor(ContextCompat.getColor(context, R.color.preview_cutout));
        cutoutPaint.setStyle(Paint.Style.FILL);

        hingePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        hingePaint.setColor(ContextCompat.getColor(context, R.color.preview_outline));
        hingePaint.setStyle(Paint.Style.STROKE);
        hingePaint.setStrokeWidth(strokeWidth);
        hingePaint.setPathEffect(dash);

        enabledFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enabledFillPaint.setColor(ContextCompat.getColor(context, R.color.preview_point));
        enabledFillPaint.setAlpha((int) (255f * 0.28f));
        enabledFillPaint.setStyle(Paint.Style.FILL);

        enabledStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enabledStrokePaint.setColor(ContextCompat.getColor(context, R.color.preview_point));
        enabledStrokePaint.setStyle(Paint.Style.STROKE);
        enabledStrokePaint.setStrokeWidth(strokeWidth);

        disabledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        disabledPaint.setColor(ContextCompat.getColor(context, R.color.preview_disabled));
        disabledPaint.setStyle(Paint.Style.STROKE);
        disabledPaint.setStrokeWidth(strokeWidth);
        disabledPaint.setPathEffect(dash);
    }

    public void setItems(List<PreviewModelFactory.Item> items) {
        if (items == null || items.isEmpty()) {
            this.items = Collections.emptyList();
        } else {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (items.isEmpty()) {
            return;
        }
        PreviewModelFactory.Item screen = items.get(0);
        float availableWidth = Math.max(0f, getWidth() - getPaddingLeft() - getPaddingRight());
        float availableHeight = Math.max(0f, getHeight() - getPaddingTop() - getPaddingBottom());
        float aspectRatio = screen.aspectRatio > 0f ? screen.aspectRatio : 1f;
        float screenWidth = Math.min(availableWidth, availableHeight * aspectRatio);
        float screenHeight = screenWidth / aspectRatio;
        if (screenHeight > availableHeight) {
            screenHeight = availableHeight;
            screenWidth = screenHeight * aspectRatio;
        }
        float left = getPaddingLeft() + (availableWidth - screenWidth) / 2f;
        float top = getPaddingTop() + (availableHeight - screenHeight) / 2f;
        screenRect.set(left, top, left + screenWidth, top + screenHeight);
        float cornerRadius = 10f * getResources().getDisplayMetrics().density;

        for (PreviewModelFactory.Item item : items) {
            map(item, screenRect, itemRect);
            switch (item.kind) {
                case PreviewModelFactory.KIND_SCREEN:
                    canvas.drawRoundRect(itemRect, cornerRadius, cornerRadius, outlinePaint);
                    break;
                case PreviewModelFactory.KIND_CUTOUT:
                    canvas.drawRoundRect(itemRect, cornerRadius, cornerRadius, cutoutPaint);
                    break;
                case PreviewModelFactory.KIND_HINGE:
                    canvas.drawRoundRect(itemRect, cornerRadius, cornerRadius, hingePaint);
                    break;
                case PreviewModelFactory.KIND_POINT_ENABLED:
                    drawPoint(canvas, itemRect, enabledFillPaint, enabledStrokePaint);
                    break;
                case PreviewModelFactory.KIND_POINT_DISABLED:
                    drawPoint(canvas, itemRect, null, disabledPaint);
                    break;
                default:
                    break;
            }
        }
    }

    private static void map(PreviewModelFactory.Item item, RectF screen, RectF output) {
        output.set(
                screen.left + item.left * screen.width(),
                screen.top + item.top * screen.height(),
                screen.left + item.right * screen.width(),
                screen.top + item.bottom * screen.height());
    }

    private static void drawPoint(Canvas canvas, RectF bounds, Paint fill, Paint stroke) {
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        if (fill != null) {
            canvas.drawCircle(cx, cy, radius, fill);
        }
        canvas.drawCircle(cx, cy, radius, stroke);
    }
}
