package com.payton.touchblocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    public static final String ACTION_START_OVERLAY = "com.payton.touchblocker.action.START_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "com.payton.touchblocker.action.STOP_OVERLAY";
    public static final String ACTION_REFRESH_POINTS = "com.payton.touchblocker.action.REFRESH_POINTS";
    public static final String ACTION_SET_DEBUG = "com.payton.touchblocker.action.SET_DEBUG";

    public static final String EXTRA_WIDTH = "extra_width";
    public static final String EXTRA_HEIGHT = "extra_height";
    public static final String EXTRA_DEBUG_ENABLED = "extra_debug_enabled";

    private static final String CHANNEL_ID = "touch_blocker_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final String TAG = "OverlayService";

    private WindowManager windowManager;
    private final List<OverlayEntry> overlays = new ArrayList<>();
    private DisplayManager displayManager;
    private boolean overlayEnabled;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (overlayEnabled) {
                refreshOverlayPoints();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, null);
        }
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action=" + action);
        if (ACTION_START_OVERLAY.equals(action)) {
            overlayEnabled = true;
            refreshOverlayPoints();
        } else if (ACTION_STOP_OVERLAY.equals(action)) {
            overlayEnabled = false;
            hideOverlay();
        } else if (ACTION_REFRESH_POINTS.equals(action)) {
            overlayEnabled = true;
            refreshOverlayPoints();
        } else if (ACTION_SET_DEBUG.equals(action)) {
            boolean enabled = intent.getBooleanExtra(EXTRA_DEBUG_ENABLED, false);
            Log.d(TAG, "set debug=" + enabled);
            PointStore.setDebugOverlayEnabled(this, enabled);
            applyDebugToOverlays(enabled);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        hideOverlay();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showOverlay() {
        Log.d(TAG, "showOverlay");
        boolean debugEnabled = PointStore.isDebugOverlayEnabled(this);
        for (OverlayEntry entry : overlays) {
            if (entry.added) {
                continue;
            }
            entry.view.setDebugEnabled(debugEnabled);
            windowManager.addView(entry.view, entry.params);
            if (!debugEnabled) {
                entry.view.startFadeOut();
            }
            entry.added = true;
        }
    }

    private void hideOverlay() {
        Log.d(TAG, "hideOverlay");
        for (OverlayEntry entry : overlays) {
            if (entry.added) {
                windowManager.removeView(entry.view);
                entry.added = false;
            }
        }
        overlays.clear();
    }

    private void refreshOverlayPoints() {
        Log.d(TAG, "refreshOverlayPoints");
        hideOverlay();
        List<TouchPoint> points = PointStore.loadPoints(this);
        int globalSize = PointStore.getGlobalSizePx(this);
        boolean debugEnabled = PointStore.isDebugOverlayEnabled(this);
        for (TouchPoint point : points) {
            if (!point.isEnabled()) {
                continue;
            }
            int size = point.getSizeOverridePx() > 0 ? point.getSizeOverridePx() : globalSize;
            PointOverlayView view = new PointOverlayView(this);
            view.setDiameterPx(size);
            view.setPointId(point.getId());
            view.setDebugEnabled(debugEnabled);

            WindowManager.LayoutParams params = buildParamsForPoint(point, size);
            overlays.add(new OverlayEntry(view, params));
        }
        showOverlay();
    }

    private void applyDebugToOverlays(boolean enabled) {
        for (OverlayEntry entry : overlays) {
            entry.view.setDebugEnabled(enabled);
            if (!enabled) {
                entry.view.startFadeOut();
            }
        }
    }

    private WindowManager.LayoutParams buildParamsForPoint(TouchPoint point, int sizePx) {
        int windowType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            windowType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        int half = sizePx / 2;
        float px = PointStore.resolveX(this, point);
        float py = PointStore.resolveY(this, point);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx,
                sizePx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Keep overlay anchored to the physical display, including cutout areas.
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.round(px - half);
        params.y = Math.round(py - half);
        Log.d(TAG, "buildParams pointId=" + point.getId()
                + " resolved=(" + px + "," + py + ")"
                + " windowXY=(" + params.x + "," + params.y + ")"
                + " size=" + sizePx);
        return params;
    }

    private Notification buildNotification() {
        createNotificationChannel();
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Touch Blocker")
                .setContentText("Overlay running")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Touch Blocker",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private static class OverlayEntry {
        private final PointOverlayView view;
        private final WindowManager.LayoutParams params;
        private boolean added;

        private OverlayEntry(PointOverlayView view, WindowManager.LayoutParams params) {
            this.view = view;
            this.params = params;
        }
    }
}
