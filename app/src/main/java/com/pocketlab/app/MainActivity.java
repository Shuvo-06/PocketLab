package com.pocketlab.app;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.color.MaterialColors;
import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.databinding.ActivityMainBinding;
import com.pocketlab.app.ui.experiments.ExperimentViewModel;
import com.pocketlab.app.ui.recorder.RecorderViewModel;
import com.pocketlab.app.util.AppDestination;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.work.SyncScheduler;

import java.util.HashSet;
import java.util.Set;

/**
 * Single-activity host. Screens are Fragments navigated through the Navigation component and a bottom
 * navigation bar. At startup it sends the user to {@link LoginActivity} when an account is required, and
 * it understands the "open this screen" Intent extra carried by notifications (deep links).
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    // ---- connection banner ----
    private static final long PENDING_REFRESH_MS = 3000;
    private static final long BACK_ONLINE_SHOW_MS = 4000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SessionRepository repository;
    private boolean offline;      // the phone has no working connection right now
    private boolean wasOffline;   // it was offline at some point since the banner last settled

    private final Runnable hideBanner = () -> {
        if (binding != null) binding.connectionBanner.setVisibility(View.GONE);
    };

    /** While offline, re-reads how many recordings wait for upload so the banner count stays right. */
    private final Runnable refreshPending = new Runnable() {
        @Override
        public void run() {
            if (binding == null || !offline) return;
            if (AuthManager.user(MainActivity.this) == null) {
                showBanner(true, getString(R.string.offline_banner));   // guest: nothing is queued for the cloud
            } else {
                repository.loadSyncCounts(counts -> {
                    if (binding == null || !offline) return;
                    int waiting = counts[1];
                    showBanner(true, waiting > 0
                            ? getResources().getQuantityString(R.plurals.offline_banner_pending, waiting, waiting)
                            : getString(R.string.offline_banner));
                });
            }
            handler.postDelayed(this, PENDING_REFRESH_MS);
        }
    };

    /** Android 13+ asks before notifications may be shown. */
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AuthManager.needsLogin(this)) {
            Intent login = new Intent(this, LoginActivity.class);
            String pending = getIntent().getStringExtra(AppDestination.EXTRA);   // keep a tapped notification's target
            if (pending != null) login.putExtra(AppDestination.EXTRA, pending);
            startActivity(login);
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        Fragment host = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        navController = ((NavHostFragment) host).getNavController();

        Set<Integer> topLevel = new HashSet<>();
        topLevel.add(R.id.dashboardFragment);
        topLevel.add(R.id.recorderFragment);
        topLevel.add(R.id.experimentsFragment);
        topLevel.add(R.id.historyFragment);
        appBarConfiguration = new AppBarConfiguration.Builder(topLevel).build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.bottomNav, navController);

        askNotificationPermissionOnce();
        repository = ((PocketLabApp) getApplication()).getSessionRepository();
        ((PocketLabApp) getApplication()).getConnectivityMonitor().online()
                .observe(this, online -> onConnectivityChanged(Boolean.TRUE.equals(online)));
        handleDestination(getIntent());
    }

    /**
     * Offline: show the banner and keep it until the connection returns. Back online: if recordings were
     * waiting, start their upload (WorkManager) and say so for a few seconds, then hide the banner.
     */
    private void onConnectivityChanged(boolean online) {
        if (binding == null) return;
        handler.removeCallbacks(hideBanner);
        handler.removeCallbacks(refreshPending);
        if (!online) {
            offline = true;
            wasOffline = true;
            showBanner(true, getString(R.string.offline_banner));
            refreshPending.run();
            return;
        }
        offline = false;
        if (!wasOffline) {            // online since the app started: nothing to announce
            binding.connectionBanner.setVisibility(View.GONE);
            return;
        }
        wasOffline = false;
        boolean signedIn = AuthManager.user(this) != null;
        if (!signedIn) {
            showBanner(false, getString(R.string.online_back));
            handler.postDelayed(hideBanner, BACK_ONLINE_SHOW_MS);
            return;
        }
        repository.loadSyncCounts(counts -> {
            if (binding == null || offline) return;
            int waiting = counts[1];
            if (waiting > 0) {
                SyncScheduler.syncPending(getApplicationContext());
                showBanner(false, getResources().getQuantityString(R.plurals.online_back_saving, waiting, waiting));
            } else {
                showBanner(false, getString(R.string.online_back));
            }
            handler.postDelayed(hideBanner, BACK_ONLINE_SHOW_MS);
        });
    }

    private void showBanner(boolean isOffline, String text) {
        android.widget.TextView banner = binding.connectionBanner;
        int bg = MaterialColors.getColor(this, isOffline
                ? com.google.android.material.R.attr.colorErrorContainer
                : com.google.android.material.R.attr.colorPrimaryContainer, isOffline ? 0xFFFFDAD6 : 0xFFB2EBF2);
        int fg = MaterialColors.getColor(this, isOffline
                ? com.google.android.material.R.attr.colorOnErrorContainer
                : com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF1C1B1F);
        banner.setBackgroundColor(bg);
        banner.setTextColor(fg);
        banner.setText(text);
        banner.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** A notification was tapped while the app was already open (launchMode singleTop). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (binding != null) handleDestination(intent);
    }

    /** Opens the screen named by the Intent extra, once (the extra is removed so rotation cannot repeat it). */
    private void handleDestination(Intent intent) {
        if (intent == null) return;
        AppDestination destination = AppDestination.fromKey(intent.getStringExtra(AppDestination.EXTRA));
        if (destination == null) return;
        intent.removeExtra(AppDestination.EXTRA);
        switch (destination) {
            case SENSORS: binding.bottomNav.setSelectedItemId(R.id.dashboardFragment); break;
            case RECORDER: binding.bottomNav.setSelectedItemId(R.id.recorderFragment); break;
            case EXPERIMENTS: binding.bottomNav.setSelectedItemId(R.id.experimentsFragment); break;
            case HISTORY: binding.bottomNav.setSelectedItemId(R.id.historyFragment); break;
            case CLOUD: openOnce(R.id.cloudFragment); break;
            case PROFILE: openOnce(R.id.profileFragment); break;
            default: break;
        }
    }

    private void openOnce(int destinationId) {
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() != destinationId) {
            navController.navigate(destinationId);
        }
    }

    private void askNotificationPermissionOnce() {
        // compileSdk 32 has no Manifest.permission.POST_NOTIFICATIONS constant, so the name is a string.
        String permission = "android.permission.POST_NOTIFICATIONS";
        SyncPrefs prefs = new SyncPrefs(this);
        if (Build.VERSION.SDK_INT < 33 || prefs.wasNotificationPermissionAsked()) return;
        prefs.setNotificationPermissionAsked();
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(permission);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // A free recording keeps running in the foreground service; an experiment run does not.
        // Rotation is not "leaving the app", so skip it.
        if (binding != null && !isChangingConfigurations()) {
            ViewModelProvider provider = new ViewModelProvider(this);
            provider.get(RecorderViewModel.class).onAppBackgrounded();
            provider.get(ExperimentViewModel.class).onAppBackgrounded();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.top_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            openOnce(R.id.profileFragment);
            return true;
        }
        if (id == R.id.action_cloud) {
            openOnce(R.id.cloudFragment);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

}
