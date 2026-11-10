package com.pocketlab.app.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pocketlab.app.R;
import com.pocketlab.app.cloud.CloudSync;
import com.pocketlab.app.cloud.SyncException;
import com.pocketlab.app.cloud.SyncMode;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.cloud.SyncResult;
import com.pocketlab.app.service.Notifier;
import com.pocketlab.app.util.AppDestination;

/**
 * Background cloud sync. WorkManager runs it when the network constraint is met, survives app
 * restarts, and retries with exponential backoff on temporary failures (up to 5 attempts).
 */
public class SyncWorker extends Worker {

    static final String KEY_MODE = "mode";
    private static final int MAX_ATTEMPTS = 5;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SyncPrefs prefs = new SyncPrefs(c);
        SyncMode mode;
        try {
            mode = SyncMode.valueOf(getInputData().getString(KEY_MODE));
        } catch (RuntimeException e) {
            mode = SyncMode.SYNC;
        }

        prefs.setRunning(true);
        try {
            SyncResult r = CloudSync.run(c, mode);
            String summary = c.getString(R.string.sync_done, r.uploaded, r.restored + r.appliedFromCloud, r.conflicts);
            prefs.recordSuccess(summary);
            if (r.changedAnything()) {
                // Shown even when the app is closed; tapping it opens the Cloud screen.
                Notifier.event(c, Notifier.ID_SYNC, c.getString(R.string.notif_sync_title), summary, AppDestination.CLOUD);
            }
            return Result.success();
        } catch (SyncException e) {
            prefs.recordFailure(e.getMessage());
            // Retrying only helps for temporary problems; sign-in, rules or setup need the user.
            boolean retry = e.isTransient() && getRunAttemptCount() < MAX_ATTEMPTS;
            if (!retry) {
                Notifier.event(c, Notifier.ID_SYNC, c.getString(R.string.notif_sync_failed_title), e.getMessage(), AppDestination.CLOUD);
            }
            return retry ? Result.retry() : Result.failure();
        } catch (RuntimeException e) {
            prefs.recordFailure(c.getString(R.string.sync_unexpected_error));
            return getRunAttemptCount() < MAX_ATTEMPTS ? Result.retry() : Result.failure();
        } finally {
            prefs.setRunning(false);
        }
    }
}
