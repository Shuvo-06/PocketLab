package com.pocketlab.app.ui.experiments;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.analysis.Series;
import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.data.SessionHeader;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.data.SessionRecorder;
import com.pocketlab.app.data.SessionText;
import com.pocketlab.app.experiment.ExperimentAnalyzer;
import com.pocketlab.app.experiment.ExperimentDefinition;
import com.pocketlab.app.experiment.ExperimentResult;
import com.pocketlab.app.net.ExperimentUpdater;
import com.pocketlab.app.net.WeatherApi;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;
import com.pocketlab.app.sensor.SensorHub;
import com.pocketlab.app.work.SyncScheduler;
import com.pocketlab.app.sensor.SensorSample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * State of the experiment wizard. Activity-scoped so a run survives rotation and tab switches.
 * Owns the sensor subscription and the recording for the running experiment.
 */
public class ExperimentViewModel extends AndroidViewModel implements SensorHub.Listener {

    public enum Kind { INTRO, INSTRUCTION, INPUTS, RECORD, RESULTS }

    public static final class Page {
        public final Kind kind;
        public final int step; // index into the steps list for INSTRUCTION pages

        Page(Kind kind, int step) {
            this.kind = kind;
            this.step = step;
        }
    }

    private final SensorHub hub;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor background = Executors.newSingleThreadExecutor();

    @Nullable private ExperimentDefinition def;
    private List<Page> pages = Collections.emptyList();
    private int pageIndex;
    private final Map<String, String> inputTexts = new HashMap<>();
    private final Map<String, Double> inputs = new HashMap<>();
    @Nullable private RecordingSession session;
    @Nullable private SensorHub.Subscription subscription;
    @Nullable private ExperimentResult result;
    @Nullable private SessionRecorder recorder;
    private boolean interrupted;
    private boolean stopPosted;
    private boolean analyzing;

    public ExperimentViewModel(@NonNull Application application) {
        super(application);
        hub = ((PocketLabApp) application).getSensorHub();
    }

    /**
     * Called by the wizard screen. {@code fresh} is true when the screen is newly opened
     * (not recreated by rotation): the wizard then restarts, unless that same experiment
     * is still recording.
     */
    public void open(ExperimentDefinition definition, boolean fresh) {
        boolean same = def != null && def.getId().equals(definition.getId());
        if (same && (!fresh || isRecording())) return;
        reset();
        def = definition;
        List<Page> p = new ArrayList<>();
        p.add(new Page(Kind.INTRO, -1));
        for (int i = 0; i < definition.getSteps().size(); i++) p.add(new Page(Kind.INSTRUCTION, i));
        if (!definition.getInputs().isEmpty()) p.add(new Page(Kind.INPUTS, -1));
        p.add(new Page(Kind.RECORD, -1));
        p.add(new Page(Kind.RESULTS, -1));
        pages = p;
    }

    private void discardRecorder() {
        if (recorder != null) {
            recorder.discard();
            recorder = null;
        }
    }

    private void reset() {
        cancelSubscription();
        discardRecorder();
        session = null;
        result = null;
        inputTexts.clear();
        inputs.clear();
        pageIndex = 0;
        interrupted = false;
        stopPosted = false;
        analyzing = false;
    }

    // ---- navigation ----

    public List<Page> getPages() { return pages; }
    public int getPageIndex() { return pageIndex; }
    public void goNext() { if (pageIndex < pages.size() - 1) pageIndex++; }
    public void goBack() { if (pageIndex > 0) pageIndex--; }

    // ---- inputs ----

    public String getInputText(String key) {
        String t = inputTexts.get(key);
        return t == null ? "" : t;
    }

    public void setInputText(String key, String text) { inputTexts.put(key, text); }

    /** @return error message per input key; empty when everything is valid */
    public Map<String, String> validateInputs() {
        Map<String, String> errors = new HashMap<>();
        inputs.clear();
        if (def == null) return errors;
        for (ExperimentDefinition.InputSpec spec : def.getInputs()) {
            try {
                Double v = spec.parse(getInputText(spec.key));
                if (v != null) inputs.put(spec.key, v);
            } catch (IllegalArgumentException e) {
                errors.put(spec.key, e.getMessage());
            }
        }
        // The weather lookup needs a complete position.
        if (inputs.containsKey("latitude") != inputs.containsKey("longitude")) {
            errors.put(inputs.containsKey("latitude") ? "longitude" : "latitude",
                    "Enter both coordinates, or leave both empty");
        }
        return errors;
    }

    public boolean isSensorAvailable() {
        return def != null && hub.isAvailable(def.getSensor());
    }

