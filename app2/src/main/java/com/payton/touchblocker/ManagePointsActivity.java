package com.payton.touchblocker;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.profile.ActiveProfiles;
import com.payton.touchblocker.profile.FoldableUi;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfilePoint;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ProfileRevalidator;
import com.payton.touchblocker.profile.ScreenProfile;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;
import com.payton.touchblocker.ui.FullscreenWindow;

import java.util.ArrayList;
import java.util.List;

/** Edits the active display profile without requiring overlay permission. */
public class ManagePointsActivity extends AppCompatActivity {
    private static final String TAG = "ManagePointsActivity";

    private final WindowLayoutInfoObserver windowLayoutInfoObserver =
            new WindowLayoutInfoObserver();
    private final DisplaySnapshotProvider displaySnapshotProvider =
            new AndroidDisplaySnapshotProvider(
                    new DisplayGenerationTracker(), windowLayoutInfoObserver);
    private final ProfileRevalidator revalidator = new ProfileRevalidator();

    private TextView globalSizeView;
    private Slider globalSizeSlider;
    private PreviewCircleView globalPreview;
    private Chip profileChip;
    private PointRowAdapter pointAdapter;
    private ProfileRepository repository;
    private ProfileDocument profileDocument;
    private ScreenProfile activeProfile;
    private DisplaySnapshot currentSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenWindow.apply(this);
        setContentView(R.layout.activity_manage_points);

