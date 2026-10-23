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
            prefs.recordSuccess(c.getString(R.string.sync_done, r.uploaded, r.restored + r.appliedFromCloud, r.conflicts));
            return Result.success();
        } catch (SyncException e) {
            prefs.recordFailure(e.getMessage());
            // Retrying only helps for temporary problems; sign-in, rules or setup need the user.
            return e.isTransient() && getRunAttemptCount() < MAX_ATTEMPTS ? Result.retry() : Result.failure();
        } catch (RuntimeException e) {
            prefs.recordFailure(c.getString(R.string.sync_unexpected_error));
            return getRunAttemptCount() < MAX_ATTEMPTS ? Result.retry() : Result.failure();
        } finally {
            prefs.setRunning(false);
        }
    }
}
