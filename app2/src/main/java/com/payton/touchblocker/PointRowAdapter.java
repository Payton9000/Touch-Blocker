package com.payton.touchblocker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.payton.touchblocker.profile.PointDisabledReason;
import com.payton.touchblocker.profile.ProfilePoint;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView binding for immutable profile points. */
public final class PointRowAdapter extends RecyclerView.Adapter<PointRowAdapter.Holder> {
    private final Listener listener;
    private final List<ProfilePoint> points = new ArrayList<>();
    private float globalDp = 48f;

    public interface Listener {
        void onEnableChanged(int pointId, boolean enabled);

        void onSizeCommitted(int pointId, float dpOrZero);

        void onDelete(int pointId);
    }

    public PointRowAdapter(Listener listener) {
        if (listener == null) {
            throw new NullPointerException("listener == null");
        }
        this.listener = listener;
    }

    public void submit(List<ProfilePoint> newPoints, float newGlobalDp) {
        points.clear();
        points.addAll(newPoints);
        globalDp = newGlobalDp;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_point_row, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ProfilePoint point = points.get(position);
        holder.title.setText(holder.itemView.getContext().getString(
                R.string.point_title_region, point.getId(), point.getRegionId()));
        holder.enabled.setOnCheckedChangeListener(null);
        holder.enabled.setChecked(point.isEnabled());
        holder.enabled.setOnCheckedChangeListener((button, checked) ->
                listener.onEnableChanged(point.getId(), checked));

        int reasonString = ManagePointsActivity.disabledReasonString(point.getDisabledReason());
        if (point.isEnabled() || reasonString == 0) {
            holder.disabledReason.setVisibility(View.GONE);
            holder.disabledReason.setText("");
        } else {
            holder.disabledReason.setVisibility(View.VISIBLE);
            holder.disabledReason.setText(reasonString);
        }

        float overrideDp = normalizeOverride(point.getDiameterDpOverride());
        holder.sizeSlider.clearOnChangeListeners();
        holder.sizeSlider.clearOnSliderTouchListeners();
        holder.sizeSlider.setValue(overrideDp);
        renderSize(holder, overrideDp);
        holder.sizeSlider.addOnChangeListener((slider, value, fromUser) ->
                renderSize(holder, normalizeOverride(value)));
        holder.sizeSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(Slider slider) {
                float committed = normalizeOverride(slider.getValue());
                if (slider.getValue() != committed) {
                    slider.setValue(committed);
                }
                listener.onSizeCommitted(point.getId(), committed);
            }
        });
        holder.delete.setOnClickListener(view -> listener.onDelete(point.getId()));
    }

    @Override
    public int getItemCount() {
        return points.size();
    }

    private void renderSize(Holder holder, float overrideDp) {
        float displayedDp = overrideDp == 0f ? globalDp : overrideDp;
        holder.size.setText(overrideDp == 0f
                ? holder.itemView.getContext().getString(R.string.point_size_global)
                : holder.itemView.getContext().getString(R.string.point_size_dp,
                        Math.round(overrideDp)));
        holder.preview.setDiameterDp(displayedDp);
    }

    private static float normalizeOverride(float value) {
        return value < 24f ? 0f : Math.min(200f, Math.round(value));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView size;
        final TextView disabledReason;
        final MaterialSwitch enabled;
        final Slider sizeSlider;
        final PreviewCircleView preview;
        final MaterialButton delete;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tv_point_title);
            size = itemView.findViewById(R.id.tv_point_size);
            disabledReason = itemView.findViewById(R.id.tv_disabled_reason);
            enabled = itemView.findViewById(R.id.switch_point_enabled);
            sizeSlider = itemView.findViewById(R.id.slider_point_size);
            preview = itemView.findViewById(R.id.preview_point);
            delete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
