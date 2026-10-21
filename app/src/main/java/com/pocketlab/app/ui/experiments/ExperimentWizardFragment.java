package com.pocketlab.app.ui.experiments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.pocketlab.app.R;
import com.pocketlab.app.databinding.FragmentExperimentWizardBinding;
import com.pocketlab.app.experiment.ExperimentDefinition;
import com.pocketlab.app.experiment.ExperimentRepository;
import com.pocketlab.app.experiment.ExperimentResult;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.sensor.SensorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Step-by-step experiment wizard: intro, instructions, inputs, record, results.
 * All state lives in {@link ExperimentViewModel}; this class only renders it.
 */
public class ExperimentWizardFragment extends Fragment {

    public static final String ARG_ID = "experimentId";
    private static final long REFRESH_MS = 100;
    private static final int[] AXIS_COLORS = {0xFFE53935, 0xFF43A047, 0xFF1E88E5};

    private FragmentExperimentWizardBinding binding;
    private ExperimentViewModel vm;
    @Nullable private ExperimentDefinition def;
    private final Map<String, TextInputLayout> inputLayouts = new HashMap<>();
    private int renderedPage = -1;
    @Nullable private RecordingSession boundSession;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (binding == null || def == null) return;
            if (vm.consumeInterrupted()) {
                Snackbar.make(binding.getRoot(), R.string.wizard_interrupted, Snackbar.LENGTH_LONG).show();
                renderedPage = -1;
            }
            if (vm.getPageIndex() != renderedPage) renderPage();
            if (currentKind() == ExperimentViewModel.Kind.RECORD) updateRecord();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExperimentWizardBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(requireActivity()).get(ExperimentViewModel.class);

        String id = getArguments() == null ? null : getArguments().getString(ARG_ID);
        try {
            def = ExperimentRepository.find(requireContext(), id);
        } catch (Exception e) {
            def = null;
        }
        if (def == null) {
            binding.pageTitle.setText(R.string.wizard_not_found);
            binding.stepLabel.setVisibility(View.GONE);
            binding.progress.setVisibility(View.GONE);
            binding.buttonBar.setVisibility(View.GONE);
            return binding.getRoot();
        }

        vm.open(def, savedInstanceState == null);
        buildInputFields(inflater);

