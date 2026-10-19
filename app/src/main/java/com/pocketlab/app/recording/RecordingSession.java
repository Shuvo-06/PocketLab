package com.pocketlab.app.recording;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.graph.SeriesBuffer;
import com.pocketlab.app.sensor.RateEstimator;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One recording of one sensor. Pure Java (no Android UI), so it is unit-testable.
 *
 * <p>Time axis: seconds since the first accepted sample, computed from the sensor's own
 * timestamps. While paused no time passes: on resume the gap is closed, so the graph
 * continues seamlessly. The graph keeps only the newest {@code capacity} points per axis,
 * but {@link #getTotalSamples()} counts everything (Phase 6 will persist all samples).
 */
public class RecordingSession {

    public static final int DEFAULT_CAPACITY = 6000;

    private final SensorType type;
    private final SamplingRate rate;
    private final List<SeriesBuffer> series;
    private final List<Marker> markers = new ArrayList<>();
    private final List<Marker> markersView = Collections.unmodifiableList(markers);
    private final RateEstimator estimator = new RateEstimator(50);

    private RecordingState state = RecordingState.IDLE;
    private boolean hasFirst;
    private boolean closeGap;
    private long firstTs;
    private long lastTs;
    private long gapNanos;
    private long totalSamples;
    private double latestTime;
    private float[] latestValues = new float[0];

    public RecordingSession(SensorType type, SamplingRate rate, int capacity) {
        this.type = type;
        this.rate = rate;
        List<SeriesBuffer> list = new ArrayList<>();
        for (int i = 0; i < type.getAxes(); i++) list.add(new SeriesBuffer(capacity));
        this.series = Collections.unmodifiableList(list);
    }

    // ---- state transitions ----

    public void start() {
        require(state == RecordingState.IDLE, "start() only from IDLE");
        state = RecordingState.RECORDING;
    }

    public void pause() {
        require(state == RecordingState.RECORDING, "pause() only while RECORDING");
        state = RecordingState.PAUSED;
    }

    public void resume() {
        require(state == RecordingState.PAUSED, "resume() only while PAUSED");
        state = RecordingState.RECORDING;
        closeGap = true;
    }

    public void stop() {
        require(state == RecordingState.RECORDING || state == RecordingState.PAUSED,
                "stop() only while RECORDING or PAUSED");
        state = RecordingState.STOPPED;
    }

    private static void require(boolean ok, String msg) {
        if (!ok) throw new IllegalStateException(msg);
    }

    // ---- data in ----

    /** Accepts a sample only while RECORDING and only for this session's sensor. */
    public void onSample(SensorSample s) {
        if (state != RecordingState.RECORDING || s.getType() != type) return;
        long ts = s.getTimestampNanos();
        if (!hasFirst) {
            firstTs = ts;
            hasFirst = true;
        } else if (closeGap) {
            gapNanos += ts - lastTs;
        }
        closeGap = false;
        lastTs = ts;

        double t = (ts - firstTs - gapNanos) / 1e9;
        latestTime = t;
        latestValues = s.getValues();
        for (int i = 0; i < series.size(); i++) series.get(i).add(t, latestValues[i]);
        estimator.add(ts);
        totalSamples++;
    }

    /** Adds a marker at the current recording time. Returns null if not recording yet. */
    public Marker addMarker() {
        if (state != RecordingState.RECORDING || !hasFirst) return null;
        Marker m = new Marker(latestTime, "M" + (markers.size() + 1));
        markers.add(m);
        return m;
    }

    // ---- data out ----

    public RecordingState getState() { return state; }
    public SensorType getType() { return type; }
    public SamplingRate getRate() { return rate; }
    public List<SeriesBuffer> getSeries() { return series; }
    /** Live, read-only view. */
    public List<Marker> getMarkers() { return markersView; }
    public long getTotalSamples() { return totalSamples; }
    public double getElapsedSeconds() { return latestTime; }
    public boolean hasSamples() { return totalSamples > 0; }
    public float[] getLatestValues() { return latestValues.clone(); }
    public double getMeasuredHz() { return estimator.getHz(); }
    public int getCapacity() { return series.get(0).getCapacity(); }
    public boolean isGraphTruncated() { return totalSamples > getCapacity(); }
}
