package com.payton.touchblocker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

/** Expands a small point list fully when it is hosted inside the manage screen's ScrollView. */
public final class ExpandedRecyclerView extends RecyclerView {
    private static final int MAX_EXPANDED_HEIGHT = 1 << 29;

    public ExpandedRecyclerView(Context context) {
        super(context);
    }

    public ExpandedRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ExpandedRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED) {
            heightSpec = View.MeasureSpec.makeMeasureSpec(
                    MAX_EXPANDED_HEIGHT, View.MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