        binding.btnBack.setOnClickListener(v -> {
            if (currentKind() == ExperimentViewModel.Kind.RESULTS) vm.runAgain(); else vm.goBack();
            renderPage();
        });
        binding.btnNext.setOnClickListener(v -> {
            if (currentKind() == ExperimentViewModel.Kind.RESULTS) {
                NavHostFragment.findNavController(this).popBackStack();
                return;
            }
            if (currentKind() == ExperimentViewModel.Kind.INPUTS && !validateInputs()) return;
            vm.goNext();
            renderPage();
        });
        binding.btnRecord.setOnClickListener(v -> {
            if (vm.isRecording()) {
                vm.finishRun();
            } else if (!vm.startRun()) {
                Snackbar.make(binding.getRoot(), R.string.recorder_start_failed, Snackbar.LENGTH_SHORT).show();
            }
            renderPage();
        });
        return binding.getRoot();
    }

    private void buildInputFields(LayoutInflater inflater) {
        for (ExperimentDefinition.InputSpec spec : def.getInputs()) {
            TextInputLayout layout = (TextInputLayout) inflater.inflate(
                    R.layout.item_experiment_input, binding.inputsContainer, false);
            TextInputEditText edit = layout.findViewById(R.id.input_edit);
            layout.setHint(spec.label);
            if (!spec.hint.isEmpty()) layout.setHelperText(spec.hint);
            if (!spec.unit.isEmpty()) layout.setSuffixText(spec.unit);
            edit.setText(vm.getInputText(spec.key));
            edit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

                @Override
                public void afterTextChanged(Editable e) {
                    vm.setInputText(spec.key, e.toString());
                    layout.setError(null);
                }
            });
            binding.inputsContainer.addView(layout);
            inputLayouts.put(spec.key, layout);
        }
    }

    private boolean validateInputs() {
        Map<String, String> errors = vm.validateInputs();
        for (Map.Entry<String, TextInputLayout> e : inputLayouts.entrySet()) {
            e.getValue().setError(errors.get(e.getKey()));
        }
        return errors.isEmpty();
    }

    private ExperimentViewModel.Kind currentKind() {
        return vm.getPages().get(vm.getPageIndex()).kind;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (def == null) return;
        ActionBar bar = ((AppCompatActivity) requireActivity()).getSupportActionBar();
        if (bar != null) bar.setTitle(def.getTitle());
        renderedPage = -1;
        handler.post(ticker);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
    }

    /** Shows the page the ViewModel is on. */
    private void renderPage() {
        int idx = vm.getPageIndex();
        int total = vm.getPages().size();
        renderedPage = idx;
        ExperimentViewModel.Page page = vm.getPages().get(idx);

        binding.progress.setMax(total);
        binding.progress.setProgress(idx + 1);
        binding.stepLabel.setText(getString(R.string.wizard_step, idx + 1, total));
        binding.inputsContainer.setVisibility(View.GONE);
        binding.recordContainer.setVisibility(View.GONE);
        binding.resultContainer.setVisibility(View.GONE);
        binding.pageBody.setVisibility(View.VISIBLE);

        binding.btnBack.setText(R.string.wizard_back);
        binding.btnBack.setVisibility(idx > 0 ? View.VISIBLE : View.INVISIBLE);
        binding.btnBack.setEnabled(true);
        binding.btnNext.setText(R.string.wizard_next);
        binding.btnNext.setVisibility(View.VISIBLE);
        binding.btnNext.setEnabled(true);

        switch (page.kind) {
            case INTRO: {
                binding.pageTitle.setText(def.getTitle());
                SensorType sensor = def.getSensor();
                StringBuilder body = new StringBuilder(def.getSummary());
                body.append("\n\n").append(getString(R.string.wizard_needs_sensor, sensor.getDisplayName()));
                if (!vm.isSensorAvailable()) {
                    body.append("\n").append(getString(R.string.wizard_sensor_missing));
                    binding.btnNext.setEnabled(false);
                }
                binding.pageBody.setText(body);
                break;
            }
            case INSTRUCTION: {
                ExperimentDefinition.Step step = def.getSteps().get(page.step);
                binding.pageTitle.setText(step.title);
                binding.pageBody.setText(step.text);
                break;
            }
            case INPUTS:
                binding.pageTitle.setText(R.string.wizard_inputs_title);
                binding.pageBody.setText(R.string.wizard_inputs_body);
                binding.inputsContainer.setVisibility(View.VISIBLE);
                break;
            case RECORD:
                binding.pageTitle.setText(R.string.wizard_record_title);
                binding.pageBody.setText(def.getRecordHint());
                binding.recordContainer.setVisibility(View.VISIBLE);
                binding.btnNext.setVisibility(View.GONE);
                updateRecord();
                break;
            case RESULTS:
                binding.pageTitle.setText(R.string.wizard_results_title);
                binding.pageBody.setVisibility(View.GONE);
                binding.resultContainer.setVisibility(View.VISIBLE);
                binding.btnBack.setText(R.string.wizard_run_again);
                binding.btnNext.setText(R.string.wizard_done);
                showResult();
                break;
        }
    }

    private void updateRecord() {
        RecordingSession s = vm.getSession();
        boolean recording = vm.isRecording();
        binding.btnRecord.setText(recording ? R.string.wizard_stop : R.string.wizard_start);
        binding.btnBack.setEnabled(!recording);

        if (s == null) {
            boundSession = null;
            binding.graph.clearData();
            binding.elapsed.setText(TimeFormat.format(0));
            binding.recordStatus.setText(R.string.wizard_not_recording);
            return;
        }
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
        binding.recordStatus.setText(recording
                ? getString(R.string.wizard_recording, def.getMaxSeconds())
                : getString(R.string.wizard_not_recording));
        binding.graph.invalidate();
    }

    private void showResult() {
        ExperimentResult r = vm.getResult();
        if (r == null) return;
        StringBuilder sb = new StringBuilder();
        for (ExperimentResult.Line l : r.getLines()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(l.label).append(": ").append(l.value);
        }
        binding.resultLines.setText(sb);
        binding.resultLines.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        binding.resultMessage.setText(r.getMessage());
        binding.resultMessage.setTextColor(MaterialColors.getColor(binding.getRoot(),
                r.isOk() ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorError));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
        inputLayouts.clear();
        boundSession = null;
        binding = null;
    }
}
