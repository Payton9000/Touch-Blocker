package com.payton.touchblocker;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class TestBlockActivity extends AppCompatActivity {
    private static final String TAG = "TestBlockActivity";
    private TestBlockView testBlockView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OverlayPermission.ensure(this)) {
            finish();
            return;
        }
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_test_block);

        testBlockView = findViewById(R.id.test_block_view);
        refreshPoints();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPoints();
    }

    private void refreshPoints() {
        if (testBlockView == null) {
            return;
        }
        testBlockView.setBlockPoints(PointStore.loadPoints(this));
        testBlockView.setGlobalSizePx(PointStore.getGlobalSizePx(this));
        testBlockView.setDebugOverlayEnabled(PointStore.isDebugOverlayEnabled(this));
    }
}
