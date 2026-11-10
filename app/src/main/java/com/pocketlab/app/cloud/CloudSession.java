package com.pocketlab.app.cloud;

import com.pocketlab.app.graph.Marker;

import java.util.List;

/**
 * The cloud copy of one recording. The summary (this class) lives in Cloud Firestore; the raw
 * samples, if any, live in the Realtime Database in {@link #sampleChunks} pieces. Pure data; see
 * {@link CloudMapper} for the stored form.
 */
public final class CloudSession {

    /** Statistics for one axis. NaN means "not available". */
    public static final class Stat {
        public final String axis;
        public final double mean, stdDev, min, max;

        public Stat(String axis, double mean, double stdDev, double min, double max) {
            this.axis = axis;
            this.mean = mean;
            this.stdDev = stdDev;
            this.min = min;
            this.max = max;
        }
    }

    public final String name;
    public final long createdAtMillis;
    public final String sensor;        // SensorType enum name
    public final String samplingRate;  // SamplingRate enum name
    public final String kind;          // SessionKind enum name
    public final String experimentId;     // may be null
    public final String experimentTitle;  // may be null
    public final double durationSeconds;
    public final long sampleCount;
    public final String resultText;    // may be null
    public final List<Marker> markers;
    public final List<Stat> stats;
    /** Last change time (ms). Used to decide which side is newer. */
    public final long modifiedAt;
    /** How many pieces of raw samples are stored in the Realtime Database (0 = none uploaded). */
    public final int sampleChunks;

    public CloudSession(String name, long createdAtMillis, String sensor, String samplingRate, String kind,
                        String experimentId, String experimentTitle, double durationSeconds, long sampleCount,
                        String resultText, List<Marker> markers, List<Stat> stats, long modifiedAt, int sampleChunks) {
        this.name = name;
        this.createdAtMillis = createdAtMillis;
        this.sensor = sensor;
        this.samplingRate = samplingRate;
        this.kind = kind;
        this.experimentId = experimentId;
        this.experimentTitle = experimentTitle;
        this.durationSeconds = durationSeconds;
        this.sampleCount = sampleCount;
        this.resultText = resultText;
        this.markers = markers;
        this.stats = stats;
        this.modifiedAt = modifiedAt;
        this.sampleChunks = sampleChunks;
    }

    /** A summary without uploaded raw samples. */
    public CloudSession(String name, long createdAtMillis, String sensor, String samplingRate, String kind,
                        String experimentId, String experimentTitle, double durationSeconds, long sampleCount,
                        String resultText, List<Marker> markers, List<Stat> stats, long modifiedAt) {
        this(name, createdAtMillis, sensor, samplingRate, kind, experimentId, experimentTitle, durationSeconds,
                sampleCount, resultText, markers, stats, modifiedAt, 0);
    }

    public CloudSession withSampleChunks(int chunks) {
        return new CloudSession(name, createdAtMillis, sensor, samplingRate, kind, experimentId, experimentTitle,
                durationSeconds, sampleCount, resultText, markers, stats, modifiedAt, chunks);
    }
}
