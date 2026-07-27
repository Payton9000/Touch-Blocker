package com.payton.touchblocker;

import android.content.Context;
import android.content.SharedPreferences;
public class PointStore {
    private static final String PREFS_NAME = "touch_blocker_prefs";
    private static final String KEY_POINTS = "points_json";
    private static final String KEY_GLOBAL_SIZE = "global_size_px";
    private static final String KEY_DEBUG_OVERLAY = "debug_overlay";
    private static final String KEY_BOOT_RESTORE_ENABLED = "boot_restore_enabled";
    private static final String KEY_OVERLAY_SHOULD_BE_ENABLED = "overlay_should_be_enabled";
    private static final String KEY_PENDING_BOOT_RESTORE_FAILURE = "pending_boot_restore_failure";
    private static final int DEFAULT_GLOBAL_SIZE_PX = 120;

    public static int getGlobalSizePx(Context context) {
        return getPrefs(context).getInt(KEY_GLOBAL_SIZE, DEFAULT_GLOBAL_SIZE_PX);
    }

    public static void setGlobalSizePx(Context context, int sizePx) {
        getPrefs(context).edit().putInt(KEY_GLOBAL_SIZE, Math.max(30, sizePx)).apply();
    }

    public static boolean isDebugOverlayEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_DEBUG_OVERLAY, false);
    }

    public static void setDebugOverlayEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_DEBUG_OVERLAY, enabled).apply();
    }

    public static boolean isBootRestoreEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_BOOT_RESTORE_ENABLED, false);
    }

    public static void setBootRestoreEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_BOOT_RESTORE_ENABLED, enabled).apply();
    }

    public static boolean shouldOverlayBeEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_OVERLAY_SHOULD_BE_ENABLED, false);
    }

    public static void setOverlayShouldBeEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_OVERLAY_SHOULD_BE_ENABLED, enabled).apply();
    }

    public static BootRestoreDecision.FailureReason getPendingBootRestoreFailure(Context context) {
        String raw = getPrefs(context).getString(KEY_PENDING_BOOT_RESTORE_FAILURE, "");
        if (raw == null || raw.length() == 0) {
            return BootRestoreDecision.FailureReason.NONE;
        }
        try {
            return BootRestoreDecision.FailureReason.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return BootRestoreDecision.FailureReason.NONE;
        }
    }

    public static void setPendingBootRestoreFailure(Context context, BootRestoreDecision.FailureReason reason) {
        if (reason == null || reason == BootRestoreDecision.FailureReason.NONE) {
            clearPendingBootRestoreFailure(context);
            return;
        }
        getPrefs(context).edit().putString(KEY_PENDING_BOOT_RESTORE_FAILURE, reason.name()).apply();
    }

    public static void clearPendingBootRestoreFailure(Context context) {
        getPrefs(context).edit().remove(KEY_PENDING_BOOT_RESTORE_FAILURE).apply();
    }

    public static String getRawPointsJson(Context context) {
        return getPrefs(context).getString(KEY_POINTS, "[]");
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Package-visible accessor so other components (e.g. {@code OverlayService},
     * {@code BootCompletedReceiver}) can build a {@code ProfileRepository} backed by the exact
     * same SharedPreferences file this store uses, without duplicating the file name literal.
     */
    static SharedPreferences prefs(Context context) {
        return getPrefs(context);
    }

}
