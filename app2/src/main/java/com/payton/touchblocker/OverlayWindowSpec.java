package com.payton.touchblocker;

import android.view.WindowManager;

/**
 * Pure, platform-version-aware constants for building the overlay window's
 * {@link WindowManager.LayoutParams}. Every method takes the caller's {@code sdkInt} instead of
 * reading {@link android.os.Build.VERSION#SDK_INT} directly, so the version-dispatch logic is a
 * plain switch that can be exercised on the JVM without Robolectric.
 */
public final class OverlayWindowSpec {

    private OverlayWindowSpec() {
    }

    /** Overlay window type: {@code TYPE_APPLICATION_OVERLAY} from API 26, else {@code TYPE_PHONE}. */
    public static int windowType(int sdkInt) {
        if (sdkInt >= 26) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    /** Flags shared by every overlay window, regardless of platform version. */
    public static int windowFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    }

    /**
     * Cutout letterboxing mode: {@code ALWAYS} from API 30, {@code SHORT_EDGES} on 28-29,
     * else 0 (no cutout API available below API 28).
     */
    public static int cutoutMode(int sdkInt) {
        if (sdkInt >= 30) {
            return WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        if (sdkInt >= 28) {
            return WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return 0;
    }

    /** Whether the window should clear {@code setFitInsetsTypes(0)}, available from API 30. */
    public static boolean clearFitInsets(int sdkInt) {
        return sdkInt >= 30;
    }
}
