package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class TouchRecordView extends View {
    private static final float DOT_RADIUS = 8f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<TouchPoint> points = new ArrayList<>();

    public TouchRecordView(Context context) {
        super(context);
        init();
    }

    public TouchRecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TouchRecordView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setColor(0xCCFF0000);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xFF000000);
        textPaint.setTextSize(24f);
    }

    public void addPoint(TouchPoint point) {
        points.add(point);
        invalidate();
    }

    public void setPoints(List<TouchPoint> newPoints) {
        points.clear();
        if (newPoints != null) {
            points.addAll(newPoints);
        }
        invalidate();
    }

    public Bounds getBounds() {
        if (points.isEmpty()) {
            return null;
        }
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = 0f;
        float maxY = 0f;
        for (TouchPoint point : points) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (TouchPoint point : points) {
            canvas.drawCircle(point.getX(), point.getY(), DOT_RADIUS, paint);
            canvas.drawText(String.valueOf(point.getId()), point.getX() + 10f, point.getY() - 10f, textPaint);
        }
    }

    public static class Bounds {
        public final float minX;
        public final float minY;
        public final float maxX;
        public final float maxY;

        public Bounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }
}
