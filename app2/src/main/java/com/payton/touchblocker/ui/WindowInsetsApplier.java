package com.payton.touchblocker.ui;

import android.view.View;

import com.payton.touchblocker.display.DisplayRegion;
import com.payton.touchblocker.display.EdgeInsets;
import com.payton.touchblocker.display.IntRect;

import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

public final class WindowInsetsApplier {
    private static final WeakHashMap<View, Padding> BASE_PADDING = new WeakHashMap<>();

    private WindowInsetsApplier() {
    }

    public static void applyContentContainerPadding(
            View contentContainer,
            EdgeInsets insets,
            IntRect windowBounds,
            List<DisplayRegion> safePanes
    ) {
        apply(contentContainer, insets, windowBounds, safePanes);
    }

    public static void applyTopPanelInsets(
            View topPanel,
            EdgeInsets insets,
            IntRect windowBounds,
            List<DisplayRegion> safePanes
    ) {
        apply(topPanel, insets, windowBounds, safePanes);
    }

    public static void applyContentContainerPadding(View contentContainer, EdgeInsets insets) {
        apply(contentContainer, insets, null, Collections.<DisplayRegion>emptyList());
    }

    public static void applyTopPanelInsets(View topPanel, EdgeInsets insets) {
        apply(topPanel, insets, null, Collections.<DisplayRegion>emptyList());
    }

    private static void apply(
            View view,
            EdgeInsets insets,
            IntRect windowBounds,
            List<DisplayRegion> safePanes
    ) {
        if (view == null) {
            throw new NullPointerException("view == null");
        }
        if (insets == null) {
            throw new NullPointerException("insets == null");
        }
        Padding base = basePadding(view);
        int left = Math.max(0, insets.getLeft());
        int top = Math.max(0, insets.getTop());
        int right = Math.max(0, insets.getRight());
        int bottom = Math.max(0, insets.getBottom());
        DisplayRegion pane = largestPane(safePanes);
        if (pane != null && windowBounds != null) {
            IntRect paneBounds = pane.getBounds();
            left = Math.max(left, paneBounds.getLeft() - windowBounds.getLeft());
            top = Math.max(top, paneBounds.getTop() - windowBounds.getTop());
            right = Math.max(right, windowBounds.getRight() - paneBounds.getRight());
            bottom = Math.max(bottom, windowBounds.getBottom() - paneBounds.getBottom());
        }
        view.setPadding(
                base.left + left,
                base.top + top,
                base.right + right,
                base.bottom + bottom);
    }

    private static synchronized Padding basePadding(View view) {
        Padding padding = BASE_PADDING.get(view);
        if (padding == null) {
            padding = new Padding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    view.getPaddingBottom());
            BASE_PADDING.put(view, padding);
        }
        return padding;
    }

    private static DisplayRegion largestPane(List<DisplayRegion> panes) {
        if (panes == null || panes.isEmpty()) {
            return null;
        }
        DisplayRegion largest = null;
        long largestArea = -1L;
        for (DisplayRegion pane : panes) {
            if (pane == null) {
                continue;
            }
            IntRect bounds = pane.getBounds();
            long area = Math.max(0, bounds.width()) * (long) Math.max(0, bounds.height());
            if (area > largestArea) {
                largest = pane;
                largestArea = area;
            }
        }
        return largest;
    }

    private static final class Padding {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Padding(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
}
