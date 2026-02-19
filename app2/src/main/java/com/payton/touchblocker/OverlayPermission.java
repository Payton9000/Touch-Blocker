package com.payton.touchblocker;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

public class OverlayPermission {
    public static final int REQUEST_CODE = 1001;

    public static boolean canDrawOverlays(Activity activity) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(activity);
    }

    public static void request(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, REQUEST_CODE);
        }
    }

    public static boolean ensure(Activity activity) {
        if (canDrawOverlays(activity)) {
            return true;
        }
        Toast.makeText(activity, "Please grant overlay permission", Toast.LENGTH_SHORT).show();
        request(activity);
        return false;
    }
}

