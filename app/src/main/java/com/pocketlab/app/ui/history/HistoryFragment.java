package com.pocketlab.app.ui.history;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.data.SessionEntity;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.databinding.FragmentHistoryBinding;
import com.pocketlab.app.databinding.ItemSessionBinding;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.sensor.SensorType;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Saved recordings: search by name, tap to open, swipe to delete (with undo). */
public class HistoryFragment extends Fragment {

    private static final long SEARCH_DELAY_MS = 250;

    private FragmentHistoryBinding binding;
    private SessionRepository repo;
    private final List<SessionEntity> items = new ArrayList<>();
    private Adapter adapter;
    private String query = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reload = this::load;
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

    /** System file picker (Storage Access Framework); no storage permission needed. */
    private final ActivityResultLauncher<String[]> importPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onImportPicked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHistoryBinding.inflate(inflater, container, false);
        repo = ((PocketLabApp) requireActivity().getApplication()).getSessionRepository();

        binding.btnImport.setOnClickListener(v -> importPicker.launch(new String[]{"*/*"}));
        adapter = new Adapter();
        binding.sessionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.sessionList.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                deleteAt(holder.getBindingAdapterPosition());
            }
        }).attachToRecyclerView(binding.sessionList);

        binding.searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable e) {
                query = e.toString().trim();
                handler.removeCallbacks(reload);
                handler.postDelayed(reload, SEARCH_DELAY_MS);
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        load();
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(reload);
    }

    private void load() {
        repo.loadSessions(query, list -> {
            if (binding == null) return;
            items.clear();
            items.addAll(list);
            adapter.notifyDataSetChanged();
            updateEmpty();
        });
    }

    private void updateEmpty() {
        binding.emptyText.setText(query.isEmpty() ? R.string.history_empty : R.string.history_no_matches);
        binding.emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void deleteAt(int position) {
        if (position == RecyclerView.NO_POSITION || position >= items.size()) return;
        SessionEntity removed = items.remove(position);
        adapter.notifyItemRemoved(position);
        updateEmpty();
        repo.setTrashed(removed.id, true);
        Snackbar.make(binding.getRoot(), R.string.history_deleted, Snackbar.LENGTH_LONG)
                .setAction(R.string.history_undo, v -> {
                    repo.setTrashed(removed.id, false);
                    load();
                })
                .show();
    }

    private void onImportPicked(@Nullable Uri uri) {
        if (uri == null || binding == null) return;
        Snackbar.make(binding.getRoot(), R.string.import_reading, Snackbar.LENGTH_SHORT).show();
        ((PocketLabApp) requireActivity().getApplication()).getTransferManager().importAsync(uri, result -> {
            if (binding == null) return;
            Snackbar.make(binding.getRoot(), result.error == null
                    ? getString(R.string.import_success, result.name)
                    : getString(R.string.import_failed, result.error), Snackbar.LENGTH_LONG).show();
            if (result.error == null) load();
        });
    }

    private void open(SessionEntity session) {
        Bundle args = new Bundle();
        args.putLong(SessionDetailFragment.ARG_ID, session.id);
        NavHostFragment.findNavController(this).navigate(R.id.sessionDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(reload);
        binding.sessionList.setAdapter(null);
        adapter = null;
        binding = null;
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemSessionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            SessionEntity e = items.get(position);
            String what = e.experimentTitle != null ? e.experimentTitle
                    : SensorType.valueOf(e.sensorType).getDisplayName();
            h.binding.name.setText(e.name);
            h.binding.meta.setText(getString(R.string.history_meta,
                    dateFormat.format(new Date(e.createdAt)), what,
                    TimeFormat.format(e.durationSeconds), e.sampleCount));
            h.binding.getRoot().setOnClickListener(v -> open(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ItemSessionBinding binding;

            Holder(ItemSessionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
