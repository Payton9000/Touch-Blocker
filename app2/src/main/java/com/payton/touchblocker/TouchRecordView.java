package com.payton.touchblocker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/** Draws recording feedback from physical display coordinates. */
public class TouchRecordView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RecordingFeedback feedback = new RecordingFeedback();
    private final int[] locationOnScreen = new int[2];

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
        paint.setColor(0xFFFF0000);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Each entry must contain {screenX, screenY, radiusPx}. */
    public void setResolved(List<float[]> resolvedCentersAndRadiiPx) {
        feedback.replace(resolvedCentersAndRadiiPx, SystemClock.uptimeMillis());
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        getLocationOnScreen(locationOnScreen);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long nowMs = SystemClock.uptimeMillis();
        int alpha = feedback.alphaAt(nowMs);
        if (alpha == 0) {
            return;
        }
        paint.setAlpha(alpha);
        for (float[] value : feedback.markers(nowMs)) {
            canvas.drawCircle(value[0] - locationOnScreen[0], value[1] - locationOnScreen[1],
                    value[2], paint);
        }
        postInvalidateOnAnimation();
    }
}
