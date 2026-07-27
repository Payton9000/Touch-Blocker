package com.payton.touchblocker;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.geometry.CoordinateTransformer;
import com.payton.touchblocker.geometry.ResolvedPoint;
import com.payton.touchblocker.profile.ActiveProfiles;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ScreenProfile;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;

import java.util.ArrayList;
import java.util.List;

public class RecordingActivity extends AppCompatActivity {
    private static final String TAG = "RecordingActivity";
    private static final String RECORDING_ENABLED = "recording_enabled";
    private static final String PREFERENCES_NAME = "touch_blocker_prefs";
    private static final long FOLD_LAYOUT_FALLBACK_DELAY_MS = 750L;

    private TextView tvHint;
    private TextView tvRecordCount;
    private TouchRecordView recordView;
    private MaterialButton startButton;
    private MaterialButton stopButton;
    private MaterialButton undoButton;

    private boolean recording;
    private long currentDownTime;
    private float currentDownX;
    private float currentDownY;
    private long currentDownGeneration = -1L;

    private final WindowLayoutInfoObserver windowLayoutInfoObserver =
            new WindowLayoutInfoObserver();
    private final DisplaySnapshotProvider displaySnapshotProvider =
            new AndroidDisplaySnapshotProvider(
                    new DisplayGenerationTracker(), windowLayoutInfoObserver);
    private DisplaySnapshot displaySnapshot;
    private RecordingGestureTracker gestureTracker;
    private ProfileRepository repository;
    private ProfileDocument profileDocument;
    private ScreenProfile activeProfile;
    private RecordingSessionModel sessionModel;
    private final RecordingSessionStartup sessionStartup = new RecordingSessionStartup();
    private final Handler initializationHandler = new Handler(Looper.getMainLooper());
    private final Runnable layoutInitializationFallback = new Runnable() {
        @Override
        public void run() {
            initializeSessionAfterTimeout();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        tvHint = findViewById(R.id.tv_record_hint);
        tvRecordCount = findViewById(R.id.tv_record_count);
        recordView = findViewById(R.id.record_view);
        startButton = findViewById(R.id.btn_record_start);
        stopButton = findViewById(R.id.btn_record_stop);
        undoButton = findViewById(R.id.btn_record_undo);
        gestureTracker = new RecordingGestureTracker(
                ViewConfiguration.get(this).getScaledTouchSlop());
        recording = savedInstanceState != null
                && savedInstanceState.getBoolean(RECORDING_ENABLED, false);

        bindControls();
        setSessionControlsEnabled(false);
        tvRecordCount.setText(getString(R.string.recording_count, 0));
        tvHint.setText(getString(recording ? R.string.recording_on : R.string.recording_off_start));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(RECORDING_ENABLED, recording);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            startRecording();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            stopAndSave();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onStart() {
        super.onStart();
        sessionStartup.resumeAfterLifecycleStart();
        windowLayoutInfoObserver.start(this, new Runnable() {
            @Override
            public void run() {
                initializeSessionAfterFirstLayout();
            }
        });
        if (sessionStartup.isWaitingForInitialization()) {
            initializationHandler.postDelayed(
                    layoutInitializationFallback, FOLD_LAYOUT_FALLBACK_DELAY_MS);
        }
    }

    @Override
    protected void onStop() {
        cancelPendingLayoutInitialization();
        windowLayoutInfoObserver.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        cancelPendingLayoutInitialization();
        windowLayoutInfoObserver.stop();
        super.onDestroy();
    }

    private boolean initializeSession() {
        displaySnapshot = captureSnapshotSafely();
        if (displaySnapshot == null) {
            Log.w(TAG, "Unable to start recording without a display snapshot");
            finish();
            return false;
        }

        repository = new ProfileRepository(
                new SharedPreferencesKeyValueStore(
                        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)),
                new ProfileJsonCodec());
        ProfileKind migrationKind = displaySnapshot.getSuggestedKind() == null
                ? ProfileKind.OUTER : displaySnapshot.getSuggestedKind();
        ProfileRepository.LoadResult loaded = repository.migrateIfNeeded(
                PointStore.getRawPointsJson(this),
                PointStore.getGlobalSizePx(this),
                displaySnapshot,
                migrationKind);
        if (!loaded.isSuccess()) {
            Log.w(TAG, "Unable to load recording profiles: " + loaded.getError());
            finish();
            return false;
        }

        ActiveProfiles.Active active = sessionStartup.ensureActiveProfile(
                loaded.getDocument(), displaySnapshot);
        if (active == null) {
            Toast.makeText(this, R.string.recording_profile_ambiguous, Toast.LENGTH_SHORT).show();
            finish();
            return false;
        }
        profileDocument = active.getDocument();
        if (active.isCreated() && !repository.save(profileDocument)) {
            Log.w(TAG, "Unable to save the newly created recording profile");
            finish();
            return false;
        }
        activeProfile = profileDocument.getProfiles().get(active.getProfileId());
        sessionModel = new RecordingSessionModel(activeProfile, displaySnapshot);
        return true;
    }

    private void initializeSessionAfterFirstLayout() {
        if (!sessionStartup.beginFromLayoutCallback()) {
            return;
        }
        initializationHandler.removeCallbacks(layoutInitializationFallback);
        initializeSessionAfterWinningInitializationRace();
    }

    private void initializeSessionAfterTimeout() {
        if (!sessionStartup.beginFromTimeout()) {
            return;
        }
        initializeSessionAfterWinningInitializationRace();
    }

    private void initializeSessionAfterWinningInitializationRace() {
        if (!initializeSession()) {
            return;
        }
        setSessionControlsEnabled(true);
        refreshRecordingFeedback();
        tvHint.setText(getString(recording ? R.string.recording_on : R.string.recording_off_start));
    }

    private void cancelPendingLayoutInitialization() {
        initializationHandler.removeCallbacks(layoutInitializationFallback);
        sessionStartup.cancelPendingInitialization();
    }

    private void bindControls() {
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecording();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopAndSave();
            }
        });
        undoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (sessionModel != null && sessionModel.undoLast()) {
                    refreshRecordingFeedback();
                }
            }
        });

        View touchLayer = findViewById(R.id.record_touch_layer);
        touchLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                return handleTouch(view, event);
            }
        });
    }

    private boolean handleTouch(View view, MotionEvent event) {
        if (!recording || sessionModel == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            gestureTracker.onDown(event.getRawX(), event.getRawY());
            currentDownTime = System.currentTimeMillis();
            currentDownX = event.getRawX();
            currentDownY = event.getRawY();
            DisplaySnapshot downSnapshot = captureSnapshotSafely();
            if (downSnapshot != null) {
                displaySnapshot = downSnapshot;
                currentDownGeneration = downSnapshot.getGeneration();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            gestureTracker.onMove(event.getRawX(), event.getRawY());
        } else if (action == MotionEvent.ACTION_UP) {
            view.performClick();
            if (!gestureTracker.finishAsTap()) {
                tvHint.setText(getString(R.string.recording_swipe_ignored));
                return true;
            }
            DisplaySnapshot upSnapshot = captureSnapshotSafely();
            if (upSnapshot != null && currentDownGeneration != upSnapshot.getGeneration()) {
                resetSessionForDisplayChange(upSnapshot);
                return true;
            }
            if (upSnapshot != null) {
                displaySnapshot = upSnapshot;
            }
            long duration = Math.max(0L, System.currentTimeMillis() - currentDownTime);
            RecordingSessionModel.Outcome outcome = sessionModel.onTap(
                    currentDownX, currentDownY, currentDownTime, duration);
            switch (outcome) {
                case NEW_POINT:
                    tvHint.setText(getString(R.string.recording_count,
                            sessionModel.getNewPointCount()));
                    break;
                case MERGED:
                case COVERED_EXISTING:
                    tvHint.setText(getString(R.string.recording_skipped_covered,
                            sessionModel.getSkippedCount()));
                    break;
                case LIMIT_REACHED:
                    tvHint.setText(getString(R.string.recording_limit_reached));
                    break;
                default:
                    break;
            }
            refreshRecordingFeedback();
        } else if (action == MotionEvent.ACTION_CANCEL) {
            gestureTracker.cancel();
        }
        return true;
    }

    private void startRecording() {
        if (sessionModel == null) {
            return;
        }
        recording = true;
        tvHint.setText(getString(R.string.recording_on));
    }

    private void stopAndSave() {
        if (sessionModel == null) {
            return;
        }
        recording = false;
        saveSession();
        finish();
    }

    private void resetSessionForDisplayChange(DisplaySnapshot snapshot) {
        displaySnapshot = snapshot;
        sessionModel = new RecordingSessionModel(activeProfile, snapshot);
        tvHint.setText(getString(R.string.recording_display_changed));
        refreshRecordingFeedback();
    }

    private void refreshRecordingFeedback() {
        tvRecordCount.setText(getString(R.string.recording_count,
                sessionModel.getNewPointCount()));
        undoButton.setEnabled(sessionModel.getNewPointCount() > 0);
        ArrayList<float[]> resolved = new ArrayList<>();
        for (ProfilePoint point : activeProfile.getPoints()) {
            if (!point.isEnabled()) {
                continue;
            }
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride() : activeProfile.getGlobalDiameterDp();
            ResolvedPoint resolvedPoint = CoordinateTransformer.resolve(
                    displaySnapshot, point, diameterDp);
            if (resolvedPoint != null) {
                resolved.add(new float[]{
                        resolvedPoint.getCenterX(),
                        resolvedPoint.getCenterY(),
                        resolvedPoint.getDiameterPx() / 2f});
            }
        }
        resolved.addAll(sessionModel.getSessionCentersAndRadiiPx());
        recordView.setResolved(resolved);
    }

    private void setSessionControlsEnabled(boolean enabled) {
        startButton.setEnabled(enabled);
        stopButton.setEnabled(enabled);
        undoButton.setEnabled(enabled && sessionModel != null
                && sessionModel.getNewPointCount() > 0);
    }

    private void saveSession() {
        if (sessionModel.getNewPointCount() == 0) {
            return;
        }
        ScreenProfile updated = sessionModel.buildUpdatedProfile();
        ProfileDocument updatedDocument = profileDocument.withProfile(updated);
        if (!repository.save(updatedDocument)) {
            Log.w(TAG, "Unable to save recorded points");
            return;
        }
        int firstNewPoint = activeProfile.getPoints().size();
        for (int index = firstNewPoint; index < updated.getPoints().size(); index++) {
            ProfilePoint point = updated.getPoints().get(index);
            float diameterDp = point.getDiameterDpOverride() > 0f
                    ? point.getDiameterDpOverride() : updated.getGlobalDiameterDp();
            ResolvedPoint resolvedPoint = CoordinateTransformer.resolve(
                    displaySnapshot, point, diameterDp);
            if (resolvedPoint != null) {
                LogManager.appendPoint(this, updated.getId(), point, resolvedPoint);
            }
        }
        profileDocument = updatedDocument;
        activeProfile = updated;
        notifyOverlayRefresh();
    }

    private void notifyOverlayRefresh() {
        if (!PointStore.shouldOverlayBeEnabled(this)) {
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_REFRESH_POINTS);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private DisplaySnapshot captureSnapshotSafely() {
        try {
            return displaySnapshotProvider.capture(this);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Unable to capture display snapshot", failure);
            return null;
        }
    }
}
