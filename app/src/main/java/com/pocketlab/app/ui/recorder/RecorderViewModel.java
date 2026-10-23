package com.pocketlab.app.ui.recorder;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;
import com.pocketlab.app.service.RecordingController;

import java.util.List;

/**
 * Thin facade over the process-level {@link RecordingController}. The recording itself no longer
 * lives here, so it keeps running when this ViewModel (or the whole screen) is gone.
 */
public class RecorderViewModel extends AndroidViewModel {

    private final RecordingController controller;

    public RecorderViewModel(@NonNull Application application) {
        super(application);
        controller = ((PocketLabApp) application).getRecordingController();
    }

    public List<SensorType> getAvailableTypes() { return controller.getAvailableTypes(); }
    @Nullable public SensorType getSelectedType() { return controller.getSelectedType(); }
    public SamplingRate getSelectedRate() { return controller.getSelectedRate(); }
    @Nullable public RecordingSession getSession() { return controller.getSession(); }
    public RecordingState getState() { return controller.getState(); }
    public boolean isActive() { return controller.isActive(); }

    public void setSelectedType(SensorType type) { controller.setSelectedType(type); }
    public void setSelectedRate(SamplingRate rate) { controller.setSelectedRate(rate); }

    public boolean start() { return controller.start(); }
    public void pause() { controller.pause(); }
    public boolean resume() { return controller.resume(); }
    public void stop() { controller.stop(); }
    public void addMarker() { controller.addMarker(); }
    public void onAppBackgrounded() { controller.onAppBackgrounded(); }
}
