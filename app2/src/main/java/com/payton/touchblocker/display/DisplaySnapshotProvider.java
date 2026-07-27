package com.payton.touchblocker.display;

import android.app.Activity;
import android.content.Context;

public interface DisplaySnapshotProvider {
    DisplaySnapshot capture(Activity activity);

    DisplaySnapshot capture(Context context, int displayId);
}
