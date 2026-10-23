package com.pocketlab.app.work;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.cloud.SyncMode;
import com.pocketlab.app.cloud.SyncPrefs;

import java.util.concurrent.TimeUnit;

/** All WorkManager scheduling in one place. Unique work names prevent duplicate jobs. */
public final class SyncScheduler {

    private static final String WORK_NOW = "pocketlab-sync-now";
    private static final String WORK_AFTER_SAVE = "pocketlab-sync-after-save";
    private static final String WORK_PERIODIC = "pocketlab-sync-periodic";
    private static final String WORK_EXPERIMENTS = "pocketlab-experiment-updates";

    private SyncScheduler() {}

    private static Constraints network(boolean wifiOnly) {
        return new Constraints.Builder()
                .setRequiredNetworkType(wifiOnly ? NetworkType.UNMETERED : NetworkType.CONNECTED)
                .build();
    }

    /** "Back up now" / "Restore": runs as soon as there is a connection (waits if offline). */
    public static void syncNow(Context context, SyncMode mode) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(network(false))
                .setInputData(new Data.Builder().putString(SyncWorker.KEY_MODE, mode.name()).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request);
    }

    /** Turns the 6-hourly automatic sync on or off (and applies the Wi-Fi-only choice). */
    public static void applyAutoSync(Context context, boolean enabled, boolean wifiOnly) {
        WorkManager wm = WorkManager.getInstance(context);
        if (!enabled) {
            wm.cancelUniqueWork(WORK_PERIODIC);
            return;
        }
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(network(wifiOnly))
                .setInputData(new Data.Builder().putString(SyncWorker.KEY_MODE, SyncMode.SYNC.name()).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build();
        wm.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.REPLACE, request);
    }

    /**
     * Called after a recording was saved: if automatic backup is on, back it up soon. The delay
     * gives the background writer time to finish the recording first.
     */
    public static void onSessionSaved(Context context) {
        SyncPrefs prefs = new SyncPrefs(context);
        if (!prefs.isAutoSync() || AuthManager.user(context) == null) return;
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setInitialDelay(30, TimeUnit.SECONDS)
                .setConstraints(network(prefs.isWifiOnly()))
                .setInputData(new Data.Builder().putString(SyncWorker.KEY_MODE, SyncMode.SYNC.name()).build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_AFTER_SAVE, ExistingWorkPolicy.KEEP, request);
    }

    /** Daily check for new experiment definitions. KEEP: scheduling again at every start changes nothing. */
    public static void scheduleExperimentUpdates(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(ExperimentUpdateWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(network(false))
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_EXPERIMENTS, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
