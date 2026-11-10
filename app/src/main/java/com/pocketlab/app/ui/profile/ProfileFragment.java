package com.pocketlab.app.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseUser;
import com.pocketlab.app.LoginActivity;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.databinding.FragmentProfileBinding;
import com.pocketlab.app.provider.ProviderDemo;
import com.pocketlab.app.util.BuildConfigVersion;

import java.text.DateFormat;
import java.util.Date;

/**
 * The user's profile (name, email, member since, how many recordings), sign-out, notification
 * settings, the ContentProvider demo, and a feedback e-mail. The profile belongs to the account, so it is the
 * same on every device the user signs in on.
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private SyncPrefs prefs;
    private SessionRepository repo;
    private int total, unsynced;
    private boolean nameFilled;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        prefs = new SyncPrefs(requireContext());
        repo = ((PocketLabApp) requireActivity().getApplication()).getSessionRepository();

        binding.switchEvents.setChecked(prefs.isEventNotifications());
        binding.switchEvents.setOnCheckedChangeListener((b, checked) -> prefs.setEventNotifications(checked));
        binding.btnNotificationSettings.setOnClickListener(v -> openNotificationSettings());
        binding.btnProviderDemo.setOnClickListener(v -> showProviderDemo());

        binding.btnSaveName.setOnClickListener(v -> saveName());
        binding.btnSignOut.setOnClickListener(v -> confirmSignOut());
        binding.btnSignIn.setOnClickListener(v -> openLogin(false));
        binding.btnRegister.setOnClickListener(v -> openLogin(true));

        String feedbackEmail = getString(R.string.feedback_email).trim();
        binding.btnFeedback.setVisibility(feedbackEmail.isEmpty() ? View.GONE : View.VISIBLE);
        binding.btnFeedback.setOnClickListener(v -> sendFeedback(feedbackEmail));
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        repo.loadSyncCounts(counts -> {
            if (binding == null) return;
            total = counts[0];
            unsynced = counts[1];
            refresh();
        });
        refresh();
    }

    private void refresh() {
        if (binding == null) return;
        boolean configured = AuthManager.isConfigured(requireContext());
        FirebaseUser user = AuthManager.user(requireContext());
        boolean signedIn = user != null;

        binding.profileName.setText(AuthManager.displayName(requireContext()));
        binding.profileEmail.setText(signedIn ? user.getEmail()
                : getString(configured ? R.string.profile_guest_hint : R.string.cloud_not_configured));
        if (signedIn && user.getMetadata() != null) {
            binding.profileSince.setText(getString(R.string.profile_since, DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(new Date(user.getMetadata().getCreationTimestamp()))));
            binding.profileSince.setVisibility(View.VISIBLE);
        } else {
            binding.profileSince.setVisibility(View.GONE);
        }
        binding.profileStats.setText(signedIn
                ? getString(R.string.profile_stats, total, unsynced)
                : getString(R.string.profile_stats_local, total));

        binding.nameGroup.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (signedIn && !nameFilled) {
            binding.nameEdit.setText(user.getDisplayName());
            nameFilled = true;
        }
        binding.btnSignOut.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        binding.guestGroup.setVisibility(configured && !signedIn ? View.VISIBLE : View.GONE);
    }

    private void saveName() {
        String name = String.valueOf(binding.nameEdit.getText()).trim();
        if (name.isEmpty()) {
            binding.nameLayout.setError(getString(R.string.profile_name_empty));
            return;
        }
        binding.nameLayout.setError(null);
        binding.btnSaveName.setEnabled(false);
        AuthManager.updateDisplayName(requireContext(), name, error -> {
            if (binding == null) return;
            binding.btnSaveName.setEnabled(true);
            Snackbar.make(binding.getRoot(), error == null ? getString(R.string.profile_name_saved) : error,
                    Snackbar.LENGTH_SHORT).show();
            refresh();
        });
    }

    private void confirmSignOut() {
        String message = unsynced > 0 ? getString(R.string.profile_sign_out_unsynced, unsynced)
                : getString(R.string.profile_sign_out_message);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.auth_sign_out)
                .setMessage(message)
                .setPositiveButton(R.string.auth_sign_out, (d, w) -> {
                    AuthManager.signOut(requireContext());
                    // Back to the login page, with no way to go "back" into the signed-out app.
                    startActivity(new Intent(requireContext(), LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openLogin(boolean register) {
        startActivity(new Intent(requireContext(), LoginActivity.class)
                .putExtra(LoginActivity.EXTRA_FORCE, true)
                .putExtra(LoginActivity.EXTRA_REGISTER, register));
    }

    // ------------------------------------------------------------------ notifications

    /** Runs a few ContentResolver queries against the app's own read-only provider (lab demo). */
    private void showProviderDemo() {
        ProviderDemo.runAsync(requireContext(), text -> {
            if (binding == null || !isAdded()) return;
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.about_provider_demo)
                    .setMessage(text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    /** An implicit Intent to the system's notification settings for this app. */
    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.profile_no_app_for_action, Snackbar.LENGTH_SHORT).show();
        }
    }

    /** An implicit Intent: any e-mail app can handle ACTION_SENDTO with a mailto: address. */
    private void sendFeedback(String email) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, new String[]{email})
                .putExtra(Intent.EXTRA_SUBJECT, "PocketLab feedback (version " + BuildConfigVersion.name(requireContext()) + ")");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.getRoot(), R.string.profile_no_app_for_action, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
