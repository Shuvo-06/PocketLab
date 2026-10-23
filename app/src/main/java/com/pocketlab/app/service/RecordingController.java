package com.pocketlab.app.service;

import android.app.Application;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.pocketlab.app.data.SessionHeader;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.data.SessionRecorder;
import com.pocketlab.app.data.SessionRepository;
import com.pocketlab.app.data.SessionText;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorHub;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;
import com.pocketlab.app.work.SyncScheduler;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Owns the free recording for the whole process, so it survives screen rotation, the activity
 * being closed, and the app going to the background. {@link RecordingService} keeps the process
 * alive (and shows the notification) while a recording runs. Main thread only.
 */
public final class RecordingController implements SensorHub.Listener {

    private final Application app;
    private final SensorHub hub;
    private final SessionRepository repo;
    private final List<SensorType> available;

    @Nullable private SensorType selectedType;
    private SamplingRate selectedRate = SamplingRate.GAME;
    @Nullable private RecordingSession session;
    @Nullable private SensorHub.Subscription subscription;
    @Nullable private SessionRecorder recorder;
    private boolean foregroundActive;

    public RecordingController(Application app, SensorHub hub, SessionRepository repo) {
        this.app = app;
        this.hub = hub;
        this.repo = repo;
        this.available = hub.getAvailableTypes();
        this.selectedType = available.isEmpty() ? null : available.get(0);
    }

    public List<SensorType> getAvailableTypes() { return available; }
    @Nullable public SensorType getSelectedType() { return selectedType; }
    public SamplingRate getSelectedRate() { return selectedRate; }
    @Nullable public RecordingSession getSession() { return session; }

    public RecordingState getState() {
        return session == null ? RecordingState.IDLE : session.getState();
    }

    /** True while recording or paused (the selection must not change then). */
    public boolean isActive() {
        RecordingState s = getState();
        return s == RecordingState.RECORDING || s == RecordingState.PAUSED;
    }

    public void setSelectedType(SensorType type) {
        if (!isActive()) selectedType = type;
    }

    public void setSelectedRate(SamplingRate rate) {
        if (!isActive()) selectedRate = rate;
    }

    /** Starts a recording and the foreground service. False if no sensor or registration failed. */
    public boolean start() {
        if (selectedType == null || isActive()) return false;
        RecordingSession s = new RecordingSession(selectedType, selectedRate, RecordingSession.DEFAULT_CAPACITY);
        SensorHub.Subscription sub = hub.subscribe(selectedType, selectedRate, this);
        if (sub == null) return false;

        long now = System.currentTimeMillis();
        SessionRecorder rec = repo.newRecorder(new SessionHeader(
                SessionText.defaultName(selectedType.getDisplayName(), now, Locale.getDefault(), TimeZone.getDefault()),
                now, selectedType, selectedRate, SessionKind.FREE, null, null));
        rec.begin();
        s.setSampleSink(rec);
        s.start();
        session = s;
        subscription = sub;
        recorder = rec;
        startService();
        return true;
    }

    public void pause() {
        if (session != null && session.getState() == RecordingState.RECORDING) {
            session.pause();
            cancelSubscription();
        }
    }

    /** False if the sensor could not be registered again. */
    public boolean resume() {
        if (session == null || session.getState() != RecordingState.PAUSED) return false;
        SensorHub.Subscription sub = hub.subscribe(session.getType(), session.getRate(), this);
        if (sub == null) return false;
        subscription = sub;
        session.resume();
        return true;
    }

    /** Stops and saves the recording (also used by the service for Stop, low battery and the time limit). */
    public void stop() {
        if (isActive() && session != null) {
            session.stop();
            cancelSubscription();
            finishRecorder();
        }
    }

    public void addMarker() {
        if (session != null) session.addMarker();
    }

    /**
     * The app left the foreground. With the foreground service running nothing needs to change;
     * if the service could not start, pause so no half-working recording is left behind.
     */
    public void onAppBackgrounded() {
        if (!foregroundActive) pause();
    }

    /** Set by {@link RecordingService} while it is running in the foreground. */
    void setForegroundActive(boolean active) {
        foregroundActive = active;
    }

    @Override
    public void onSample(@NonNull SensorSample sample) {
        if (session != null) session.onSample(sample);
    }

    private void startService() {
        try {
            ContextCompat.startForegroundService(app, new Intent(app, RecordingService.class));
        } catch (RuntimeException e) {
            // For example "not allowed to start a foreground service now": fall back to pausing
            // when the app is backgrounded.
            foregroundActive = false;
        }
    }

    private void cancelSubscription() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    private void finishRecorder() {
        if (recorder == null || session == null) return;
        if (session.hasSamples()) {
            recorder.finish(session, null);
            SyncScheduler.onSessionSaved(app);   // backs it up soon if automatic backup is on
        } else {
            recorder.discard();
        }
        recorder = null;
    }
}
