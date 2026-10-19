package com.pocketlab.app.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.chip.Chip;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.databinding.FragmentDashboardBinding;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorHub;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Live sensor dashboard. Sensors are registered in onStart and released in onStop,
 * so nothing keeps running (or draining the battery) while the screen is hidden.
 */
public class DashboardFragment extends Fragment implements SensorHub.Listener {

    private static final long UI_REFRESH_MS = 100;

    private FragmentDashboardBinding binding;
    private SensorHub hub;
    private DashboardViewModel viewModel;
    private SensorInfoAdapter adapter;
    private final Map<SensorType, SensorRow> rowsByType = new EnumMap<>(SensorType.class);
    private final List<SensorHub.Subscription> subscriptions = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refreshTicker = new Runnable() {
        @Override
        public void run() {
            if (adapter != null) adapter.refreshAll();
            handler.postDelayed(this, UI_REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        hub = ((PocketLabApp) requireActivity().getApplication()).getSensorHub();
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        List<SensorRow> rows = new ArrayList<>();
        rowsByType.clear();
        for (SensorType type : SensorType.values()) {
            SensorRow row = new SensorRow(type, hub.getInfo(type));
            rows.add(row);
            rowsByType.put(type, row);
        }
        adapter = new SensorInfoAdapter(rows);
        binding.sensorList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.sensorList.setAdapter(adapter);
        ((SimpleItemAnimator) binding.sensorList.getItemAnimator()).setSupportsChangeAnimations(false);

        binding.summary.setText(getString(R.string.dashboard_summary,
                hub.getAvailableCount(), SensorType.values().length));
        setUpRateChips(inflater);
        return binding.getRoot();
    }

    private void setUpRateChips(LayoutInflater inflater) {
        SamplingRate selected = viewModel.getRate();
        for (SamplingRate rate : SamplingRate.values()) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_rate_chip, binding.rateChips, false);
            chip.setId(View.generateViewId());
            chip.setTag(rate);
            chip.setText(rate.getLabel());
            binding.rateChips.addView(chip);
            if (rate == selected) chip.setChecked(true);
        }
        binding.rateChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            Chip chip = group.findViewById(ids.get(0));
            viewModel.setRate((SamplingRate) chip.getTag());
            if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                stopSensors();
                startSensors();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        startSensors();
        handler.post(refreshTicker);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(refreshTicker);
        stopSensors();
    }

    private void startSensors() {
        for (SensorRow row : rowsByType.values()) row.reset();
        for (SensorType type : hub.getAvailableTypes()) {
            SensorHub.Subscription sub = hub.subscribe(type, viewModel.getRate(), this);
            if (sub != null) subscriptions.add(sub);
        }
    }

    private void stopSensors() {
        for (SensorHub.Subscription sub : subscriptions) sub.cancel();
        subscriptions.clear();
    }

    @Override
    public void onSample(@NonNull SensorSample sample) {
        SensorRow row = rowsByType.get(sample.getType());
        if (row != null) row.onSample(sample);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.sensorList.setAdapter(null);
        adapter = null;
        binding = null;
    }
}
