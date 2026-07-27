package com.payton.touchblocker;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowInsets;

import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.CutoutInsetsCache;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.IntRect;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.geometry.OverlayClusterPlanner;
import com.payton.touchblocker.geometry.OverlayRefreshPlanner;
import com.payton.touchblocker.profile.ActiveProfiles;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ProfileRevalidator;
import com.payton.touchblocker.profile.ScreenProfile;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OverlayService extends Service {
    public static final String ACTION_START_OVERLAY = "com.payton.touchblocker.action.START_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "com.payton.touchblocker.action.STOP_OVERLAY";
    public static final String ACTION_REFRESH_POINTS = "com.payton.touchblocker.action.REFRESH_POINTS";
    public static final String ACTION_SET_DEBUG = "com.payton.touchblocker.action.SET_DEBUG";

    public static final String EXTRA_DEBUG_ENABLED = "extra_debug_enabled";

    private static final String CHANNEL_ID = "touch_blocker_channel";
    private static final int NOTIFICATION_ID = 1002;
    private static final String TAG = "OverlayService";

    private WindowManager windowManager;
    private final Map<String, OverlayEntry> windowsByKey = new HashMap<>();
    private DisplayManager displayManager;
    private DisplaySnapshotProvider displaySnapshotProvider;
    private boolean overlayEnabled;
    private final OverlayRefreshState refreshState = new OverlayRefreshState();
    private Context foldWindowContext;
    private final WindowLayoutInfoObserver foldObserver = new WindowLayoutInfoObserver();
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
        displaySnapshotProvider = new AndroidDisplaySnapshotProvider(
                new DisplayGenerationTracker(), foldObserver);
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, null);
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayManager != null) {
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                foldWindowContext = createDisplayContext(display)
                        .createWindowContext(OverlayWindowSpec.windowType(Build.VERSION.SDK_INT), null);
                foldObserver.start(foldWindowContext, display.getDisplayId());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            DisplaySnapshot snapshot = captureDisplaySnapshot();
            ProfileRepository repository = new ProfileRepository(
                    new SharedPreferencesKeyValueStore(PointStore.prefs(this)),
                    new ProfileJsonCodec());
            boolean hasBlockablePoints = ProfileRestoreCheck.hasBlockablePoints(
                    repository, PointStore.getRawPointsJson(this), PointStore.getGlobalSizePx(this),
                    snapshot);
            if (!PointStore.shouldOverlayBeEnabled(this)
                    || !OverlayPermission.canDrawOverlays(this)
                    || !hasBlockablePoints) {
                overlayEnabled = false;
                stopSelf();
                return START_NOT_STICKY;
            }
            overlayEnabled = true;
            refreshOverlayPoints();
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
        foldObserver.stop();
        hideOverlay();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (overlayEnabled) {
            refreshOverlayPoints();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void refreshOverlayPoints() {
        Log.d(TAG, "refreshOverlayPoints");
        DisplaySnapshot snapshot = captureDisplaySnapshot();
        if (snapshot == null) {
            hideOverlay();
            return;
        }
        ProfileRepository repository = new ProfileRepository(
                new SharedPreferencesKeyValueStore(PointStore.prefs(this)),
                new ProfileJsonCodec());
        ProfileRepository.LoadResult loaded = repository.migrateIfNeeded(
                PointStore.getRawPointsJson(this), PointStore.getGlobalSizePx(this),
                snapshot, snapshot.getSuggestedKind() == null
                        ? ProfileKind.OUTER : snapshot.getSuggestedKind());
        if (!loaded.isSuccess()) {
            hideOverlay();
            return;
        }
        ActiveProfiles.Active active = ActiveProfiles.ensure(loaded.getDocument(), snapshot);
        if (active == null || active.isCreated()) {
            // AMBIGUOUS or brand-new empty profile -> nothing to block.
            if (active != null && active.isCreated()) {
                repository.save(active.getDocument());
            }
            hideOverlay();
            return;
        }
        ScreenProfile profile = active.getDocument().getProfiles().get(active.getProfileId());
        ScreenProfile revalidated = new ProfileRevalidator().revalidate(profile, snapshot);
        if (revalidated != profile) {
            // Clears stale HINGE/CUTOUT disabled bits (spec Section 5) and records new
            // OUT_OF_BOUNDS points.
            repository.save(active.getDocument().withProfile(revalidated));
            profile = revalidated;
        }
        boolean debugEnabled = PointStore.isDebugOverlayEnabled(this);
        String stamp = snapshot.getStableKey() + '|' + snapshot.getGeneration()
                + '|' + repository.getRevision() + '|' + debugEnabled;
        if (refreshState.shouldSkip(stamp, windowsByKey.keySet())) {
            return;
        }
        refreshState.recordRefreshAttempt(stamp);
        applyWindowPlans(OverlayRefreshPlanner.plan(snapshot, profile), debugEnabled);
    }

    private void applyWindowPlans(List<OverlayClusterPlanner.WindowPlan> plans, boolean debugEnabled) {
        Set<String> wanted = new HashSet<>();
        int failures = 0;
        for (OverlayClusterPlanner.WindowPlan plan : plans) {
            wanted.add(plan.key());
            OverlayEntry entry = windowsByKey.get(plan.key());
            if (entry != null) {
                entry.params.x = plan.getBounds().getLeft();
                entry.params.y = plan.getBounds().getTop();
                entry.params.width = plan.getBounds().width();
                entry.params.height = plan.getBounds().height();
                entry.view.setCircles(plan.getMembers(), plan.getBounds());
                entry.view.setDebugEnabled(debugEnabled);
                try {
                    windowManager.updateViewLayout(entry.view, entry.params);
                } catch (RuntimeException failure) {
                    Log.w(TAG, "Unable to update overlay window; removing", failure);
                    removeEntry(plan.key(), entry);
                    failures++;
                }
                continue;
            }
            if (!addWindow(plan, debugEnabled)) {
                failures++;
            }
        }
        for (String key : new ArrayList<>(windowsByKey.keySet())) {
            if (!wanted.contains(key)) {
                removeEntry(key, windowsByKey.get(key));
            }
        }
        if (!plans.isEmpty() && failures == plans.size()) {
            failOverlaySetup();
        }
        refreshState.recordAppliedWindows(wanted, windowsByKey.keySet());
    }

    /**
     * Builds a window for a brand-new plan and adds it via {@link #windowManager}, retrying
     * {@code addView} once on {@link RuntimeException} before reporting failure. Only called when
     * {@link #windowsByKey} has no existing entry for {@code plan.key()}, so a successful add here
     * is always this window's first, and {@link PointOverlayView#startFadeOut()} is fired
     * accordingly (unless debug mode is on).
     */
    @SuppressLint("NewApi")
    private boolean addWindow(OverlayClusterPlanner.WindowPlan plan, boolean debugEnabled) {
        IntRect bounds = plan.getBounds();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                bounds.width(),
                bounds.height(),
                OverlayWindowSpec.windowType(Build.VERSION.SDK_INT),
                OverlayWindowSpec.windowFlags(),
                PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = OverlayWindowSpec.cutoutMode(Build.VERSION.SDK_INT);
        }
        if (OverlayWindowSpec.clearFitInsets(Build.VERSION.SDK_INT)) {
            params.setFitInsetsTypes(0);
        }
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.x = bounds.getLeft();
        params.y = bounds.getTop();

        PointOverlayView view = new PointOverlayView(this);
        view.setCircles(plan.getMembers(), bounds);
        view.setDebugEnabled(debugEnabled);

        boolean added = false;
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 2 && !added; attempt++) {
            try {
                windowManager.addView(view, params);
                added = true;
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        if (!added) {
            Log.e(TAG, "Unable to add overlay window after retry", lastFailure);
            return false;
        }
        cacheCutoutInsets(view);
        windowsByKey.put(plan.key(), new OverlayEntry(view, params));
        if (!debugEnabled) {
            view.startFadeOut();
        }
        return true;
    }

    @SuppressLint("NewApi")
    private static void cacheCutoutInsets(PointOverlayView view) {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P
                && Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            return;
        }
        Display display = view.getDisplay();
        if (display == null) {
            return;
        }
        final int displayId = display.getDisplayId();
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View ignored, WindowInsets insets) {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                List<IntRect> cutoutBounds = new ArrayList<>();
                if (cutout != null) {
                    for (Rect rect : cutout.getBoundingRects()) {
                        cutoutBounds.add(new IntRect(
                                rect.left, rect.top, rect.right, rect.bottom));
                    }
                }
                CutoutInsetsCache.instance().update(displayId, cutoutBounds);
                return insets;
            }
        });
        view.requestApplyInsets();
    }

    private void removeEntry(String key, OverlayEntry entry) {
        if (entry == null) {
            return;
        }
        entry.view.dispose();
        try {
            windowManager.removeView(entry.view);
        } catch (RuntimeException failure) {
            // The system may have removed the window after a permission change.
            Log.w(TAG, "Overlay window was already removed", failure);
        }
        windowsByKey.remove(key);
    }

    private void hideOverlay() {
        Log.d(TAG, "hideOverlay");
        for (OverlayEntry entry : windowsByKey.values()) {
            entry.view.dispose();
            try {
                windowManager.removeView(entry.view);
            } catch (RuntimeException failure) {
                // The system may have removed the window after a permission change.
                Log.w(TAG, "Overlay window was already removed", failure);
            }
        }
        windowsByKey.clear();
    }

    private void failOverlaySetup() {
        overlayEnabled = false;
        hideOverlay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private DisplaySnapshot captureDisplaySnapshot() {
        if (displaySnapshotProvider == null) {
            return null;
        }
        int displayId = Display.DEFAULT_DISPLAY;
        if (displayManager != null) {
            Display display = displayManager.getDisplay(displayId);
            if (display != null) {
                displayId = display.getDisplayId();
            }
        }
        try {
            return displaySnapshotProvider.capture(this, displayId);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to capture display snapshot; keeping legacy coordinates", failure);
            return null;
        }
    }

    private void applyDebugToOverlays(boolean enabled) {
        for (OverlayEntry entry : windowsByKey.values()) {
            entry.view.setDebugEnabled(enabled);
            if (!enabled) {
                entry.view.startFadeOut();
            }
        }
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
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_overlay_running))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
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

        private OverlayEntry(PointOverlayView view, WindowManager.LayoutParams params) {
            this.view = view;
            this.params = params;
        }
    }
}
