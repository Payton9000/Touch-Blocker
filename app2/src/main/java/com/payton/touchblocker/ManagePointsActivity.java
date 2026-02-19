package com.payton.touchblocker;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class ManagePointsActivity extends AppCompatActivity {
    private TextView tvGlobalSize;
    private SeekBar sbGlobalSize;
    private PreviewCircleView globalPreview;
    private LinearLayout listContainer;

    private static final String TAG = "ManagePointsActivity";

    private List<TouchPoint> points;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OverlayPermission.ensure(this)) {
            finish();
            return;
        }
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_manage_points);

        tvGlobalSize = findViewById(R.id.tv_global_size);
        sbGlobalSize = findViewById(R.id.sb_global_size);
        globalPreview = findViewById(R.id.preview_global);
        listContainer = findViewById(R.id.list_container);

        points = PointStore.loadPoints(this);
        int globalSize = PointStore.getGlobalSizePx(this);
        applyGlobalSize(globalSize);

        sbGlobalSize.setProgress(globalSize);
        sbGlobalSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = Math.max(30, progress);
                PointStore.setGlobalSizePx(ManagePointsActivity.this, size);
                applyGlobalSize(size);
                notifyOverlayRefresh();
                Log.d(TAG, "Global size=" + size);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        renderPointRows();
    }

    private void applyGlobalSize(int sizePx) {
        tvGlobalSize.setText("Global size: " + sizePx + "px");
        globalPreview.setDiameterPx(sizePx);
    }

    private void renderPointRows() {
        listContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final TouchPoint point : points) {
            View row = inflater.inflate(R.layout.item_point_row, listContainer, false);
            TextView tvTitle = row.findViewById(R.id.tv_point_title);
            final Switch swEnabled = row.findViewById(R.id.sw_enabled);
            final SeekBar sbSize = row.findViewById(R.id.sb_point_size);
            final TextView tvSize = row.findViewById(R.id.tv_point_size);
            final PreviewCircleView preview = row.findViewById(R.id.preview_point);
            View btnDelete = row.findViewById(R.id.btn_delete);

            tvTitle.setText("Point #" + point.getId());
            swEnabled.setChecked(point.isEnabled());

            int currentSize = point.getSizeOverridePx() > 0 ? point.getSizeOverridePx() : 0;
            sbSize.setProgress(Math.max(0, currentSize));
            updatePointSizeLabel(tvSize, currentSize);
            preview.setDiameterPx(currentSize > 0 ? currentSize : PointStore.getGlobalSizePx(this));

            swEnabled.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                    point.setEnabled(isChecked);
                    PointStore.updatePoint(ManagePointsActivity.this, point);
                    notifyOverlayRefresh();
                    Log.d(TAG, "Point " + point.getId() + " enabled=" + isChecked);
                }
            });

            sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int size = Math.max(0, progress);
                    point.setSizeOverridePx(size == 0 ? -1 : size);
                    PointStore.updatePoint(ManagePointsActivity.this, point);
                    updatePointSizeLabel(tvSize, size);
                    int previewSize = size > 0 ? size : PointStore.getGlobalSizePx(ManagePointsActivity.this);
                    preview.setDiameterPx(previewSize);
                    notifyOverlayRefresh();
                    Log.d(TAG, "Point " + point.getId() + " size=" + size);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            btnDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PointStore.deletePoint(ManagePointsActivity.this, point.getId());
                    points.remove(point);
                    notifyOverlayRefresh();
                    renderPointRows();
                    Log.d(TAG, "Point " + point.getId() + " deleted");
                }
            });

            listContainer.addView(row);
        }
    }

    private void updatePointSizeLabel(TextView tvSize, int size) {
        if (size <= 0) {
            tvSize.setText("Size: Global");
        } else {
            tvSize.setText("Size: " + size + "px");
        }
    }

    private void notifyOverlayRefresh() {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_REFRESH_POINTS);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}
