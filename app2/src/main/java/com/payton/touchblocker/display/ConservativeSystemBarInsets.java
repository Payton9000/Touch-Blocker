package com.payton.touchblocker.display;

public final class ConservativeSystemBarInsets {
    private static final int DEFAULT_STATUS_BAR_DP = 24;
    private static final int DEFAULT_SIDE_GESTURE_DP = 24;
    private static final int DEFAULT_BOTTOM_BAR_DP = 48;

    private ConservativeSystemBarInsets() {
    }

    public static EdgeInsets create(
            int statusBarHeight,
            int navigationBarHeight,
            int navigationBarWidth,
            int sideGestureInset,
            int bottomGestureInset,
            int densityDpi
    ) {
        int effectiveDensityDpi = densityDpi > 0 ? densityDpi : 160;
        int top = Math.max(
                Math.max(0, statusBarHeight),
                dpToPx(DEFAULT_STATUS_BAR_DP, effectiveDensityDpi));
        int side = max(
                Math.max(0, navigationBarWidth),
                Math.max(0, sideGestureInset),
                dpToPx(DEFAULT_SIDE_GESTURE_DP, effectiveDensityDpi));
        int bottom = max(
                Math.max(0, navigationBarHeight),
                Math.max(0, bottomGestureInset),
                dpToPx(DEFAULT_BOTTOM_BAR_DP, effectiveDensityDpi));
        return new EdgeInsets(side, top, side, bottom);
    }

    private static int dpToPx(int dp, int densityDpi) {
        return (int) Math.ceil(dp * (densityDpi / 160f));
    }

    private static int max(int first, int second, int third) {
        return Math.max(first, Math.max(second, third));
    }
}
