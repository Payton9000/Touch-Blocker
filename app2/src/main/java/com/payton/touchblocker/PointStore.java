package com.payton.touchblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PointStore {
    private static final String PREFS_NAME = "touch_blocker_prefs";
    private static final String KEY_POINTS = "points_json";
    private static final String KEY_GLOBAL_SIZE = "global_size_px";
    private static final String KEY_DEBUG_OVERLAY = "debug_overlay";
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

    public static int getNextId(Context context) {
        int maxId = 0;
        for (TouchPoint point : loadPoints(context)) {
            maxId = Math.max(maxId, point.getId());
        }
        return maxId + 1;
    }

    public static List<TouchPoint> loadPoints(Context context) {
        String json = getPrefs(context).getString(KEY_POINTS, "[]");
        List<TouchPoint> points = new ArrayList<>();
        boolean updated = false;
        DisplayMetrics metrics = getRealDisplayMetrics(context);
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                int id = obj.optInt("id", 0);
                float x = (float) obj.optDouble("x", 0);
                float y = (float) obj.optDouble("y", 0);
                long timestamp = obj.optLong("timestamp", 0);
                long duration = obj.optLong("duration", 0);
                TouchPoint point = new TouchPoint(id, x, y, timestamp, duration);
                point.setEnabled(obj.optBoolean("enabled", true));
                point.setSizeOverridePx(obj.optInt("sizeOverride", -1));
                point.setNormalizedX((float) obj.optDouble("nx", -1));
                point.setNormalizedY((float) obj.optDouble("ny", -1));
                point.setBaseRotation(obj.optInt("rotation", -1));
                point.setBaseWidthPx(obj.optInt("baseWidth", -1));
                point.setBaseHeightPx(obj.optInt("baseHeight", -1));
                if (point.getNormalizedX() < 0f && point.getNormalizedY() < 0f
                        && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    point.setNormalizedX(x / metrics.widthPixels);
                    point.setNormalizedY(y / metrics.heightPixels);
                    updated = true;
                }
                points.add(point);
            }
        } catch (JSONException ignored) {
        }
        if (updated) {
            savePoints(context, points);
        }
        return points;
    }

    public static void savePoints(Context context, List<TouchPoint> points) {
        JSONArray array = new JSONArray();
        for (TouchPoint point : points) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", point.getId());
                obj.put("x", point.getX());
                obj.put("y", point.getY());
                obj.put("timestamp", point.getTimestamp());
                obj.put("duration", point.getDurationMs());
                obj.put("enabled", point.isEnabled());
                obj.put("sizeOverride", point.getSizeOverridePx());
                obj.put("nx", point.getNormalizedX());
                obj.put("ny", point.getNormalizedY());
                obj.put("rotation", point.getBaseRotation());
                obj.put("baseWidth", point.getBaseWidthPx());
                obj.put("baseHeight", point.getBaseHeightPx());
                array.put(obj);
            } catch (JSONException ignored) {
            }
        }
        getPrefs(context).edit().putString(KEY_POINTS, array.toString()).apply();
    }

    public static float resolveX(Context context, TouchPoint point) {
        return resolvePoint(context, point)[0];
    }

    public static float resolveY(Context context, TouchPoint point) {
        return resolvePoint(context, point)[1];
    }

    private static float[] resolvePoint(Context context, TouchPoint point) {
        DisplayMetrics metrics = getRealDisplayMetrics(context);
        int currentRotation = getCurrentRotation(context);
        int currentWidth = metrics.widthPixels;
        int currentHeight = metrics.heightPixels;
        int currentNaturalW = (currentRotation == Surface.ROTATION_0 || currentRotation == Surface.ROTATION_180)
                ? currentWidth : currentHeight;
        int currentNaturalH = (currentRotation == Surface.ROTATION_0 || currentRotation == Surface.ROTATION_180)
                ? currentHeight : currentWidth;

        float naturalX;
        float naturalY;

        if (point.getBaseWidthPx() > 0 && point.getBaseHeightPx() > 0 && point.getBaseRotation() >= 0) {
            int baseRotation = point.getBaseRotation();
            int baseNaturalW = (baseRotation == Surface.ROTATION_0 || baseRotation == Surface.ROTATION_180)
                    ? point.getBaseWidthPx() : point.getBaseHeightPx();
            int baseNaturalH = (baseRotation == Surface.ROTATION_0 || baseRotation == Surface.ROTATION_180)
                    ? point.getBaseHeightPx() : point.getBaseWidthPx();

            float[] baseNatural = unrotateToNatural(point.getX(), point.getY(), baseRotation, baseNaturalW, baseNaturalH);
            naturalX = baseNatural[0];
            naturalY = baseNatural[1];

            if (baseNaturalW > 0 && baseNaturalH > 0) {
                float scaleX = currentNaturalW / (float) baseNaturalW;
                float scaleY = currentNaturalH / (float) baseNaturalH;
                naturalX *= scaleX;
                naturalY *= scaleY;
            }
        } else if (point.getNormalizedX() >= 0f && point.getNormalizedY() >= 0f) {
            naturalX = point.getNormalizedX() * currentNaturalW;
            naturalY = point.getNormalizedY() * currentNaturalH;
        } else {
            return new float[]{point.getX(), point.getY()};
        }

        return rotateFromNatural(naturalX, naturalY, currentRotation, currentNaturalW, currentNaturalH);
    }

    private static float[] rotateFromNatural(float x, float y, int rotation, int naturalW, int naturalH) {
        if (rotation == Surface.ROTATION_90) {
            return new float[]{y, naturalW - x};
        }
        if (rotation == Surface.ROTATION_180) {
            return new float[]{naturalW - x, naturalH - y};
        }
        if (rotation == Surface.ROTATION_270) {
            return new float[]{naturalH - y, x};
        }
        return new float[]{x, y};
    }

    private static float[] unrotateToNatural(float x, float y, int rotation, int naturalW, int naturalH) {
        if (rotation == Surface.ROTATION_90) {
            return new float[]{naturalW - y, x};
        }
        if (rotation == Surface.ROTATION_180) {
            return new float[]{naturalW - x, naturalH - y};
        }
        if (rotation == Surface.ROTATION_270) {
            return new float[]{y, naturalH - x};
        }
        return new float[]{x, y};
    }

    private static int getCurrentRotation(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            return Surface.ROTATION_0;
        }
        Display display = wm.getDefaultDisplay();
        return display.getRotation();
    }

    public static void addPoint(Context context, TouchPoint point) {
        List<TouchPoint> points = loadPoints(context);
        points.add(point);
        savePoints(context, points);
    }

    public static void updatePoint(Context context, TouchPoint updatedPoint) {
        List<TouchPoint> points = loadPoints(context);
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).getId() == updatedPoint.getId()) {
                points.set(i, updatedPoint);
                break;
            }
        }
        savePoints(context, points);
    }

    public static void deletePoint(Context context, int pointId) {
        List<TouchPoint> points = loadPoints(context);
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).getId() == pointId) {
                points.remove(i);
                break;
            }
        }
        savePoints(context, points);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static DisplayMetrics getRealDisplayMetrics(Context context) {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            Display display = wm.getDefaultDisplay();
            display.getRealMetrics(metrics);
        } else {
            metrics = context.getResources().getDisplayMetrics();
        }
        return metrics;
    }
}
