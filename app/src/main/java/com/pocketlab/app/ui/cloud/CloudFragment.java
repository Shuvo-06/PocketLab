package com.pocketlab.app.ui.cloud;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.cloud.NetworkState;
import com.pocketlab.app.cloud.SyncMode;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.databinding.FragmentCloudBinding;
import com.pocketlab.app.experiment.ExperimentRepository;
import com.pocketlab.app.net.ExperimentDownloader;
import com.pocketlab.app.net.ExperimentUpdater;
import com.pocketlab.app.work.SyncScheduler;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

/** Account, cloud backup and experiment-update controls, with live status (online, syncing, last result). */
public class CloudFragment extends Fragment {

    private static final long REFRESH_MS = 1000;
    private static final int COUNTS_EVERY_N_TICKS = 5;

    private FragmentCloudBinding binding;
    private SyncPrefs prefs;
    private SessionRepository repo;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
    private int tick;
    private int total, unsynced;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (binding == null) return;
            if (tick++ % COUNTS_EVERY_N_TICKS == 0) {
                repo.loadSyncCounts(counts -> {
                    total = counts[0];
                    unsynced = counts[1];
                });
            }
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCloudBinding.inflate(inflater, container, false);
        prefs = new SyncPrefs(requireContext());
        repo = ((PocketLabApp) requireActivity().getApplication()).getSessionRepository();

        binding.switchAuto.setChecked(prefs.isAutoSync());
        binding.checkWifi.setChecked(prefs.isWifiOnly());

        binding.btnSignIn.setOnClickListener(v -> authenticate(false));
        binding.btnRegister.setOnClickListener(v -> authenticate(true));
        binding.btnSignOut.setOnClickListener(v -> {
            AuthManager.signOut(requireContext());
            prefs.setAutoSync(false);
            SyncScheduler.applyAutoSync(requireContext(), false, prefs.isWifiOnly());
            binding.switchAuto.setChecked(false);
            refresh();
        });

        binding.btnBackup.setOnClickListener(v -> queue(SyncMode.BACKUP));
        binding.btnRestore.setOnClickListener(v -> queue(SyncMode.RESTORE));
        binding.switchAuto.setOnCheckedChangeListener((b, checked) -> {
            prefs.setAutoSync(checked);
            SyncScheduler.applyAutoSync(requireContext(), checked, prefs.isWifiOnly());
        });
        binding.checkWifi.setOnCheckedChangeListener((b, checked) -> {
            prefs.setWifiOnly(checked);
            if (prefs.isAutoSync()) SyncScheduler.applyAutoSync(requireContext(), true, checked);
        });
        binding.btnCheckExperiments.setOnClickListener(v -> checkExperiments());
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        tick = 0;
        handler.post(ticker);
    }

    @Override
    public void onStop() {
        super.onStop();
        handler.removeCallbacks(ticker);
    }

    // ------------------------------------------------------------------ actions

    private void authenticate(boolean register) {
        String email = String.valueOf(binding.emailEdit.getText());
        String password = String.valueOf(binding.passwordEdit.getText());
        setAuthBusy(true);
        AuthManager.Callback done = error -> {
            if (binding == null) return;
            setAuthBusy(false);
            if (error != null) {
                Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
            } else {
                binding.passwordEdit.setText("");
                refresh();
            }
        };
        if (register) AuthManager.register(requireContext(), email, password, done);
        else AuthManager.signIn(requireContext(), email, password, done);
    }

    private void setAuthBusy(boolean busy) {
        binding.btnSignIn.setEnabled(!busy);
        binding.btnRegister.setEnabled(!busy);
    }

    private void queue(SyncMode mode) {
        SyncScheduler.syncNow(requireContext(), mode);
        Snackbar.make(binding.getRoot(), NetworkState.isOnline(requireContext())
                ? R.string.cloud_queued : R.string.cloud_queued_offline, Snackbar.LENGTH_LONG).show();
    }

    private void checkExperiments() {
        binding.btnCheckExperiments.setEnabled(false);
        android.content.Context app = requireContext().getApplicationContext();
        Handler main = handler;
        Executors.newSingleThreadExecutor().execute(() -> {
            ExperimentUpdater.Result r = ExperimentUpdater.update(ExperimentRepository.diskCache(app),
                    new ExperimentDownloader(), app.getString(R.string.experiments_url));
            if (r.status == ExperimentUpdater.Status.UPDATED) ExperimentRepository.invalidate();
            new SyncPrefs(app).recordExperimentCheck(r.message);
            main.post(() -> {
                if (binding == null) return;
                binding.btnCheckExperiments.setEnabled(true);
                Snackbar.make(binding.getRoot(), r.message, Snackbar.LENGTH_LONG).show();
                refresh();
            });
        });
    }

    // ------------------------------------------------------------------ status

    private void refresh() {
        if (binding == null) return;
        android.content.Context ctx = requireContext();
        boolean online = NetworkState.isOnline(ctx);
        boolean configured = AuthManager.isConfigured(ctx);
        FirebaseUser user = AuthManager.user(ctx);
        boolean signedIn = user != null;

        binding.onlineStatus.setText(online ? R.string.cloud_online : R.string.cloud_offline);

        // Account card
        if (!configured) {
            binding.accountStatus.setText(R.string.cloud_not_configured);
        } else if (!signedIn) {
            binding.accountStatus.setText(R.string.cloud_signed_out);
        } else {
            binding.accountStatus.setText(getString(R.string.cloud_signed_in_as, user.getEmail()));
        }
        binding.signedOutGroup.setVisibility(configured && !signedIn ? View.VISIBLE : View.GONE);
        binding.btnSignOut.setVisibility(signedIn ? View.VISIBLE : View.GONE);

        // Backup card
        boolean canSync = configured && signedIn;
        binding.btnBackup.setEnabled(canSync);
        binding.btnRestore.setEnabled(canSync);
        binding.switchAuto.setEnabled(canSync);
        binding.checkWifi.setEnabled(canSync);
        StringBuilder sb = new StringBuilder();
        if (!canSync) {
            sb.append(getString(R.string.cloud_sign_in_first));
        } else {
            sb.append(getString(R.string.cloud_counts, total, unsynced));
            if (prefs.isRunning()) {
                sb.append('\n').append(getString(R.string.cloud_syncing));
            } else if (prefs.getLastMessage() != null) {
                sb.append('\n').append(getString(prefs.wasLastOk() ? R.string.cloud_last_ok : R.string.cloud_last_failed,
                        prefs.getLastSyncAt() > 0 ? dateFormat.format(new Date(prefs.getLastSyncAt())) : "–",
                        prefs.getLastMessage()));
            }
        }
        binding.backupStatus.setText(sb);

        // Experiments card
        StringBuilder ex = new StringBuilder(getString(ExperimentRepositoryState.sourceLabel()));
        if (getString(R.string.experiments_url).trim().isEmpty()) {
            ex.append('\n').append(getString(R.string.exp_updates_not_configured));
        } else if (prefs.getExperimentMessage() != null) {
            ex.append('\n').append(getString(R.string.exp_updates_last,
                    dateFormat.format(new Date(prefs.getExperimentCheckAt())), prefs.getExperimentMessage()));
        }
        binding.experimentsStatus.setText(ex);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(ticker);
        binding = null;
    }

    /** Tiny helper so the label choice is in one place. */
    private static final class ExperimentRepositoryState {
        static int sourceLabel() {
            return ExperimentRepository.isFromDownload() ? R.string.exp_source_downloaded : R.string.exp_source_bundled;
        }
    }
}
