package com.pocketlab.app.ui.recorder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.pocketlab.app.R;
import com.pocketlab.app.analysis.PeriodResult;
import com.pocketlab.app.analysis.SummaryStats;
import com.pocketlab.app.databinding.FragmentRecorderBinding;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;
import com.pocketlab.app.recording.RecordingSummary;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;
import com.pocketlab.app.sensor.ValueFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Free recorder: pick a sensor and rate, record, pause/resume, add markers, watch the graph. */
public class RecorderFragment extends Fragment {

    private static final long REFRESH_MS = 50;
    private static final int[] AXIS_COLORS = {0xFFE53935, 0xFF43A047, 0xFF1E88E5};

    private FragmentRecorderBinding binding;
    private RecorderViewModel vm;
    private List<SensorType> available;
    @Nullable private RecordingSession boundSession;
    @Nullable private RecordingSession summarisedSession;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRecorderBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(requireActivity()).get(RecorderViewModel.class);
        available = vm.getAvailableTypes();

        if (available.isEmpty()) {
            binding.content.setVisibility(View.GONE);
            binding.emptyText.setVisibility(View.VISIBLE);
            return binding.getRoot();
        }

        setUpDropdowns();
        binding.btnPrimary.setOnClickListener(v -> onPrimaryClicked());
        binding.btnStop.setOnClickListener(v -> {
            vm.stop();
            refresh();
        });
        binding.btnMarker.setOnClickListener(v -> vm.addMarker());
        return binding.getRoot();
    }

    private void setUpDropdowns() {
        List<String> sensorNames = new ArrayList<>();
        for (SensorType t : available) sensorNames.add(t.getDisplayName());
        binding.sensorDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, sensorNames));
        SensorType selected = vm.getSelectedType();
        if (selected != null) binding.sensorDropdown.setText(selected.getDisplayName(), false);
        binding.sensorDropdown.setOnItemClickListener((p, v, pos, id) ->
                vm.setSelectedType(available.get(pos)));

        SamplingRate[] rates = SamplingRate.values();
        List<String> rateNames = new ArrayList<>();
        for (SamplingRate r : rates) rateNames.add(rateLabel(r));
        binding.rateDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, rateNames));
        binding.rateDropdown.setText(rateLabel(vm.getSelectedRate()), false);
        binding.rateDropdown.setOnItemClickListener((p, v, pos, id) ->
                vm.setSelectedRate(rates[pos]));
    }

    private String rateLabel(SamplingRate r) {
        return r.getPeriodUs() == 0
                ? r.getLabel() + " (device max)"
                : String.format(Locale.US, "%s (~%.0f Hz)", r.getLabel(), r.getNominalHz());
    }

    private void onPrimaryClicked() {
        boolean ok = true;
        switch (vm.getState()) {
            case RECORDING:
                vm.pause();
                break;
            case PAUSED:
                ok = vm.resume();
                break;
            default:
                ok = vm.start();
                break;
        }
        if (!ok) {
            Snackbar.make(binding.getRoot(), R.string.recorder_start_failed, Snackbar.LENGTH_SHORT).show();
        }
        refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (available != null && !available.isEmpty()) handler.post(ticker);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
    }

    /** Pushes the ViewModel state into the views. Cheap; runs every 50 ms. */
    private void refresh() {
        if (binding == null) return;
        RecordingSession s = vm.getSession();
        RecordingState st = vm.getState();
        boolean active = st == RecordingState.RECORDING || st == RecordingState.PAUSED;

        // Controls
        binding.btnPrimary.setText(st == RecordingState.RECORDING ? R.string.recorder_pause
                : st == RecordingState.PAUSED ? R.string.recorder_resume : R.string.recorder_start);
        binding.btnStop.setEnabled(active);
        binding.btnMarker.setEnabled(st == RecordingState.RECORDING && s != null && s.hasSamples());
        binding.sensorLayout.setEnabled(!active);
        binding.rateLayout.setEnabled(!active);
        binding.status.setText(statusLabel(st));

        if (s == null) {
            binding.analysis.setVisibility(View.GONE);
            binding.elapsed.setText(TimeFormat.format(0));
            binding.values.setText("");
            binding.stats.setText("");
            return;
        }

        // Bind the graph once per session
        if (s != boundSession) {
            boundSession = s;
            SensorType type = s.getType();
            List<String> labels = new ArrayList<>();
            int[] colors;
            if (type.getAxes() == 3) {
                colors = AXIS_COLORS;
                for (int i = 0; i < 3; i++) labels.add(type.getAxisLabel(i));
            } else {
                colors = new int[]{MaterialColors.getColor(binding.getRoot(),
                        com.google.android.material.R.attr.colorPrimary, 0xFF006A6A)};
                labels.add(type.getDisplayName());
            }
            binding.graph.setData(s.getSeries(), labels, colors, s.getMarkers(), type.getUnit());
            binding.graph.resetView();
        }

        binding.elapsed.setText(TimeFormat.format(s.getElapsedSeconds()));
        if (s.hasSamples()) {
            binding.values.setText(ValueFormatter.format(s.getType(), s.getLatestValues()));
        } else {
            binding.values.setText(R.string.sensor_waiting);
        }
        binding.stats.setText(s.isGraphTruncated()
                ? getString(R.string.recorder_stats_truncated, s.getTotalSamples(), s.getMeasuredHz(), s.getCapacity())
                : getString(R.string.recorder_stats, s.getTotalSamples(), s.getMeasuredHz()));

        if (st == RecordingState.RECORDING) binding.graph.invalidate();

        // Summary appears once, when the recording is stopped.
        if (st == RecordingState.STOPPED && s.hasSamples()) {
            if (summarisedSession != s) {
                summarisedSession = s;
                binding.analysis.setText(formatSummary(s, RecordingSummary.of(s)));
            }
            binding.analysis.setVisibility(View.VISIBLE);
        } else {
            binding.analysis.setVisibility(View.GONE);
        }
    }

    private String formatSummary(RecordingSession s, RecordingSummary sum) {
        SensorType type = s.getType();
        String fmt = "%." + type.getDecimals() + "f";
        StringBuilder sb = new StringBuilder(getString(R.string.analysis_header));
        for (RecordingSummary.Axis a : sum.getAxes()) {
            SummaryStats st = a.getStats();
            String sd = Double.isNaN(st.getStdDev()) ? "–" : String.format(Locale.US, fmt, st.getStdDev());
            sb.append('\n').append(getString(R.string.analysis_axis_line,
                    axisName(type, a.getIndex()),
                    String.format(Locale.US, fmt, st.getMean()), sd,
                    String.format(Locale.US, fmt, st.getMin()),
                    String.format(Locale.US, fmt, st.getMax()), type.getUnit()));
        }
        PeriodResult p = sum.getPeriod();
        if (p != null) {
            sb.append('\n').append(getString(R.string.analysis_period,
                    axisName(type, sum.getPeriodAxis()), p.getPeriodSeconds(),
                    p.getStdDevSeconds(), p.getPeriodCount(), p.getFrequencyHz()));
        }
        if (sum.isTruncated()) {
            sb.append('\n').append(getString(R.string.analysis_note, sum.getSamplesUsed()));
        }
        return sb.toString();
    }

    private static String axisName(SensorType type, int index) {
        return type.getAxes() > 1 ? type.getAxisLabel(index) : type.getDisplayName();
    }

    private int statusLabel(RecordingState st) {
        switch (st) {
            case RECORDING: return R.string.state_recording;
            case PAUSED: return R.string.state_paused;
            case STOPPED: return R.string.state_stopped;
            default: return R.string.state_ready;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        boundSession = null;
        summarisedSession = null;
        binding = null;
    }
}
