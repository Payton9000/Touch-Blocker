package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** A density-aware circle sample that always remains inside its view bounds. */
public class PreviewCircleView extends View {
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float diameterDp = 48f;

    public PreviewCircleView(Context context) {
        super(context);
        init();
    }

    public PreviewCircleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PreviewCircleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(4f);
        circlePaint.setColor(0x88FF0000);
        labelPaint.setColor(0xFF5F2120);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 12f);
    }

    public void setDiameterDp(float diameterDp) {
        this.diameterDp = Math.max(0f, diameterDp);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float requestedRadius = (diameterDp * density) / 2f;
        float maxRadius = Math.max(0f, Math.min(getWidth(), getHeight()) / 2f
                - circlePaint.getStrokeWidth() / 2f);
        float radius = Math.min(requestedRadius, maxRadius);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, radius, circlePaint);
        Paint.FontMetrics metrics = labelPaint.getFontMetrics();
        float baseline = cy - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(Math.round(diameterDp) + " dp", cx, baseline, labelPaint);
    }
}
