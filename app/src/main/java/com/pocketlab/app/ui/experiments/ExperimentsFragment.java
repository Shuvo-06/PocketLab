package com.pocketlab.app.ui.experiments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.pocketlab.app.R;
import com.pocketlab.app.databinding.FragmentPlaceholderBinding;

/** Placeholder screen. Replaced by the real feature in a later phase. */
public class ExperimentsFragment extends Fragment {

    private FragmentPlaceholderBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaceholderBinding.inflate(inflater, container, false);
        binding.icon.setImageResource(R.drawable.ic_experiments);
        binding.title.setText(R.string.experiments_title);
        binding.body.setText(R.string.experiments_body);
        binding.phaseChip.setText(R.string.experiments_phase);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
