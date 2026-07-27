package com.payton.touchblocker;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.EdgeInsets;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.profile.FoldableUi;
import com.payton.touchblocker.profile.ActiveProfileResolver;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ProfileRepositoryFactory;
import com.payton.touchblocker.profile.ProfileSelection;
import com.payton.touchblocker.profile.ScreenProfile;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;
import com.payton.touchblocker.ui.PreviewModelFactory;
import com.payton.touchblocker.ui.ScreenPreviewView;
import com.payton.touchblocker.ui.WindowInsetsApplier;

import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String NOTIFICATION_PREFS = "touch_blocker_main_prefs";
    private static final String KEY_NOTIFICATION_PERMISSION_REQUESTED = "notif_perm_requested";
    private static final int REQUEST_NOTIFICATION_PERMISSION = 100;

    private TextView tvStatus;
    private TextView tvBootRestoreSummary;
    private TextView tvBootRestoreFailure;
    private TextView tvProfileState;
    private MaterialButton btnRequestOverlay;
    private MaterialSwitch switchOverlay;
    private MaterialSwitch switchBootRestore;
    private MaterialSwitch switchDebug;
    private ChipGroup profileChips;
    private Chip chipInner;
    private Chip chipOuter;
    private Chip chipAuto;
    private ScreenPreviewView screenPreview;
    private boolean renderingOverlayState;
    private boolean renderingProfileState;
    private ProfileRepository repository;
    private ProfileDocument document;
    private DisplaySnapshot snapshot;

    private final WindowLayoutInfoObserver windowLayoutInfoObserver = new WindowLayoutInfoObserver();
    private final DisplaySnapshotProvider displaySnapshotProvider =
            new AndroidDisplaySnapshotProvider(
                    new DisplayGenerationTracker(),
                    windowLayoutInfoObserver);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        repository = ProfileRepositoryFactory.create(
                name -> new SharedPreferencesKeyValueStore(
                        getSharedPreferences(name, MODE_PRIVATE)));
        bindViews();
        configureEdgeToEdge();
        configureActions();
        refreshUiState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUiState();
        refreshPreview();
    }

    @Override
    protected void onStart() {
        super.onStart();
        windowLayoutInfoObserver.start(this);
    }

    @Override
    protected void onStop() {
        windowLayoutInfoObserver.stop();
        super.onStop();
    }

    @VisibleForTesting
    DisplaySnapshot captureDisplaySnapshotForTest() {
        return displaySnapshotProvider.capture(this);
    }

    private void bindViews() {
        tvStatus = findViewById(R.id.tv_status);
        tvBootRestoreSummary = findViewById(R.id.tv_boot_restore_summary);
        tvBootRestoreFailure = findViewById(R.id.tv_boot_restore_failure);
        tvProfileState = findViewById(R.id.tv_profile_state);
        btnRequestOverlay = findViewById(R.id.btn_request_overlay);
        switchOverlay = findViewById(R.id.switch_overlay);
        switchBootRestore = findViewById(R.id.switch_boot_restore);
        switchDebug = findViewById(R.id.switch_debug);
        profileChips = findViewById(R.id.profile_chips);
        chipInner = findViewById(R.id.chip_inner);
        chipOuter = findViewById(R.id.chip_outer);
        chipAuto = findViewById(R.id.chip_auto);
        screenPreview = findViewById(R.id.screen_preview);
    }

    private void configureEdgeToEdge() {
        View content = findViewById(R.id.main_scroll_content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            WindowInsetsApplier.applyContentContainerPadding(
                    view,
                    new EdgeInsets(bars.left, bars.top, bars.right, bars.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void configureActions() {
        btnRequestOverlay.setOnClickListener(v -> OverlayPermission.request(MainActivity.this));

        switchOverlay.setOnCheckedChangeListener((buttonView, enabled) -> {
            if (renderingOverlayState) {
                return;
            }
            if (enabled) {
                startOverlayFromUser();
            } else {
                stopOverlayFromUser();
            }
        });

        findViewById(R.id.btn_start_record).setOnClickListener(v -> {
            if (!OverlayPermission.ensure(MainActivity.this)) {
                return;
            }
            Log.d(TAG, "Start recording screen");
            startActivity(new Intent(MainActivity.this, RecordingActivity.class));
        });
        findViewById(R.id.btn_manage_points).setOnClickListener(v -> {
            if (!OverlayPermission.ensure(MainActivity.this)) {
                return;
            }
            Log.d(TAG, "Open manage points");
            startActivity(new Intent(MainActivity.this, ManagePointsActivity.class));
        });
        findViewById(R.id.btn_test_block).setOnClickListener(v -> {
            if (!OverlayPermission.ensure(MainActivity.this)) {
                return;
            }
            Log.d(TAG, "Open test blocking");
            startActivity(new Intent(MainActivity.this, TestBlockActivity.class));
        });

        switchDebug.setOnCheckedChangeListener((buttonView, enabled) -> {
            if (renderingOverlayState) {
                return;
            }
            if (!OverlayPermission.ensure(MainActivity.this)) {
                renderDebugState();
                return;
            }
            PointStore.setDebugOverlayEnabled(MainActivity.this, enabled);
            Log.d(TAG, "Toggle debug overlay=" + enabled);
            notifyDebugChanged(enabled);
        });

        switchBootRestore.setOnCheckedChangeListener((buttonView, enabled) -> {
            if (renderingOverlayState) {
                return;
            }
            if (enabled && !OverlayPermission.canDrawOverlays(MainActivity.this)) {
                renderingOverlayState = true;
                switchBootRestore.setChecked(false);
                renderingOverlayState = false;
                Toast.makeText(
                        MainActivity.this,
                        getString(R.string.overlay_permission_required),
                        Toast.LENGTH_SHORT).show();
                OverlayPermission.request(MainActivity.this);
                return;
            }
            PointStore.setBootRestoreEnabled(MainActivity.this, enabled);
            if (enabled) {
                PointStore.setOverlayShouldBeEnabled(MainActivity.this, true);
            }
            PointStore.clearPendingBootRestoreFailure(MainActivity.this);
            refreshUiState();
        });

        findViewById(R.id.btn_export_logs).setOnClickListener(v -> exportLogs());
        profileChips.setOnCheckedChangeListener((group, checkedId) -> {
            if (renderingProfileState || checkedId == View.NO_ID || snapshot == null || document == null) {
                return;
            }
            if (checkedId == R.id.chip_auto) {
                saveProfileDocument(document.withoutManualBinding(snapshot.getStableKey()));
            } else if (checkedId == R.id.chip_inner) {
                bindProfileKind(ProfileKind.INNER);
            } else if (checkedId == R.id.chip_outer) {
                bindProfileKind(ProfileKind.OUTER);
            }
        });
    }

    private void startOverlayFromUser() {
        if (!OverlayPermission.ensure(this)) {
            refreshUiState();
            return;
        }
        requestNotificationPermissionOnce();
        Log.d(TAG, "Start overlay");
        PointStore.setOverlayShouldBeEnabled(this, true);
        PointStore.clearPendingBootRestoreFailure(this);
        startOverlayService(OverlayService.ACTION_START_OVERLAY);
        refreshUiState();
    }

    private void stopOverlayFromUser() {
        Log.d(TAG, "Stop overlay");
        PointStore.setOverlayShouldBeEnabled(this, false);
        PointStore.clearPendingBootRestoreFailure(this);
        startOverlayService(OverlayService.ACTION_STOP_OVERLAY);
        refreshUiState();
    }

    private void requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences(NOTIFICATION_PREFS, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)) {
            return;
        }
        preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply();
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    private void refreshPreview() {
        try {
            snapshot = displaySnapshotProvider.capture(this);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to capture display snapshot for preview", failure);
            return;
        }
        ProfileRepository.LoadResult loaded = repository.migrateIfNeeded(
                PointStore.getRawPointsJson(this),
                PointStore.getGlobalSizePx(this),
                snapshot,
                snapshot.getSuggestedKind() == null
                        ? ProfileKind.OUTER : snapshot.getSuggestedKind());
        if (!loaded.isSuccess()) {
            Log.w(TAG, "Unable to load display profiles for preview: " + loaded.getError());
            return;
        }
        ActiveProfileResolver.Resolution resolution = ActiveProfileResolver.resolve(
                repository, loaded.getDocument(), snapshot);
        document = resolution.getDocument();
        renderPreview(resolution);
    }

    private void renderPreview(ActiveProfileResolver.Resolution resolution) {
        ScreenProfile profile = resolution.getProfile();
        if (profile == null) {
            profile = previewOnlyProfile(snapshot);
        }
        screenPreview.setItems(PreviewModelFactory.build(snapshot, profile));
        renderProfileControls(resolution.getSelection());
    }

    private void renderProfileControls(ProfileSelection selection) {
        boolean showControls = FoldableUi.shouldShowProfileControls(document, snapshot);
        profileChips.setVisibility(showControls ? View.VISIBLE : View.GONE);
        String innerProfileId = profileIdOfKind(ProfileKind.INNER);
        String outerProfileId = profileIdOfKind(ProfileKind.OUTER);
        chipInner.setEnabled(innerProfileId != null);
        chipOuter.setEnabled(outerProfileId != null);

        renderingProfileState = true;
        String manualProfileId = document.getManualBindings().get(snapshot.getStableKey());
        if (manualProfileId != null && manualProfileId.equals(innerProfileId)) {
            chipInner.setChecked(true);
        } else if (manualProfileId != null && manualProfileId.equals(outerProfileId)) {
            chipOuter.setChecked(true);
        } else {
            chipAuto.setChecked(true);
        }
        renderingProfileState = false;

        if (selection.getStatus() == ProfileSelection.Status.UNKNOWN) {
            tvProfileState.setText(R.string.profile_state_unknown);
            tvProfileState.setVisibility(View.VISIBLE);
        } else if (selection.getStatus() == ProfileSelection.Status.AMBIGUOUS) {
            tvProfileState.setText(R.string.profile_state_ambiguous);
            tvProfileState.setVisibility(View.VISIBLE);
        } else {
            tvProfileState.setVisibility(View.GONE);
            tvProfileState.setText("");
        }
    }

    private void bindProfileKind(ProfileKind kind) {
        String profileId = profileIdOfKind(kind);
        if (profileId != null) {
            saveProfileDocument(document.withManualBinding(snapshot.getStableKey(), profileId));
        }
    }

    private String profileIdOfKind(ProfileKind kind) {
        for (ScreenProfile profile : document.getProfiles().values()) {
            if (profile.getKind() == kind) {
                return profile.getId();
            }
        }
        return null;
    }

    private void saveProfileDocument(ProfileDocument updated) {
        if (!repository.save(updated)) {
            return;
        }
        refreshPreview();
        if (PointStore.shouldOverlayBeEnabled(this) && OverlayPermission.canDrawOverlays(this)) {
            startOverlayService(OverlayService.ACTION_REFRESH_POINTS);
        }
    }

    private static ScreenProfile previewOnlyProfile(DisplaySnapshot snapshot) {
        return new ScreenProfile(
                "preview",
                snapshot.getSuggestedKind() == null ? ProfileKind.OUTER : snapshot.getSuggestedKind(),
                48f,
                Collections.emptyList(),
                Collections.emptyList(),
                snapshot.getRegions(),
                snapshot.getUnsafeAreas());
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

    private void renderDebugState() {
        boolean enabled = PointStore.isDebugOverlayEnabled(this);
        renderingOverlayState = true;
        switchDebug.setChecked(enabled);
        switchDebug.setText(getString(enabled ? R.string.debug_overlay_on : R.string.debug_overlay_off));
        renderingOverlayState = false;
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
        if (OverlayPermission.canDrawOverlays(this)
                && PointStore.getPendingBootRestoreFailure(this)
                == BootRestoreDecision.FailureReason.MISSING_PERMISSION) {
            PointStore.clearPendingBootRestoreFailure(this);
        }
        updateOverlayStatus();
        renderDebugState();
        updateBootRestoreSection();
    }

    private void updateOverlayStatus() {
        boolean hasPermission = OverlayPermission.canDrawOverlays(this);
        boolean showOn = PointStore.shouldOverlayBeEnabled(this)
                && PointStore.getPendingBootRestoreFailure(this) == BootRestoreDecision.FailureReason.NONE;
        tvStatus.setText(hasPermission
                ? getString(showOn ? R.string.overlay_status_on : R.string.overlay_status_off)
                : getString(R.string.permission_needed_title));
        btnRequestOverlay.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
        renderingOverlayState = true;
        switchOverlay.setChecked(PointStore.shouldOverlayBeEnabled(this));
        renderingOverlayState = false;
    }

    private void updateBootRestoreSection() {
        boolean autoStartEnabled = PointStore.isBootRestoreEnabled(this);
        boolean overlayShouldBeEnabled = PointStore.shouldOverlayBeEnabled(this);
        renderingOverlayState = true;
        switchBootRestore.setChecked(autoStartEnabled);
        renderingOverlayState = false;

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
        if (autoStartEnabled && !OverlayPermission.canDrawOverlays(this)) {
            failureResId = R.string.auto_start_on_boot_failure_missing_permission;
        } else if (failureReason == BootRestoreDecision.FailureReason.MISSING_PERMISSION) {
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
