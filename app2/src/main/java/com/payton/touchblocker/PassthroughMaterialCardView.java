package com.payton.touchblocker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.android.material.card.MaterialCardView;

/** A purely visual card that never consumes touches intended for the surface below it. */
public final class PassthroughMaterialCardView extends MaterialCardView {
    public PassthroughMaterialCardView(Context context) {
        super(context);
    }

    public PassthroughMaterialCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PassthroughMaterialCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }
}
