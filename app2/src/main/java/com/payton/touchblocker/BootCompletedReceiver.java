package com.payton.touchblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                PointStore.isBootRestoreEnabled(context),
                PointStore.shouldOverlayBeEnabled(context),
                OverlayPermission.canDrawOverlays(context),
                PointStore.hasEnabledPoints(context)
        );

        if (decision.shouldStart()) {
            PointStore.clearPendingBootRestoreFailure(context);
            Log.d(TAG, "Restoring overlay after boot");
            startOverlayService(context, OverlayService.ACTION_START_OVERLAY);
            return;
        }

        BootRestoreDecision.FailureReason reason = decision.getFailureReason();
        if (reason == BootRestoreDecision.FailureReason.MISSING_PERMISSION
                || reason == BootRestoreDecision.FailureReason.NO_ENABLED_POINTS) {
            PointStore.setPendingBootRestoreFailure(context, reason);
            Log.d(TAG, "Skip restore after boot: " + reason);
        } else {
            PointStore.clearPendingBootRestoreFailure(context);
        }
    }

    private void startOverlayService(Context context, String action) {
        Intent serviceIntent = new Intent(context, OverlayService.class);
        serviceIntent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
