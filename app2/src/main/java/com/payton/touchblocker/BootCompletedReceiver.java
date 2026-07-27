package com.payton.touchblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !isRestoreTrigger(intent.getAction())) {
            return;
        }

        BootRestoreDecision decision = BootRestoreDecision.evaluate(
                PointStore.isBootRestoreEnabled(context),
                PointStore.shouldOverlayBeEnabled(context),
                OverlayPermission.canDrawOverlays(context),
                hasBlockablePoints(context)
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

    static boolean isRestoreTrigger(String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
    }

    private static boolean hasBlockablePoints(Context context) {
        DisplaySnapshotProvider displaySnapshotProvider = new AndroidDisplaySnapshotProvider(
                new DisplayGenerationTracker(), new WindowLayoutInfoObserver());
        DisplaySnapshot snapshot;
        try {
            snapshot = displaySnapshotProvider.capture(context, Display.DEFAULT_DISPLAY);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to capture display snapshot for boot restore check", failure);
            return false;
        }
        ProfileRepository repository = new ProfileRepository(
                new SharedPreferencesKeyValueStore(PointStore.prefs(context)),
                new ProfileJsonCodec());
        return ProfileRestoreCheck.hasBlockablePoints(
                repository, PointStore.getRawPointsJson(context), PointStore.getGlobalSizePx(context),
                snapshot);
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
