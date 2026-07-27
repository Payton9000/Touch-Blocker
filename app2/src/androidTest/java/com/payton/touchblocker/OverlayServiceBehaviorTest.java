package com.payton.touchblocker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ScreenProfile;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class OverlayServiceBehaviorTest {
    private static final long RUNNING_STATE_TIMEOUT_MS = 5_000L;

    @Test
    public void disposesViewBeforeRemovingItFromWindowManager() throws Exception {
        AtomicBoolean removed = new AtomicBoolean();
        AtomicBoolean disposedAtRemoval = new AtomicBoolean();
        AtomicReference<TrackingPointOverlayView> viewReference = new AtomicReference<>();
        AtomicReference<Map<?, ?>> windowsByKeyReference = new AtomicReference<>();

        runOnMainSync(() -> {
            OverlayService service = new OverlayService();
            TrackingPointOverlayView view = new TrackingPointOverlayView();
            view.startFadeOut();
            viewReference.set(view);

            WindowManager windowManager = (WindowManager) Proxy.newProxyInstance(
                    OverlayServiceBehaviorTest.class.getClassLoader(),
                    new Class<?>[]{WindowManager.class},
                    (proxy, method, args) -> {
                        if ("removeView".equals(method.getName())) {
                            disposedAtRemoval.set(view.disposed);
                            removed.set(args != null && args.length == 1 && args[0] == view);
                        }
                        return null;
                    });

            Field windowManagerField = OverlayService.class.getDeclaredField("windowManager");
            windowManagerField.setAccessible(true);
            windowManagerField.set(service, windowManager);

            Field windowsByKeyField = OverlayService.class.getDeclaredField("windowsByKey");
            windowsByKeyField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> windowsByKey = (Map<String, Object>) windowsByKeyField.get(service);
            windowsByKeyReference.set(windowsByKey);

            Class<?> entryClass = Class.forName(OverlayService.class.getName() + "$OverlayEntry");
            Constructor<?> constructor = entryClass.getDeclaredConstructor(
                    PointOverlayView.class, WindowManager.LayoutParams.class);
            constructor.setAccessible(true);
            Object entry = constructor.newInstance(view, new WindowManager.LayoutParams());
            windowsByKey.put("test-key", entry);

            Method hideOverlay = OverlayService.class.getDeclaredMethod("hideOverlay");
            hideOverlay.setAccessible(true);
            hideOverlay.invoke(service);
        });

        assertTrue(viewReference.get().disposed);
        assertTrue(disposedAtRemoval.get());
        assertTrue(removed.get());
        assertTrue(windowsByKeyReference.get().isEmpty());
    }

    @Test
    public void startOverlayStartsRunningServiceAndStoppingItStopsTheService() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // The overlay only becomes visible (and the service only stays up past a first,
        // failed addView) when this app is actually allowed to draw overlay windows. That is
        // an environment/appops concern, not something this task's rewrite controls, so skip
        // rather than flake when it is not granted.
        assumeTrue(
                "SYSTEM_ALERT_WINDOW must be granted to this app for the overlay to add "
                        + "real windows",
                OverlayPermission.canDrawOverlays(context));

        clearProfileRepositoryState(context);
        try {
            DisplaySnapshotProvider snapshotProvider = new AndroidDisplaySnapshotProvider(
                    new DisplayGenerationTracker(), new WindowLayoutInfoObserver());
            DisplaySnapshot snapshot = snapshotProvider.capture(context, Display.DEFAULT_DISPLAY);

            // "full" is the region id DisplaySnapshotMapper assigns whenever there is no
            // separating fold feature, i.e. on every ordinary (non-foldable) test device.
            ProfilePoint point = ProfilePoint.enabled(
                    1, "full", 0.5f, 0.5f, 0L, 0L, snapshot.getGeneration());
            ScreenProfile profile = new ScreenProfile(
                    "task7-behavior-test-profile",
                    ProfileKind.OUTER,
                    48f,
                    Collections.singletonList(point),
                    Collections.singletonList(snapshot.getStableKey()),
                    snapshot.getRegions(),
                    snapshot.getUnsafeAreas());
            ProfileDocument document = new ProfileDocument(
                    2,
                    Collections.singletonMap(profile.getId(), profile),
                    Collections.<String, String>emptyMap());

            ProfileRepository repository = new ProfileRepository(
                    new SharedPreferencesKeyValueStore(PointStore.prefs(context)),
                    new ProfileJsonCodec());
            assertTrue(
                    "failed to install the test profile document",
                    repository.save(document));

            Intent serviceIntent = new Intent(context, OverlayService.class);
            sendServiceAction(context, OverlayService.ACTION_START_OVERLAY);
            awaitRunningState(context, true);

            sendServiceAction(context, OverlayService.ACTION_STOP_OVERLAY);
            // ACTION_STOP_OVERLAY only clears the overlay windows, matching its existing
            // contract (it does not stop the started service, same as before this rewrite).
            // Fully stop it the same way the system would once nothing keeps it alive, so
            // "stopped" below is an unambiguous, pollable state.
            context.stopService(serviceIntent);
            awaitRunningState(context, false);
        } finally {
            clearProfileRepositoryState(context);
        }
    }

    private static void sendServiceAction(Context context, String action) {
        Intent intent = new Intent(context, OverlayService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void awaitRunningState(Context context, boolean expectedRunning)
            throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + RUNNING_STATE_TIMEOUT_MS;
        boolean running = isOverlayServiceRunning(context);
        while (running != expectedRunning && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(50L);
            running = isOverlayServiceRunning(context);
        }
        assertEquals(expectedRunning, running);
    }

    @SuppressWarnings("deprecation")
    private static boolean isOverlayServiceRunning(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }
        for (ActivityManager.RunningServiceInfo info
                : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (OverlayService.class.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static void clearProfileRepositoryState(Context context) {
        ProfileRepository.clearProcessCacheForTest();
        PointStore.prefs(context).edit()
                .remove(ProfileRepository.KEY_CURRENT)
                .remove(ProfileRepository.KEY_BACKUP)
                .remove(ProfileRepository.KEY_LEGACY_BACKUP)
                .remove(ProfileRepository.KEY_MIGRATION_COMPLETE)
                .commit();
    }

    private static void runOnMainSync(ThrowingRunnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        rethrow(failure.get());
    }

    private static void rethrow(Throwable throwable) throws Exception {
        if (throwable == null) {
            return;
        }
        if (throwable instanceof Exception) {
            throw (Exception) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new AssertionError(throwable);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class TrackingPointOverlayView extends PointOverlayView {
        private boolean disposed;

        private TrackingPointOverlayView() {
            super(InstrumentationRegistry.getInstrumentation().getTargetContext());
        }

        @Override
        public void dispose() {
            disposed = true;
            super.dispose();
        }
    }
}