        repository = new ProfileRepository(
                new SharedPreferencesKeyValueStore(PointStore.prefs(this)),
                new ProfileJsonCodec());
        bindViews();
        bindControls();
    }

    @Override
    protected void onStart() {
        super.onStart();
        windowLayoutInfoObserver.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProfile();
    }

    @Override
    protected void onStop() {
        windowLayoutInfoObserver.stop();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            FullscreenWindow.apply(this);
        }
    }

    private void bindViews() {
        globalSizeView = findViewById(R.id.tv_global_size);
        globalSizeSlider = findViewById(R.id.slider_global_size);
        globalPreview = findViewById(R.id.preview_global);
        profileChip = findViewById(R.id.profile_chip);
        RecyclerView pointsList = findViewById(R.id.points_list);
        pointsList.setLayoutManager(new LinearLayoutManager(this));
        pointsList.setNestedScrollingEnabled(false);
        pointAdapter = new PointRowAdapter(new PointRowAdapter.Listener() {
            @Override
            public void onEnableChanged(int pointId, boolean enabled) {
                updatePointEnabled(pointId, enabled);
            }

            @Override
            public void onSizeCommitted(int pointId, float dpOrZero) {
                updatePointSize(pointId, dpOrZero);
            }

            @Override
            public void onDelete(int pointId) {
                deletePoint(pointId);
            }
        });
        pointsList.setAdapter(pointAdapter);
    }

    private void bindControls() {
        globalSizeSlider.addOnChangeListener((slider, value, fromUser) ->
                renderGlobalSize(value));
        globalSizeSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                if (activeProfile == null) {
                    return;
                }
                float diameterDp = slider.getValue();
                if (diameterDp == activeProfile.getGlobalDiameterDp()) {
                    return;
                }
                ScreenProfile updated = new ScreenProfile(
                        activeProfile.getId(),
                        activeProfile.getKind(),
                        diameterDp,
                        activeProfile.getPoints(),
                        activeProfile.getFingerprints(),
                        activeProfile.getReferenceRegions(),
                        activeProfile.getReferenceUnsafeAreas());
                saveProfile(updated);
            }
        });
        MaterialButton recordButton = findViewById(R.id.btn_record_new_point);
        recordButton.setOnClickListener(view ->
                startActivity(new Intent(ManagePointsActivity.this, RecordingActivity.class)));
    }

    private void refreshProfile() {
        try {
            currentSnapshot = captureSnapshotSafely();
            if (currentSnapshot == null) {
                clearProfile();
                return;
            }
            ProfileKind migrationKind = currentSnapshot.getSuggestedKind() == null
                    ? ProfileKind.OUTER : currentSnapshot.getSuggestedKind();
            ProfileRepository.LoadResult loaded = repository.migrateIfNeeded(
                    PointStore.getRawPointsJson(this),
                    PointStore.getGlobalSizePx(this),
                    currentSnapshot,
                    migrationKind);
            if (!loaded.isSuccess()) {
                Log.w(TAG, "Unable to load profiles: " + loaded.getError());
                clearProfile();
                return;
            }

            ActiveProfiles.Active active = ActiveProfiles.ensure(
                    loaded.getDocument(), currentSnapshot);
            if (active == null) {
                Log.w(TAG, "No unambiguous profile for the current display");
                clearProfile();
                return;
            }
            profileDocument = active.getDocument();
            if (active.isCreated() && !repository.save(profileDocument)) {
                Log.w(TAG, "Unable to save newly created display profile");
                clearProfile();
                return;
            }
            activeProfile = profileDocument.getProfiles().get(active.getProfileId());
            if (activeProfile == null) {
                clearProfile();
                return;
            }
            renderProfile();
        } catch (RuntimeException failure) {
            Log.e(TAG, "Unable to render the current display profile", failure);
            clearProfile();
        }
    }

    private void clearProfile() {
        profileDocument = null;
        activeProfile = null;
        profileChip.setVisibility(View.GONE);
        pointAdapter.submit(new ArrayList<ProfilePoint>(),
                ScreenProfile.DEFAULT_GLOBAL_DIAMETER_DP);
    }

    private void renderProfile() {
        boolean showProfileChip = FoldableUi.shouldShowProfileControls(
                profileDocument, currentSnapshot);
        profileChip.setVisibility(showProfileChip ? View.VISIBLE : View.GONE);
        profileChip.setText(activeProfile.getKind() == ProfileKind.INNER
                ? R.string.profile_chip_inner : R.string.profile_chip_outer);
        float sliderDiameterDp = sliderValueForGlobalDiameter(
                activeProfile.getGlobalDiameterDp());
        globalSizeSlider.setValue(sliderDiameterDp);
        renderGlobalSize(sliderDiameterDp);
        pointAdapter.submit(activeProfile.getPoints(), sliderDiameterDp);
    }

    private void renderGlobalSize(float diameterDp) {
        int roundedDp = Math.round(diameterDp);
        globalSizeView.setText(getString(R.string.global_size_dp, roundedDp));
        globalPreview.setDiameterDp(diameterDp);
    }

    /** Material Slider uses 1dp increments, while profiles migrated from pixels can be fractional. */
    static float sliderValueForGlobalDiameter(float diameterDp) {
        return Math.max(24f, Math.min(200f, Math.round(diameterDp)));
    }

    private void updatePointEnabled(int pointId, boolean enabled) {
        if (activeProfile == null) {
            return;
        }
        DisplaySnapshot snapshot = captureSnapshotSafely();
        if (enabled && snapshot == null) {
            Toast.makeText(this, R.string.reason_invalid_data, Toast.LENGTH_SHORT).show();
            pointAdapter.submit(activeProfile.getPoints(), activeProfile.getGlobalDiameterDp());
            return;
        }
        if (snapshot != null) {
            currentSnapshot = snapshot;
        }
        ScreenProfile updated = revalidator.setPointEnabled(
                activeProfile, pointId, enabled, currentSnapshot);
        ProfilePoint changed = findPoint(updated, pointId);
        if (changed == null) {
            return;
        }
        if (enabled && !changed.isEnabled()) {
            int reason = disabledReasonString(changed.getDisabledReason());
            if (reason != 0) {
                Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
            }
        }
        saveProfile(updated);
    }

    private void updatePointSize(int pointId, float dpOrZero) {
        if (activeProfile == null) {
            return;
        }
        List<ProfilePoint> points = new ArrayList<>(activeProfile.getPoints());
        for (int index = 0; index < points.size(); index++) {
            if (points.get(index).getId() == pointId) {
                if (points.get(index).getDiameterDpOverride() == dpOrZero) {
                    return;
                }
                points.set(index, points.get(index).withDiameterDpOverride(dpOrZero));
                saveProfile(copyWithPoints(points));
                return;
            }
        }
    }

    private void deletePoint(int pointId) {
        if (activeProfile == null) {
            return;
        }
        List<ProfilePoint> points = new ArrayList<>(activeProfile.getPoints());
        for (int index = 0; index < points.size(); index++) {
            if (points.get(index).getId() == pointId) {
                points.remove(index);
                saveProfile(copyWithPoints(points));
                return;
            }
        }
    }

    private ScreenProfile copyWithPoints(List<ProfilePoint> points) {
        return new ScreenProfile(
                activeProfile.getId(),
                activeProfile.getKind(),
                activeProfile.getGlobalDiameterDp(),
                points,
                activeProfile.getFingerprints(),
                activeProfile.getReferenceRegions(),
                activeProfile.getReferenceUnsafeAreas());
    }

    private void saveProfile(ScreenProfile updated) {
        ProfileDocument updatedDocument = profileDocument.withProfile(updated);
        if (!repository.save(updatedDocument)) {
            Log.w(TAG, "Unable to save profile update");
            return;
        }
        profileDocument = updatedDocument;
        activeProfile = updated;
        renderProfile();
        refreshOverlayService();
    }

    private void refreshOverlayService() {
        if (!PointStore.shouldOverlayBeEnabled(this)) {
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_REFRESH_POINTS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    private static ProfilePoint findPoint(ScreenProfile profile, int pointId) {
        for (ProfilePoint point : profile.getPoints()) {
            if (point.getId() == pointId) {
                return point;
            }
        }
        return null;
    }

    static int disabledReasonString(PointDisabledReason reason) {
        if (reason == PointDisabledReason.INVALID_DATA) {
            return R.string.reason_invalid_data;
        }
        if (reason == PointDisabledReason.NEEDS_REVIEW) {
            return R.string.reason_needs_review;
        }
        if (reason == PointDisabledReason.OUT_OF_BOUNDS
                || reason == PointDisabledReason.CUTOUT
                || reason == PointDisabledReason.HINGE) {
            return R.string.reason_out_of_bounds;
        }
        return 0;
    }
}
