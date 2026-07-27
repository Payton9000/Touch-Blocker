package com.payton.touchblocker;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.payton.touchblocker.display.AndroidDisplaySnapshotProvider;
import com.payton.touchblocker.display.DisplayGenerationTracker;
import com.payton.touchblocker.display.DisplaySnapshot;
import com.payton.touchblocker.display.DisplaySnapshotProvider;
import com.payton.touchblocker.display.WindowLayoutInfoObserver;
import com.payton.touchblocker.geometry.OverlayClusterPlanner;
import com.payton.touchblocker.geometry.OverlayRefreshPlanner;
import com.payton.touchblocker.profile.ActiveProfiles;
import com.payton.touchblocker.profile.ProfileDocument;
import com.payton.touchblocker.profile.ProfileJsonCodec;
import com.payton.touchblocker.profile.ProfileKind;
import com.payton.touchblocker.profile.ProfileRepository;
import com.payton.touchblocker.profile.ScreenProfile;
import com.payton.touchblocker.profile.SharedPreferencesKeyValueStore;
import com.payton.touchblocker.ui.FullscreenWindow;

import java.util.Collections;
import java.util.List;

public class TestBlockActivity extends AppCompatActivity {
    private static final String TAG = "TestBlockActivity";
    private static final String PREFERENCES_NAME = "touch_blocker_prefs";

    private final WindowLayoutInfoObserver windowLayoutInfoObserver =
            new WindowLayoutInfoObserver();
    private final DisplaySnapshotProvider displaySnapshotProvider =
            new AndroidDisplaySnapshotProvider(
                    new DisplayGenerationTracker(), windowLayoutInfoObserver);

    private TestBlockView testBlockView;
    private TextView statsView;
    private ProfileRepository profileRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FullscreenWindow.apply(this);
        setContentView(R.layout.activity_test_block);

        SharedPreferences preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        profileRepository = new ProfileRepository(
                new SharedPreferencesKeyValueStore(preferences), new ProfileJsonCodec());
        testBlockView = findViewById(R.id.test_block_view);
        statsView = findViewById(R.id.tv_test_stats);
        testBlockView.setOnStatsChangedListener(new TestBlockView.OnStatsChangedListener() {
            @Override
            public void onStatsChanged(int blocked, int passed) {
                statsView.setText(getString(R.string.test_stats, blocked, passed));
            }
        });
        refreshPlans();
    }

    @Override
    protected void onStart() {
        super.onStart();
        windowLayoutInfoObserver.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPlans();
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshPlans();
    }

    private void refreshPlans() {
        if (testBlockView == null || profileRepository == null) {
            return;
        }
        DisplaySnapshot snapshot = captureSnapshotSafely();
        if (snapshot == null) {
            testBlockView.setPlans(Collections.<OverlayClusterPlanner.WindowPlan>emptyList());
            return;
        }

        ProfileKind kind = snapshot.getSuggestedKind() == null
                ? ProfileKind.OUTER : snapshot.getSuggestedKind();
        ProfileRepository.LoadResult loadResult = profileRepository.migrateIfNeeded(
                PointStore.getRawPointsJson(this),
                PointStore.getGlobalSizePx(this),
                snapshot,
                kind);
        if (!loadResult.isSuccess()) {
            Log.w(TAG, "Unable to load profiles for test screen: " + loadResult.getError());
            testBlockView.setPlans(Collections.<OverlayClusterPlanner.WindowPlan>emptyList());
            return;
        }

        ActiveProfiles.Active active = ActiveProfiles.ensure(loadResult.getDocument(), snapshot);
        if (active == null) {
            Log.w(TAG, "No unambiguous profile for the current display");
            testBlockView.setPlans(Collections.<OverlayClusterPlanner.WindowPlan>emptyList());
            return;
        }
        ProfileDocument document = active.getDocument();
        if (active.isCreated() && !profileRepository.save(document)) {
            Log.w(TAG, "Unable to save the new display profile");
        }
        ScreenProfile profile = document.getProfiles().get(active.getProfileId());
        if (profile == null) {
            Log.w(TAG, "Selected profile was missing from the document");
            testBlockView.setPlans(Collections.<OverlayClusterPlanner.WindowPlan>emptyList());
            return;
        }
        List<OverlayClusterPlanner.WindowPlan> plans = OverlayRefreshPlanner.plan(snapshot, profile);
        testBlockView.setPlans(plans);
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
