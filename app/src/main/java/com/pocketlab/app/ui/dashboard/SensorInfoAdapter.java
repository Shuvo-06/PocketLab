package com.pocketlab.app.ui.dashboard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.pocketlab.app.R;
import com.pocketlab.app.databinding.ItemSensorBinding;
import com.pocketlab.app.sensor.SensorInfo;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;
import com.pocketlab.app.sensor.ValueFormatter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/** Shows availability, live values, hardware info and measured rate for each sensor. */
class SensorInfoAdapter extends RecyclerView.Adapter<SensorInfoAdapter.Holder> {

    private final List<SensorRow> rows;
    private final DecimalFormat compact = new DecimalFormat("0.######",
            DecimalFormatSymbols.getInstance(Locale.US));

    SensorInfoAdapter(List<SensorRow> rows) {
        this.rows = rows;
    }

    /** Re-binds every card; called on a timer so UI work is decoupled from sensor rate. */
    void refreshAll() {
        notifyItemRangeChanged(0, rows.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemSensorBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        SensorRow row = rows.get(position);
        Context ctx = h.binding.getRoot().getContext();
        SensorType type = row.type;

        h.binding.sensorName.setText(type.getDisplayName());
        h.binding.sensorUnit.setText(type.getUnit());
        h.binding.getRoot().setAlpha(row.isAvailable() ? 1f : 0.55f);

        if (!row.isAvailable()) {
            h.binding.sensorStatus.setText(R.string.sensor_unavailable);
            h.binding.sensorStatus.setTextColor(MaterialColors.getColor(h.binding.getRoot(),
                    com.google.android.material.R.attr.colorError));
            h.binding.sensorValues.setText("—");
            h.binding.sensorDetails.setText("");
            h.binding.sensorStats.setText("");
            return;
        }

        h.binding.sensorStatus.setText(type.getDescription());
        h.binding.sensorStatus.setTextColor(MaterialColors.getColor(h.binding.getRoot(),
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        h.binding.sensorDetails.setText(describeHardware(ctx, row.info, type));

        SensorSample s = row.latest;
        if (s == null) {
            h.binding.sensorValues.setText(R.string.sensor_waiting);
            h.binding.sensorStats.setText("");
        } else {
            h.binding.sensorValues.setText(ValueFormatter.format(type, s.getValues()));
            h.binding.sensorStats.setText(ctx.getString(R.string.sensor_stats,
                    row.getHz(), accuracyLabel(ctx, s.getAccuracy())));
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String describeHardware(Context ctx, SensorInfo info, SensorType type) {
        int d = info.getMinDelayUs();
        String minDelay = d > 0 ? String.format(Locale.US, "%.1f ms", d / 1000.0)
                : d == 0 ? ctx.getString(R.string.min_delay_on_change)
                : ctx.getString(R.string.min_delay_na);
        return ctx.getString(R.string.sensor_details, info.getName(), info.getVendor(),
                compact.format(info.getMaxRange()), type.getUnit(),
                compact.format(info.getResolution()), minDelay);
    }

    private String accuracyLabel(Context ctx, int accuracy) {
        switch (accuracy) {
            case 3: return ctx.getString(R.string.accuracy_high);
            case 2: return ctx.getString(R.string.accuracy_medium);
            case 1: return ctx.getString(R.string.accuracy_low);
            case 0: return ctx.getString(R.string.accuracy_unreliable);
            default: return "—";
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemSensorBinding binding;

        Holder(ItemSensorBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
