package com.pocketlab.app;

import android.app.Application;

import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.cloud.ConnectivityMonitor;
import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.export.TransferManager;
import com.pocketlab.app.sensor.SensorHub;
import com.pocketlab.app.service.RecordingController;
import com.pocketlab.app.work.SyncScheduler;

import java.util.concurrent.Executors;

/** Application entry point and home of shared singletons. */
public class PocketLabApp extends Application {

    private SensorHub sensorHub;
    private SessionRepository sessionRepository;
    private TransferManager transferManager;
    private RecordingController recordingController;
    private ConnectivityMonitor connectivityMonitor;

    @Override
    public void onCreate() {
        super.onCreate();
        connectivityMonitor = new ConnectivityMonitor(this);
        sensorHub = new SensorHub(this);
        sessionRepository = new SessionRepository(AppDatabase.get(this),
                Executors.newSingleThreadExecutor(), () -> AuthManager.uid(this));
        sessionRepository.recoverAndPurge();
        recordingController = new RecordingController(this, sensorHub, sessionRepository);
        SyncScheduler.scheduleExperimentUpdates(this);   // daily check for new experiments (no-op without a server)
        transferManager = new TransferManager(this, AppDatabase.get(this));
    }

    public ConnectivityMonitor getConnectivityMonitor() {
        return connectivityMonitor;
    }

    public SensorHub getSensorHub() {
        return sensorHub;
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }

    public TransferManager getTransferManager() {
        return transferManager;
    }

    public RecordingController getRecordingController() {
        return recordingController;
    }
}
