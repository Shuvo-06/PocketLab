package com.pocketlab.app.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pocketlab.app.R;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.experiment.ExperimentRepository;
import com.pocketlab.app.net.ExperimentDownloader;
import com.pocketlab.app.net.ExperimentUpdater;
import com.pocketlab.app.service.Notifier;
import com.pocketlab.app.util.AppDestination;

/** Checks the experiment server (about once a day) and refreshes the cached definitions. */
public class ExperimentUpdateWorker extends Worker {

    private static final int MAX_ATTEMPTS = 3;

    public ExperimentUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        ExperimentUpdater.Result r = ExperimentUpdater.update(ExperimentRepository.diskCache(c),
                new ExperimentDownloader(), c.getString(R.string.experiments_url));
        if (r.status == ExperimentUpdater.Status.NOT_CONFIGURED) return Result.success();
        if (r.status == ExperimentUpdater.Status.UPDATED) {
            ExperimentRepository.invalidate();
            Notifier.event(c, Notifier.ID_EXPERIMENTS, c.getString(R.string.notif_experiments_title),
                    c.getString(R.string.notif_experiments_text, r.count), AppDestination.EXPERIMENTS);
        }
        new SyncPrefs(c).recordExperimentCheck(r.message);
        if (r.status == ExperimentUpdater.Status.FAILED && r.transientFailure && getRunAttemptCount() < MAX_ATTEMPTS) {
            return Result.retry();
        }
        return Result.success();   // the next daily run tries again anyway
    }
}
