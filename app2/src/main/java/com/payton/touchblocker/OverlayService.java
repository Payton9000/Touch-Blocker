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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileFallback;
import com.payton.touchblocker.profile.LegacyRemigration;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Coalesces the burst of display callbacks a single rotation or fold produces. One rotation
     * delivers up to four separate triggers (display change, fold observer, configuration change),
     * and each refresh captures a display snapshot, migrates, re-validates every point and can
     * commit to disk synchronously. Running that burst back-to-back on the main thread stalled it
     * for close to a second, and a stalled main thread means the overlay windows stop consuming
     * touches -- the "rapid tapping stops blocking" symptom. Debouncing collapses the burst into
     * one refresh once the display has settled.
     */
    private final Runnable coalescedRefresh = new Runnable() {
        @Override
        public void run() {
            if (overlayEnabled) {
                refreshOverlayPoints(false);
            }
        }
    };
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            scheduleCoalescedRefresh();
        }
    };

    /** Debounce window for system-driven refreshes; long enough to absorb one rotation's burst. */
    private static final long REFRESH_DEBOUNCE_MS = 150L;

    private void scheduleCoalescedRefresh() {
        if (!overlayEnabled) {
            return;
        }
        mainHandler.removeCallbacks(coalescedRefresh);
        mainHandler.postDelayed(coalescedRefresh, REFRESH_DEBOUNCE_MS);
    }

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
        startOverlayForeground();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && displayManager != null) {
            Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display != null) {
                foldWindowContext = createDisplayContext(display)
                        .createWindowContext(OverlayWindowSpec.windowType(Build.VERSION.SDK_INT), null);
                foldObserver.start(foldWindowContext, display.getDisplayId(), new Runnable() {
                    @Override
                    public void run() {
                        // Rotation broadcasts can precede the corresponding WindowLayoutInfo
                        // update. Rebuild once the hinge has its current orientation.
                        scheduleCoalescedRefresh();
                    }
                });
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
            // A restart by the system is not a user action, so the blockers come back silently.
            refreshOverlayPoints(OverlayRevealPolicy.shouldRevealNewWindows(null));
            return START_STICKY;
        }
        String action = intent.getAction();
        Log.d(TAG, "onStartCommand action=" + action);
        if (ACTION_START_OVERLAY.equals(action) || ACTION_REFRESH_POINTS.equals(action)) {
            overlayEnabled = true;
            refreshOverlayPoints(OverlayRevealPolicy.shouldRevealNewWindows(action));
        } else if (ACTION_STOP_OVERLAY.equals(action)) {
            // Stopping means stopping: leaving the foreground service up kept an
            // undismissable "overlay running" notification in the shade forever, because the
            // notification is setOngoing(true) and nothing ever took it down.
            stopOverlayCompletely();
            return START_NOT_STICKY;
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
        mainHandler.removeCallbacks(coalescedRefresh);
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
        }
        foldObserver.stop();
        hideOverlay();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        scheduleCoalescedRefresh();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Rebuilds the overlay windows for the current display geometry and active profile.
     *
     * @param revealNewWindows whether windows created by this refresh should play the visible
     *     fade-out. Only a user-initiated start or point edit passes {@code true}; refreshes
     *     driven by the system (rotation, fold, configuration change, service restart) pass
     *     {@code false} so the blockers stay invisible instead of flashing red on every rotate.
     */
    private void refreshOverlayPoints(boolean revealNewWindows) {
        Log.d(TAG, "refreshOverlayPoints reveal=" + revealNewWindows);
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
        // One-time repair for profiles written by the first v2 migration, whose points had no
        // rotation anchor and therefore drifted across the glass on every screen rotation.
        if (LegacyRemigration.repairIfNeeded(
                repository,
                new SharedPreferencesKeyValueStore(PointStore.prefs(this)),
                PointStore.getRawPointsJson(this),
                PointStore.getGlobalSizePx(this),
                snapshot)) {
            Log.d(TAG, "repaired rotation anchors from legacy backup");
            loaded = repository.load();
            if (!loaded.isSuccess()) {
                hideOverlay();
                return;
            }
        }
        ActiveProfiles.Active active = ActiveProfiles.ensure(loaded.getDocument(), snapshot);
        ProfileDocument document = active == null ? loaded.getDocument() : active.getDocument();
        String profileId = active == null ? null : active.getProfileId();
        if (active != null && active.isCreated()) {
            repository.save(active.getDocument());
        }
        // Fall back whenever the selected profile cannot block anything -- either no profile was
        // selected at all, or the one selected for this display is empty. Both happen for reasons
        // the user never asked for: folding, or changing the system display-size setting, each of
        // which changes the display fingerprint. Tearing every window down here while still
        // reporting the overlay as ON is what made blocking silently stop working.
        if (!hasEnabledPoints(document, profileId)) {
            String fallbackId = ProfileFallback.selectFallbackProfileId(document, snapshot);
            if (fallbackId == null) {
                // Genuinely nothing to block anywhere: no stored profile has enabled points.
                hideOverlay();
                return;
            }
            Log.d(TAG, "profile " + profileId + " has nothing to block; using " + fallbackId);
            profileId = fallbackId;
        }
        ScreenProfile profile = document.getProfiles().get(profileId);
        if (profile == null) {
            hideOverlay();
            return;
        }
        ScreenProfile revalidated = new ProfileRevalidator().revalidate(profile, snapshot);
        if (revalidated != profile) {
            // Clears stale HINGE/CUTOUT disabled bits (spec Section 5) and records new
            // OUT_OF_BOUNDS points.
            repository.save(document.withProfile(revalidated));
            profile = revalidated;
        }
        boolean debugEnabled = PointStore.isDebugOverlayEnabled(this);
        String stamp = snapshot.getStableKey() + '|' + snapshot.getGeneration()
                + '|' + repository.getRevision() + '|' + debugEnabled
                + '|' + profile.getId();
        if (refreshState.shouldSkip(stamp, windowsByKey.keySet())) {
            return;
        }
        refreshState.recordRefreshAttempt(stamp);
        applyWindowPlans(
                OverlayRefreshPlanner.plan(snapshot, profile), debugEnabled, revealNewWindows);
    }

    /** Whether {@code profileId} names a stored profile that has at least one enabled point. */
    private static boolean hasEnabledPoints(ProfileDocument document, String profileId) {
        if (profileId == null) {
            return false;
        }
        ScreenProfile profile = document.getProfiles().get(profileId);
        if (profile == null) {
            return false;
        }
        for (com.payton.touchblocker.profile.ProfilePoint point : profile.getPoints()) {
            if (point.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    private void applyWindowPlans(
            List<OverlayClusterPlanner.WindowPlan> plans,
            boolean debugEnabled,
            boolean revealNewWindows) {
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
            if (!addWindow(plan, debugEnabled, revealNewWindows)) {
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
     * is always this window's first. It then either plays the visible
     * {@link PointOverlayView#startFadeOut()} reveal or, when this refresh was triggered by the
     * system rather than the user, goes straight to
     * {@link PointOverlayView#hideWithoutFade()} (unless debug mode is on).
     */
    @SuppressLint("NewApi")
    private boolean addWindow(
            OverlayClusterPlanner.WindowPlan plan,
            boolean debugEnabled,
            boolean revealNewWindows) {
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
        if (debugEnabled) {
            view.setDebugEnabled(true);
        }

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
            if (revealNewWindows) {
                view.startFadeOut();
            } else {
                view.hideWithoutFade();
            }
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

    /**
     * Enters the foreground, passing the service type on Android 14+.
     *
     * <p>From API 34 {@code startForeground} throws unless the type it is given matches one the
     * manifest declares. The manifest declares {@code specialUse} (no typed category fits "block
     * touches at chosen coordinates"), so that type is supplied here. Passing it is harmless while
     * {@code targetSdk} is still 33 and prevents the eventual bump from becoming a crash.
     */
    @SuppressLint("InlinedApi")
    private void startOverlayForeground() {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            return;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    /**
     * Tears the overlay down and takes the foreground service and its notification with it.
     * Used both for a user-requested stop and for an unrecoverable setup failure.
     */
    private void stopOverlayCompletely() {
        overlayEnabled = false;
        mainHandler.removeCallbacks(coalescedRefresh);
        hideOverlay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    /**
     * Every overlay window failed to attach, so blocking cannot work. Clearing the persisted
     * "should be enabled" flag is the important part: without it the UI kept showing the overlay
     * as ON after the service had given up, so the user believed their screen was protected when
     * nothing was intercepting touches.
     */
    private void failOverlaySetup() {
        Log.w(TAG, "overlay setup failed; disabling and reporting to the UI");
        PointStore.setOverlayShouldBeEnabled(this, false);
        // Deliberately not MISSING_PERMISSION: refreshUiState() clears that reason whenever the
        // permission is in fact granted, which would erase this report and leave the UI silent.
        PointStore.setPendingBootRestoreFailure(
                this, BootRestoreDecision.FailureReason.SERVICE_START_REFUSED);
        stopOverlayCompletely();
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

    /**
     * Applies a debug-mode toggle the user just made. Turning it off is a deliberate user
     * action, so the fade-out reveal that {@link PointOverlayView#setDebugEnabled} starts is
     * wanted here even though a system-driven refresh suppresses it.
     */
    private void applyDebugToOverlays(boolean enabled) {
        for (OverlayEntry entry : windowsByKey.values()) {
            entry.view.setDebugEnabled(enabled);
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
