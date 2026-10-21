package com.pocketlab.app.ui.experiments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.databinding.FragmentExperimentsBinding;
import com.pocketlab.app.databinding.ItemExperimentBinding;
import com.pocketlab.app.experiment.ExperimentDefinition;
import com.pocketlab.app.experiment.ExperimentRepository;
import com.pocketlab.app.sensor.SensorHub;

import java.util.List;

/** Lists the guided experiments. Experiments whose sensor is missing are shown but disabled. */
public class ExperimentsFragment extends Fragment {

    private FragmentExperimentsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExperimentsBinding.inflate(inflater, container, false);
        SensorHub hub = ((PocketLabApp) requireActivity().getApplication()).getSensorHub();
        try {
            List<ExperimentDefinition> defs = ExperimentRepository.load(requireContext());
            binding.experimentList.setLayoutManager(new LinearLayoutManager(requireContext()));
            binding.experimentList.setAdapter(new Adapter(defs, hub));
        } catch (Exception e) {
            binding.errorText.setText(getString(R.string.experiments_error, e.getMessage()));
            binding.errorText.setVisibility(View.VISIBLE);
        }
        return binding.getRoot();
    }

    private void open(ExperimentDefinition def, boolean available) {
        if (!available) {
            Snackbar.make(binding.getRoot(), R.string.wizard_sensor_missing, Snackbar.LENGTH_LONG).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString(ExperimentWizardFragment.ARG_ID, def.getId());
        NavHostFragment.findNavController(this).navigate(R.id.experimentWizardFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding.experimentList.setAdapter(null);
        binding = null;
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<ExperimentDefinition> defs;
        private final SensorHub hub;

        Adapter(List<ExperimentDefinition> defs, SensorHub hub) {
            this.defs = defs;
            this.hub = hub;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemExperimentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ExperimentDefinition def = defs.get(position);
            boolean available = hub.isAvailable(def.getSensor());
            h.binding.title.setText(def.getTitle());
            h.binding.summary.setText(def.getSummary());
            h.binding.sensorLine.setText(getString(
                    available ? R.string.experiment_sensor_ok : R.string.experiment_sensor_missing,
                    def.getSensor().getDisplayName()));
            h.binding.getRoot().setAlpha(available ? 1f : 0.55f);
            h.binding.getRoot().setOnClickListener(v -> open(def, available));
        }

        @Override
        public int getItemCount() {
            return defs.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemExperimentBinding binding;

            Holder(ItemExperimentBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
