package com.payton.touchblocker;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class TouchBlockerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
