package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PreviewCircleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int diameterPx = 120;

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
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(0x88FF0000);
    }

    public void setDiameterPx(int diameterPx) {
        this.diameterPx = Math.max(30, diameterPx);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = diameterPx / 2f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, radius, paint);
    }
}

