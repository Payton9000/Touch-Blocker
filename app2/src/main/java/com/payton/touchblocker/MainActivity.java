package com.payton.touchblocker;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private TextView tvStatus;
    private TextView tvBootRestoreSummary;
    private TextView tvBootRestoreFailure;
    private Button btnToggleDebug;
    private SwitchCompat switchBootRestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvBootRestoreSummary = findViewById(R.id.tv_boot_restore_summary);
        tvBootRestoreFailure = findViewById(R.id.tv_boot_restore_failure);
        switchBootRestore = findViewById(R.id.switch_boot_restore);

        Button btnRequestOverlay = findViewById(R.id.btn_request_overlay);
        Button btnStartOverlay = findViewById(R.id.btn_start_overlay);
        Button btnStopOverlay = findViewById(R.id.btn_stop_overlay);
        Button btnStartRecord = findViewById(R.id.btn_start_record);
        Button btnManagePoints = findViewById(R.id.btn_manage_points);
        Button btnTestBlock = findViewById(R.id.btn_test_block);
        Button btnExport = findViewById(R.id.btn_export_logs);
        btnToggleDebug = findViewById(R.id.btn_toggle_debug);

        refreshUiState();

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
                PointStore.setOverlayShouldBeEnabled(MainActivity.this, true);
                PointStore.clearPendingBootRestoreFailure(MainActivity.this);
                startOverlayService(OverlayService.ACTION_START_OVERLAY);
                refreshUiState();
            }
        });

        btnStopOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Stop overlay");
                PointStore.setOverlayShouldBeEnabled(MainActivity.this, false);
                PointStore.clearPendingBootRestoreFailure(MainActivity.this);
                startOverlayService(OverlayService.ACTION_STOP_OVERLAY);
                refreshUiState();
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
                updateDebugButton();
                notifyDebugChanged(enabled);
            }
        });

        switchBootRestore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean enabled = switchBootRestore.isChecked();
                PointStore.setBootRestoreEnabled(MainActivity.this, enabled);
                PointStore.clearPendingBootRestoreFailure(MainActivity.this);
                updateOverlayStatus();
                updateBootRestoreSection();
            }
        });

        btnExport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportLogs();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUiState();
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
                Toast.makeText(this, getString(R.string.no_logs_yet), Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_logs)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.export_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateDebugButton() {
        boolean enabled = PointStore.isDebugOverlayEnabled(this);
        btnToggleDebug.setText(getString(enabled ? R.string.debug_overlay_on : R.string.debug_overlay_off));
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

    private void refreshUiState() {
        updateOverlayStatus();
        updateDebugButton();
        updateBootRestoreSection();
    }

    private void updateOverlayStatus() {
        boolean showOn = PointStore.shouldOverlayBeEnabled(this)
                && PointStore.getPendingBootRestoreFailure(this) == BootRestoreDecision.FailureReason.NONE;
        tvStatus.setText(getString(showOn ? R.string.overlay_status_on : R.string.overlay_status_off));
    }

    private void updateBootRestoreSection() {
        boolean autoStartEnabled = PointStore.isBootRestoreEnabled(this);
        boolean overlayShouldBeEnabled = PointStore.shouldOverlayBeEnabled(this);
        switchBootRestore.setChecked(autoStartEnabled);

        int summaryResId;
        if (!autoStartEnabled) {
            summaryResId = R.string.auto_start_on_boot_summary_disabled;
        } else if (overlayShouldBeEnabled) {
            summaryResId = R.string.auto_start_on_boot_summary_ready;
        } else {
            summaryResId = R.string.auto_start_on_boot_summary_waiting;
        }
        tvBootRestoreSummary.setText(getString(summaryResId));

        int failureResId = 0;
        BootRestoreDecision.FailureReason failureReason = PointStore.getPendingBootRestoreFailure(this);
        if (failureReason == BootRestoreDecision.FailureReason.MISSING_PERMISSION) {
            failureResId = R.string.auto_start_on_boot_failure_missing_permission;
        } else if (failureReason == BootRestoreDecision.FailureReason.NO_ENABLED_POINTS) {
            failureResId = R.string.auto_start_on_boot_failure_no_points;
        }

        if (failureResId == 0) {
            tvBootRestoreFailure.setVisibility(View.GONE);
            tvBootRestoreFailure.setText("");
        } else {
            tvBootRestoreFailure.setVisibility(View.VISIBLE);
            tvBootRestoreFailure.setText(getString(failureResId));
        }
    }
}
