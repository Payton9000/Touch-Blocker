package com.payton.touchblocker.display;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.RequiresApi;
import androidx.window.layout.WindowMetrics;
import androidx.window.layout.WindowMetricsCalculator;

import com.payton.touchblocker.profile.ProfileKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AndroidDisplaySnapshotProvider implements DisplaySnapshotProvider {
    private final DisplayGenerationTracker generationTracker;
    private final WindowLayoutInfoObserver layoutInfoObserver;

    public AndroidDisplaySnapshotProvider(
            DisplayGenerationTracker generationTracker,
            WindowLayoutInfoObserver layoutInfoObserver
    ) {
        if (generationTracker == null) {
            throw new NullPointerException("generationTracker == null");
        }
        if (layoutInfoObserver == null) {
            throw new NullPointerException("layoutInfoObserver == null");
        }
        this.generationTracker = generationTracker;
        this.layoutInfoObserver = layoutInfoObserver;
    }

    @Override
    public DisplaySnapshot capture(Activity activity) {
        if (activity == null) {
            throw new NullPointerException("activity == null");
        }
        Display display = activityDisplay(activity);
        WindowMetrics metrics = WindowMetricsCalculator.getOrCreate()
                .computeCurrentWindowMetrics(activity);
        Rect metricsBounds = metrics.getBounds();
        IntRect windowBounds = new IntRect(
                metricsBounds.left,
                metricsBounds.top,
                metricsBounds.right,
                metricsBounds.bottom);

        View decorView = activity.getWindow().getDecorView();
        WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(decorView);
        DisplayFrameData frame = readActivityFrame(windowBounds, rootInsets);
        FoldFeatureData foldFeature = offsetFold(
                layoutInfoObserver.getLatestFoldFeature(display.getDisplayId()),
                frame.getBounds().getLeft(),
                frame.getBounds().getTop());
        return captureInputs(
                activity,
                display,
                frame.getBounds(),
                frame.getSafeInsets(),
                frame.getCutouts(),
                foldFeature);
    }

    @Override
    public DisplaySnapshot capture(Context context, int displayId) {
        if (context == null) {
            throw new NullPointerException("context == null");
        }
        DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager == null ? null : displayManager.getDisplay(displayId);
        if (display == null) {
            throw new IllegalArgumentException("Unknown displayId=" + displayId);
        }
        Context displayContext = displayId == Display.DEFAULT_DISPLAY
                ? context
                : context.createDisplayContext(display);
        DisplayFrameData frame = readContextFrame(displayContext, display);
        FoldFeatureData foldFeature = offsetFold(
                layoutInfoObserver.getLatestFoldFeature(displayId),
                frame.getBounds().getLeft(),
                frame.getBounds().getTop());
        return captureInputs(
                displayContext,
                display,
                frame.getBounds(),
                frame.getSafeInsets(),
                frame.getCutouts(),
                foldFeature);
    }

    private DisplaySnapshot captureInputs(
            Context context,
            Display display,
            IntRect windowBounds,
            EdgeInsets safeInsets,
            List<IntRect> cutouts,
            FoldFeatureData foldFeature
    ) {
        int displayId = display.getDisplayId();
        int rotation = display.getRotation();
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        Point currentSize = realSize(display);
        int naturalWidth = isQuarterTurn(rotation) ? currentSize.y : currentSize.x;
        int naturalHeight = isQuarterTurn(rotation) ? currentSize.x : currentSize.y;
        Point maximumSize = maximumModeSize(display, naturalWidth, naturalHeight);
        String role = displayId == Display.DEFAULT_DISPLAY ? "built-in" : "external";
        String stableKey = DisplayStableKeyFactory.create(
                null,
                naturalWidth,
                naturalHeight,
                maximumSize.x,
                maximumSize.y,
                metrics.densityDpi,
                role);
        DisplayGeometrySignature signature = new DisplayGeometrySignature(
                windowBounds,
                rotation,
                safeInsets,
                foldFeature,
                cutouts);
        long generation = generationTracker.generationFor(displayId, signature);
        ProfileKind suggestedKind = DisplayProfileKindSuggester.suggest(foldFeature);
        return DisplaySnapshotMapper.map(
                stableKey,
                displayId,
                windowBounds,
                rotation,
                metrics.density,
                generation,
                suggestedKind,
                safeInsets,
                cutouts,
                foldFeature);
    }

    private static Display activityDisplay(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && activity.getDisplay() != null) {
            return activity.getDisplay();
        }
        return activity.getWindowManager().getDefaultDisplay();
    }

    private static DisplayFrameData readActivityFrame(
            IntRect windowBounds,
            WindowInsetsCompat rootInsets
    ) {
        if (rootInsets == null) {
            return DisplayFrameData.fromWindowData(
                    windowBounds,
                    EdgeInsets.NONE,
                    EdgeInsets.NONE,
                    EdgeInsets.NONE,
                    Collections.<IntRect>emptyList());
        }
        return DisplayFrameData.fromWindowData(
                windowBounds,
                toEdgeInsets(rootInsets.getInsets(WindowInsetsCompat.Type.systemBars())),
                toEdgeInsets(rootInsets.getInsets(WindowInsetsCompat.Type.displayCutout())),
                toEdgeInsets(rootInsets.getInsets(
                        WindowInsetsCompat.Type.mandatorySystemGestures())),
                readCompatCutouts(rootInsets.getDisplayCutout()));
    }

    private static DisplayFrameData readContextFrame(Context displayContext, Display display) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            DisplayFrameData frame = Api30Impl.readFrame(displayContext);
            if (frame != null) {
                return frame;
            }
        }

        Point size = realSize(display);
        IntRect bounds = new IntRect(0, 0, size.x, size.y);
        EdgeInsets conservativeSystemInsets = readConservativeSystemInsets(displayContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return Api29Impl.readFrame(display, bounds, conservativeSystemInsets);
        }
        return DisplayFrameData.fromWindowData(
                bounds,
                conservativeSystemInsets,
                EdgeInsets.NONE,
                EdgeInsets.NONE,
                CutoutInsetsCache.instance().get(display.getDisplayId()));
    }

    private static EdgeInsets readConservativeSystemInsets(Context displayContext) {
        Resources resources = displayContext.getResources();
        int statusBarHeight = maxSystemDimension(
                resources,
                "status_bar_height",
                "status_bar_height_landscape");
        int navigationBarHeight = maxSystemDimension(
                resources,
                "navigation_bar_height",
                "navigation_bar_height_landscape",
                "navigation_bar_frame_height",
                "navigation_bar_frame_height_landscape");
        int navigationBarWidth = maxSystemDimension(
                resources,
                "navigation_bar_width",
                "navigation_bar_frame_width");
        int sideGestureInset = maxSystemDimension(
                resources,
                "config_backGestureInset",
                "config_backGestureInsetScalable");
        int bottomGestureInset = maxSystemDimension(
                resources,
                "config_bottom_gesture_inset",
                "navigation_bar_gesture_height",
                "navigation_bar_gesture_larger_height");
        return ConservativeSystemBarInsets.create(
                statusBarHeight,
                navigationBarHeight,
                navigationBarWidth,
                sideGestureInset,
                bottomGestureInset,
                resources.getDisplayMetrics().densityDpi);
    }

    private static int maxSystemDimension(Resources resources, String... names) {
        int maximum = 0;
        for (String name : names) {
            int resourceId = resources.getIdentifier(name, "dimen", "android");
            if (resourceId == 0) {
                continue;
            }
            try {
                maximum = Math.max(maximum, resources.getDimensionPixelSize(resourceId));
            } catch (Resources.NotFoundException ignored) {
                // The density-based fallback remains conservative when an OEM omits a resource.
            }
        }
        return maximum;
    }

    private static List<IntRect> readCompatCutouts(DisplayCutoutCompat cutout) {
        if (cutout == null) {
            return Collections.emptyList();
        }
        ArrayList<IntRect> result = new ArrayList<>();
        for (Rect rect : cutout.getBoundingRects()) {
            result.add(new IntRect(rect.left, rect.top, rect.right, rect.bottom));
        }
        return result;
    }

    private static EdgeInsets toEdgeInsets(Insets insets) {
        return new EdgeInsets(insets.left, insets.top, insets.right, insets.bottom);
    }

    private static FoldFeatureData offsetFold(FoldFeatureData fold, int dx, int dy) {
        if (fold == null) {
            return null;
        }
        IntRect bounds = fold.getBounds();
        return new FoldFeatureData(
                new IntRect(
                        bounds.getLeft() + dx,
                        bounds.getTop() + dy,
                        bounds.getRight() + dx,
                        bounds.getBottom() + dy),
                fold.isSeparating(),
                fold.isFullOcclusion());
    }

    @SuppressWarnings("deprecation")
    private static Point realSize(Display display) {
        Point size = new Point();
        display.getRealSize(size);
        return size;
    }

    private static Point maximumModeSize(Display display, int fallbackWidth, int fallbackHeight) {
        int bestWidth = fallbackWidth;
        int bestHeight = fallbackHeight;
        long bestArea = (long) fallbackWidth * fallbackHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (Display.Mode mode : display.getSupportedModes()) {
                int width = mode.getPhysicalWidth();
                int height = mode.getPhysicalHeight();
                long area = (long) width * height;
                if (area > bestArea) {
                    bestArea = area;
                    bestWidth = width;
                    bestHeight = height;
                }
            }
        }
        return new Point(bestWidth, bestHeight);
    }

    private static boolean isQuarterTurn(int rotation) {
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static final class Api29Impl {
        private Api29Impl() {
        }

        static DisplayFrameData readFrame(
                Display display,
                IntRect bounds,
                EdgeInsets conservativeSystemInsets
        ) {
            android.view.DisplayCutout cutout = display.getCutout();
            EdgeInsets cutoutInsets = cutout == null
                    ? EdgeInsets.NONE
                    : new EdgeInsets(
                            cutout.getSafeInsetLeft(),
                            cutout.getSafeInsetTop(),
                            cutout.getSafeInsetRight(),
                            cutout.getSafeInsetBottom());
            return DisplayFrameData.fromWindowData(
                    bounds,
                    conservativeSystemInsets,
                    cutoutInsets,
                    EdgeInsets.NONE,
                    readCutouts(cutout));
        }

        private static List<IntRect> readCutouts(android.view.DisplayCutout cutout) {
            if (cutout == null) {
                return Collections.emptyList();
            }
            ArrayList<IntRect> result = new ArrayList<>();
            for (Rect rect : cutout.getBoundingRects()) {
                result.add(new IntRect(rect.left, rect.top, rect.right, rect.bottom));
            }
            return result;
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static final class Api30Impl {
        private Api30Impl() {
        }

        static DisplayFrameData readFrame(Context displayContext) {
            android.view.WindowManager windowManager =
                    (android.view.WindowManager) displayContext.getSystemService(
                            Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return null;
            }
            android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            Rect platformBounds = metrics.getBounds();
            IntRect bounds = new IntRect(
                    platformBounds.left,
                    platformBounds.top,
                    platformBounds.right,
                    platformBounds.bottom);
            android.view.WindowInsets windowInsets = metrics.getWindowInsets();
            return DisplayFrameData.fromWindowData(
                    bounds,
                    toEdgeInsets(windowInsets.getInsets(
                            android.view.WindowInsets.Type.systemBars())),
                    toEdgeInsets(windowInsets.getInsets(
                            android.view.WindowInsets.Type.displayCutout())),
                    toEdgeInsets(windowInsets.getInsets(
                            android.view.WindowInsets.Type.mandatorySystemGestures())),
                    readCutouts(windowInsets.getDisplayCutout()));
        }

        private static EdgeInsets toEdgeInsets(android.graphics.Insets insets) {
            return new EdgeInsets(insets.left, insets.top, insets.right, insets.bottom);
        }

        private static List<IntRect> readCutouts(android.view.DisplayCutout cutout) {
            if (cutout == null) {
                return Collections.emptyList();
            }
            ArrayList<IntRect> result = new ArrayList<>();
            for (Rect rect : cutout.getBoundingRects()) {
                result.add(new IntRect(rect.left, rect.top, rect.right, rect.bottom));
            }
            return result;
        }
    }
}
