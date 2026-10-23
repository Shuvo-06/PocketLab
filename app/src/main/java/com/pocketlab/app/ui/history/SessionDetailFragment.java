package com.pocketlab.app.ui.history;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.data.MarkerEntity;
import com.pocketlab.app.data.SampleEntity;
import com.pocketlab.app.data.SessionDetail;
import com.pocketlab.app.data.SessionEntity;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.databinding.FragmentSessionDetailBinding;
import com.pocketlab.app.export.TransferManager;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.graph.SeriesBuffer;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.recording.RecordingSummary;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;
import com.pocketlab.app.ui.SummaryText;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** One saved recording: graph, summary, saved result, rename and delete. */
public class SessionDetailFragment extends Fragment {

    public static final String ARG_ID = "sessionId";
    private static final int[] AXIS_COLORS = {0xFFE53935, 0xFF43A047, 0xFF1E88E5};
    private static final int MAX_NAME_LENGTH = 80;

    private FragmentSessionDetailBinding binding;
    private SessionRepository repo;
    private long sessionId;
    @Nullable private SessionEntity session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSessionDetailBinding.inflate(inflater, container, false);
        repo = ((PocketLabApp) requireActivity().getApplication()).getSessionRepository();
        sessionId = getArguments() == null ? -1 : getArguments().getLong(ARG_ID, -1);

        binding.btnRename.setOnClickListener(v -> showRename());
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
        binding.btnExport.setOnClickListener(v -> showExportDialog());
        repo.loadDetail(sessionId, this::bind);
        return binding.getRoot();
    }

    private void bind(@Nullable SessionDetail d) {
        if (binding == null) return;
        binding.loading.setVisibility(View.GONE);
        if (d == null) {
            binding.errorText.setText(R.string.history_not_found);
            binding.errorText.setVisibility(View.VISIBLE);
            return;
        }
        session = d.session;
        SensorType type = SensorType.valueOf(d.session.sensorType);
        binding.content.setVisibility(View.VISIBLE);
        binding.nameText.setText(d.session.name);

        String meta = getString(R.string.history_meta_detail,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(d.session.createdAt)),
                d.session.experimentTitle != null ? d.session.experimentTitle : type.getDisplayName(),
                SamplingRate.valueOf(d.session.samplingRate).getLabel(),
                TimeFormat.format(d.session.durationSeconds), d.session.sampleCount);
        if (d.samples.size() < d.totalSamples) {
            meta += "\n" + getString(R.string.history_decimated, d.samples.size(), d.totalSamples);
        }
        if (d.totalSamples == 0 && d.session.sampleCount > 0) {
            meta += "\n" + getString(R.string.history_summary_only);   // restored from a cloud backup
        }
        binding.metaText.setText(meta);

        if (d.session.resultText != null && !d.session.resultText.isEmpty()) {
            binding.resultText.setText(d.session.resultText);
            binding.resultText.setVisibility(View.VISIBLE);
        }

        // Rebuild the graph buffers from the stored rows.
        int axes = type.getAxes();
        List<SeriesBuffer> buffers = new ArrayList<>();
        for (int k = 0; k < axes; k++) buffers.add(new SeriesBuffer(Math.max(1, d.samples.size())));
        for (SampleEntity s : d.samples) {
            buffers.get(0).add(s.timeSeconds, s.v0);
            if (axes > 1) buffers.get(1).add(s.timeSeconds, s.v1);
            if (axes > 2) buffers.get(2).add(s.timeSeconds, s.v2);
        }
        List<Marker> markers = new ArrayList<>();
        for (MarkerEntity m : d.markers) markers.add(new Marker(m.timeSeconds, m.label));

        List<String> labels = new ArrayList<>();
        int[] colors;
        if (axes == 3) {
            colors = AXIS_COLORS;
            for (int i = 0; i < 3; i++) labels.add(type.getAxisLabel(i));
        } else {
            colors = new int[]{MaterialColors.getColor(binding.getRoot(),
                    com.google.android.material.R.attr.colorPrimary, 0xFF006A6A)};
            labels.add(type.getDisplayName());
        }
        binding.graph.setData(buffers, labels, colors, markers, type.getUnit());

        RecordingSummary sum = RecordingSummary.of(buffers, d.samples.size() < d.totalSamples);
        if (!sum.getAxes().isEmpty()) {
            binding.summaryText.setText(SummaryText.format(requireContext(), type, sum));
            binding.summaryText.setVisibility(View.VISIBLE);
        }
    }

    private void showExportDialog() {
        if (session == null) return;
        String[] labels = {getString(R.string.export_csv), getString(R.string.export_json),
                getString(R.string.export_xml), getString(R.string.export_pdf)};
        TransferManager.Format[] formats = TransferManager.Format.values();   // CSV, JSON, XML, PDF
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.export_title)
                .setItems(labels, (dialog, which) -> export(formats[which]))
                .show();
    }

    private void export(TransferManager.Format format) {
        TransferManager tm = ((PocketLabApp) requireActivity().getApplication()).getTransferManager();
        binding.btnExport.setEnabled(false);
        Snackbar.make(binding.getRoot(), R.string.export_preparing, Snackbar.LENGTH_SHORT).show();
        tm.exportAsync(sessionId, format, result -> {
            if (binding == null || !isAdded()) return;
            binding.btnExport.setEnabled(true);
            if (result.file == null) {
                Snackbar.make(binding.getRoot(), getString(R.string.export_failed, result.error),
                        Snackbar.LENGTH_LONG).show();
                return;
            }
            share(result);
        });
    }

    /** Opens the Android share sheet for the exported file (granted through FileProvider). */
    private void share(TransferManager.ExportResult result) {
        Context ctx = requireContext();
        Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", result.file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(result.format.mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, session != null ? session.name : "PocketLab");
        send.setClipData(ClipData.newRawUri("", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, getString(R.string.export_chooser)));
    }

    private void showRename() {
        if (session == null) return;
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_rename, null);
        TextInputEditText edit = v.findViewById(R.id.rename_edit);
        edit.setText(session.name);
        edit.selectAll();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_rename)
                .setView(v)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = String.valueOf(edit.getText()).trim();
                    if (name.isEmpty() || session == null || binding == null) return;
                    if (name.length() > MAX_NAME_LENGTH) name = name.substring(0, MAX_NAME_LENGTH);
                    session.name = name;
                    repo.rename(sessionId, name);
                    binding.nameText.setText(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.history_delete_title)
                .setMessage(R.string.history_delete_message)
                .setPositiveButton(R.string.history_delete, (dialog, which) -> {
                    repo.setTrashed(sessionId, true);
                    NavHostFragment.findNavController(this).popBackStack();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
