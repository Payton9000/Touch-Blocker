package com.payton.touchblocker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.util.List;

public class RecordingActivity extends AppCompatActivity {
    private TextView tvHint;
    private TouchRecordView recordView;

    private static final String TAG = "RecordingActivity";

    private boolean recording = false;
    private long currentDownTime = 0L;
    private float currentDownX = 0f;
    private float currentDownY = 0f;

    private List<TouchPoint> points;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OverlayPermission.ensure(this)) {
            finish();
            return;
        }
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_recording);

        tvHint = findViewById(R.id.tv_record_hint);
        recordView = findViewById(R.id.record_view);

        points = PointStore.loadPoints(this);
        recordView.setPoints(points);

        View touchLayer = findViewById(R.id.record_touch_layer);
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!recording) {
                    return true;
                }
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    currentDownTime = System.currentTimeMillis();
                    currentDownX = event.getRawX();
                    currentDownY = event.getRawY();
                } else if (action == MotionEvent.ACTION_UP) {
                    long upTime = System.currentTimeMillis();
                    long duration = Math.max(0, upTime - currentDownTime);
                    TouchPoint point = new TouchPoint(PointStore.getNextId(RecordingActivity.this),
                            currentDownX, currentDownY, currentDownTime, duration);
                    DisplayMetrics metrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                    if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                        point.setNormalizedX(currentDownX / metrics.widthPixels);
                        point.setNormalizedY(currentDownY / metrics.heightPixels);
                    }
                    int rotation = getWindowManager().getDefaultDisplay().getRotation();
                    point.setBaseRotation(rotation);
                    point.setBaseWidthPx(metrics.widthPixels);
                    point.setBaseHeightPx(metrics.heightPixels);
                    points.add(point);
                    recordView.addPoint(point);
                    PointStore.addPoint(RecordingActivity.this, point);
                    LogManager.appendPoint(RecordingActivity.this, point);
                    Log.d(TAG, "Recorded point id=" + point.getId());
                }
                return true;
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            recording = true;
            tvHint.setText("Recording: ON (Volume Down to stop)");
            Log.d(TAG, "Recording ON");
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            recording = false;
            tvHint.setText("Recording: OFF (Saved)");
            Log.d(TAG, "Recording OFF");
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
