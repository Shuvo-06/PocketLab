package com.pocketlab.app.ui.recorder;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorHub;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import java.util.List;

/**
 * Owns the active recording. Scoped to the Activity, so a recording survives screen
 * rotation and switching between tabs. It owns the sensor subscription directly.
 */
public class RecorderViewModel extends AndroidViewModel implements SensorHub.Listener {

    private final SensorHub hub;
    private final List<SensorType> available;
    @Nullable private SensorType selectedType;
    private SamplingRate selectedRate = SamplingRate.GAME;
    @Nullable private RecordingSession session;
    @Nullable private SensorHub.Subscription subscription;

    public RecorderViewModel(@NonNull Application application) {
        super(application);
        hub = ((PocketLabApp) application).getSensorHub();
        available = hub.getAvailableTypes();
        selectedType = available.isEmpty() ? null : available.get(0);
    }

    public List<SensorType> getAvailableTypes() { return available; }
    @Nullable public SensorType getSelectedType() { return selectedType; }
    public SamplingRate getSelectedRate() { return selectedRate; }
    @Nullable public RecordingSession getSession() { return session; }

    public RecordingState getState() {
        return session == null ? RecordingState.IDLE : session.getState();
    }

    /** True while a recording is running or paused (selection must not change then). */
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

    /** Starts a new recording. Returns false if no sensor is selected or registration failed. */
    public boolean start() {
        if (selectedType == null || isActive()) return false;
        RecordingSession s = new RecordingSession(selectedType, selectedRate,
                RecordingSession.DEFAULT_CAPACITY);
        SensorHub.Subscription sub = hub.subscribe(selectedType, selectedRate, this);
        if (sub == null) return false;
        s.start();
        session = s;
        subscription = sub;
        return true;
    }

    public void pause() {
        if (session != null && session.getState() == RecordingState.RECORDING) {
            session.pause();
            cancelSubscription();
        }
    }

    /** Returns false if the sensor could not be re-registered. */
    public boolean resume() {
        if (session == null || session.getState() != RecordingState.PAUSED || selectedType == null) {
            return false;
        }
        SensorHub.Subscription sub = hub.subscribe(session.getType(), session.getRate(), this);
        if (sub == null) return false;
        subscription = sub;
        session.resume();
        return true;
    }

    public void stop() {
        if (isActive() && session != null) {
            session.stop();
            cancelSubscription();
        }
    }

    public void addMarker() {
        if (session != null) session.addMarker();
    }

    /** Called when the app leaves the foreground. */
    public void onAppBackgrounded() {
        pause();
    }

    @Override
    public void onSample(@NonNull SensorSample sample) {
        if (session != null) session.onSample(sample);
    }

    private void cancelSubscription() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    @Override
    protected void onCleared() {
        cancelSubscription();
    }
}
