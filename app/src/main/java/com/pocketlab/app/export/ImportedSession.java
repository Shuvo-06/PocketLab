package com.pocketlab.app.export;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import java.util.List;

/** A recording read from a file and checked. Sample arrays may be longer than {@code count}. */
public final class ImportedSession {
    public final String name;
    public final long createdAtMillis;
    public final SensorType sensor;
    public final SamplingRate rate;
    public final String experimentId;     // may be null
    public final String experimentTitle;  // may be null
    public final double durationSeconds;
    public final String resultText;       // may be null
    public final List<Marker> markers;
    public final int count;
    public final double[] times;
    public final float[][] values;        // [3][...]

    ImportedSession(String name, long createdAtMillis, SensorType sensor, SamplingRate rate,
                    String experimentId, String experimentTitle, double durationSeconds,
                    String resultText, List<Marker> markers, int count, double[] times, float[][] values) {
        this.name = name;
        this.createdAtMillis = createdAtMillis;
        this.sensor = sensor;
        this.rate = rate;
        this.experimentId = experimentId;
        this.experimentTitle = experimentTitle;
        this.durationSeconds = durationSeconds;
        this.resultText = resultText;
        this.markers = markers;
        this.count = count;
        this.times = times;
        this.values = values;
    }
}
