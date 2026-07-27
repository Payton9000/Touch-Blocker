package com.payton.touchblocker.display;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.Display;

import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

public final class WindowLayoutInfoObserver {
    private static final DisplayFoldCache PROCESS_FOLD_CACHE = new DisplayFoldCache();

    private final Object lock = new Object();
    private final DisplayFoldCache foldCache;
    private final Consumer<WindowLayoutInfo> listener = new Consumer<WindowLayoutInfo>() {
        @Override
        public void accept(WindowLayoutInfo windowLayoutInfo) {
            Runnable callback;
            synchronized (lock) {
                if (!started || observedDisplayId == Display.INVALID_DISPLAY) {
                    return;
                }
                foldCache.update(observedDisplayId, findFoldFeature(windowLayoutInfo));
                callback = layoutInfoUpdatedCallback;
            }
            if (callback != null) {
                callback.run();
            }
        }
    };

    private WindowInfoTrackerCallbackAdapter callbackAdapter;
    private Activity activity;
    private boolean started;
    private int observedDisplayId = Display.INVALID_DISPLAY;
    private Runnable layoutInfoUpdatedCallback;

    public WindowLayoutInfoObserver() {
        this(PROCESS_FOLD_CACHE);
    }

    WindowLayoutInfoObserver(DisplayFoldCache foldCache) {
        if (foldCache == null) {
            throw new NullPointerException("foldCache == null");
        }
        this.foldCache = foldCache;
    }

    public void start(Activity activity) {
        start(activity, null);
    }

    /** Starts observation and runs {@code callback} after each cache update on the main executor. */
    public void start(Activity activity, Runnable callback) {
        if (activity == null) {
            throw new NullPointerException("activity == null");
        }
        stop();
        WindowInfoTrackerCallbackAdapter adapter = new WindowInfoTrackerCallbackAdapter(
                WindowInfoTracker.getOrCreate(activity));
        int displayId = activityDisplayId(activity);
        synchronized (lock) {
            this.activity = activity;
            callbackAdapter = adapter;
            observedDisplayId = displayId;
            layoutInfoUpdatedCallback = callback;
            started = true;
        }
        adapter.addWindowLayoutInfoListener(
                activity,
                ContextCompat.getMainExecutor(activity),
                listener);
    }

    /**
     * Starts observing {@code displayId} using a window context instead of an {@link Activity}
     * (e.g. one created for the overlay's window type via
     * {@link android.content.Context#createWindowContext(int, android.os.Bundle)}). The
     * {@code activity} field stays null on this path; {@link #stop()} already tolerates that.
     */
    public void start(Context uiContext, int displayId) {
        start(uiContext, displayId, null);
    }

    /**
     * Starts observing a display-backed window context and invokes {@code callback} whenever
     * the fold cache receives newer geometry. Overlay callers use this second signal because a
     * display rotation notification can arrive before WindowManager publishes the rotated hinge.
     */
    public void start(Context uiContext, int displayId, Runnable callback) {
        if (uiContext == null) {
            throw new NullPointerException("uiContext == null");
        }
        stop();
        WindowInfoTrackerCallbackAdapter adapter = new WindowInfoTrackerCallbackAdapter(
                WindowInfoTracker.getOrCreate(uiContext));
        synchronized (lock) {
            callbackAdapter = adapter;
            observedDisplayId = displayId;
            layoutInfoUpdatedCallback = callback;
            started = true;
        }
        adapter.addWindowLayoutInfoListener(
                uiContext, ContextCompat.getMainExecutor(uiContext), listener);
    }

    public void stop() {
        WindowInfoTrackerCallbackAdapter adapter;
        synchronized (lock) {
            if (!started) {
                activity = null;
                callbackAdapter = null;
                layoutInfoUpdatedCallback = null;
                return;
            }
            started = false;
            adapter = callbackAdapter;
            activity = null;
            callbackAdapter = null;
            layoutInfoUpdatedCallback = null;
        }
        if (adapter != null) {
            adapter.removeWindowLayoutInfoListener(listener);
        }
    }

    public FoldFeatureData getLatestFoldFeature() {
        int displayId;
        synchronized (lock) {
            displayId = observedDisplayId;
        }
        return displayId == Display.INVALID_DISPLAY ? null : foldCache.get(displayId);
    }

    public FoldFeatureData getLatestFoldFeature(int displayId) {
        return foldCache.get(displayId);
    }

    @SuppressWarnings("deprecation")
    private static int activityDisplayId(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activity.getDisplay() != null) {
            return activity.getDisplay().getDisplayId();
        }
        return activity.getWindowManager().getDefaultDisplay().getDisplayId();
    }

    private static FoldFeatureData findFoldFeature(WindowLayoutInfo windowLayoutInfo) {
        if (windowLayoutInfo == null) {
            return null;
        }
        for (DisplayFeature feature : windowLayoutInfo.getDisplayFeatures()) {
            if (feature instanceof FoldingFeature) {
                FoldingFeature foldingFeature = (FoldingFeature) feature;
                Rect bounds = foldingFeature.getBounds();
                return new FoldFeatureData(
                        new IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        foldingFeature.isSeparating(),
                        FoldingFeature.OcclusionType.FULL.equals(foldingFeature.getOcclusionType()));
            }
        }
        return null;
    }
}