    // ---- recording ----

    @Nullable public RecordingSession getSession() { return session; }
    @Nullable public ExperimentResult getResult() { return result; }

    /** True while the finished run is being analysed (this can include a short web request). */
    public boolean isAnalyzing() { return analyzing; }

    public boolean isRecording() {
        return session != null && session.getState() == RecordingState.RECORDING;
    }

    public boolean startRun() {
        if (def == null || isRecording() || analyzing) return false;
        // The whole run is analysed from this buffer, so it must hold all of it
        // (allowing up to ~250 samples/s in case the device delivers faster than requested).
        int capacity = Math.max(6000, (int) Math.ceil(def.getMaxSeconds() * 250));
        RecordingSession s = new RecordingSession(def.getSensor(), def.getRate(), capacity);
        SensorHub.Subscription sub = hub.subscribe(def.getSensor(), def.getRate(), this);
        if (sub == null) return false;
        long now = System.currentTimeMillis();
        SessionRecorder rec = ((PocketLabApp) getApplication()).getSessionRepository().newRecorder(
                new SessionHeader(SessionText.defaultName(def.getTitle(), now,
                        Locale.getDefault(), TimeZone.getDefault()),
                        now, def.getSensor(), def.getRate(), SessionKind.EXPERIMENT,
                        def.getId(), def.getTitle(), AuthManager.uid(getApplication())));
        rec.begin();
        s.setSampleSink(rec);
        s.start();
        session = s;
        subscription = sub;
        recorder = rec;
        result = null;
        stopPosted = false;
        return true;
    }

    /**
     * Stops recording, analyses the data and moves to the results page. If the user entered coordinates,
     * the analysis first asks the Open-Meteo REST API for today's sea-level pressure (on a background
     * thread, so the screen never freezes); if that fails the result is still produced, with a note.
     */
    public void finishRun() {
        if (def == null || session == null || !isRecording()) return;
        final ExperimentDefinition d = def;
        final RecordingSession s = session;
        s.stop();
        cancelSubscription();
        final SessionRecorder rec = recorder;
        recorder = null;
        final Map<String, Double> in = new HashMap<>(inputs);
        final List<Series> axes = ExperimentAnalyzer.seriesFrom(s);
        analyzing = true;

        final Double lat = in.get("latitude");
        final Double lon = in.get("longitude");
        if (lat == null || lon == null) {
            complete(d, s, rec, in, axes, null);
            return;
        }
        background.execute(() -> {
            String note = null;
            try {
                in.put("seaLevelHpa", new WeatherApi().fetchSeaLevelPressure(lat, lon));
            } catch (IOException e) {
                note = "Weather reference unavailable: " + ExperimentUpdater.describe(e);
            } catch (RuntimeException e) {
                note = "Weather reference unavailable.";
            }
            final String finalNote = note;
            handler.post(() -> complete(d, s, rec, in, axes, finalNote));
        });
    }

    private void complete(ExperimentDefinition d, RecordingSession s, @Nullable SessionRecorder rec,
                          Map<String, Double> in, List<Series> axes, @Nullable String note) {
        analyzing = false;
        if (session != s) {            // the user left or started over while we were waiting
            if (rec != null) rec.discard();
            return;
        }
        ExperimentResult r = ExperimentAnalyzer.analyze(d, in, axes);
        if (note != null && r.isOk()) r = r.withNote(note);
        result = r;
        if (rec != null) {
            // Only successful runs are kept; a failed attempt would just clutter the history.
            if (r.isOk()) {
                rec.finish(s, r.toPlainText());
                SyncScheduler.onSessionSaved(getApplication());
            } else {
                rec.discard();
            }
        }
        pageIndex = pages.size() - 1;
    }

    /** Discards the run and returns to the record page. Inputs are kept. */
    public void runAgain() {
        cancelSubscription();
        session = null;
        result = null;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).kind == Kind.RECORD) pageIndex = i;
        }
    }

    /** The app left the foreground: a half-finished run would be unreliable, so discard it. */
    public void onAppBackgrounded() {
        if (isRecording()) {
            cancelSubscription();
            discardRecorder();
            session = null;
            interrupted = true;
        }
    }

    public boolean consumeInterrupted() {
        boolean was = interrupted;
        interrupted = false;
        return was;
    }

    @Override
    public void onSample(@NonNull SensorSample sample) {
        if (session == null || def == null) return;
        session.onSample(sample);
        if (!stopPosted && session.getElapsedSeconds() >= def.getMaxSeconds()) {
            stopPosted = true;
            handler.post(this::finishRun); // not inside the sensor callback
        }
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
        discardRecorder();
    }
}
