package com.payton.touchblocker;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "MainActivity";

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);

        Button btnRequestOverlay = findViewById(R.id.btn_request_overlay);
        Button btnStartOverlay = findViewById(R.id.btn_start_overlay);
        Button btnStopOverlay = findViewById(R.id.btn_stop_overlay);
        Button btnStartRecord = findViewById(R.id.btn_start_record);
        Button btnManagePoints = findViewById(R.id.btn_manage_points);
        Button btnTestBlock = findViewById(R.id.btn_test_block);
        Button btnExport = findViewById(R.id.btn_export_logs);
        final Button btnToggleDebug = findViewById(R.id.btn_toggle_debug);

        updateDebugButton(btnToggleDebug);

        btnRequestOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OverlayPermission.request(MainActivity.this);
            }
        });

        btnStartOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!OverlayPermission.ensure(MainActivity.this)) {
                    return;
                }
                Log.d(TAG, "Start overlay");
                startOverlayService(OverlayService.ACTION_START_OVERLAY);
                tvStatus.setText("Overlay: ON");
            }
        });

        btnStopOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Stop overlay");
                startOverlayService(OverlayService.ACTION_STOP_OVERLAY);
                tvStatus.setText("Overlay: OFF");
            }
        });

        btnStartRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!OverlayPermission.ensure(MainActivity.this)) {
                    return;
                }
                Log.d(TAG, "Start recording screen");
                startActivity(new Intent(MainActivity.this, RecordingActivity.class));
            }
        });

        btnManagePoints.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!OverlayPermission.ensure(MainActivity.this)) {
                    return;
                }
                Log.d(TAG, "Open manage points");
                startActivity(new Intent(MainActivity.this, ManagePointsActivity.class));
            }
        });

        btnTestBlock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!OverlayPermission.ensure(MainActivity.this)) {
                    return;
                }
                Log.d(TAG, "Open test blocking");
                startActivity(new Intent(MainActivity.this, TestBlockActivity.class));
            }
        });

        btnToggleDebug.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!OverlayPermission.ensure(MainActivity.this)) {
                    return;
                }
                boolean enabled = !PointStore.isDebugOverlayEnabled(MainActivity.this);
                PointStore.setDebugOverlayEnabled(MainActivity.this, enabled);
                Log.d(TAG, "Toggle debug overlay=" + enabled);
                updateDebugButton(btnToggleDebug);
                notifyDebugChanged(enabled);
            }
        });

        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLogs();
            }
        });
    }

    private void startOverlayService(String action) {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void exportLogs() {
        try {
            Intent shareIntent = LogManager.buildShareIntent(this);
            if (shareIntent == null) {
                Toast.makeText(this, "No logs yet", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(Intent.createChooser(shareIntent, "Export Logs"));
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDebugButton(Button button) {
        boolean enabled = PointStore.isDebugOverlayEnabled(this);
        button.setText(enabled ? "Debug Overlay: ON" : "Debug Overlay: OFF");
    }

    private void notifyDebugChanged(boolean enabled) {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_SET_DEBUG);
        intent.putExtra(OverlayService.EXTRA_DEBUG_ENABLED, enabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
