package com.pocketlab.app.cloud;

import com.pocketlab.app.graph.Marker;

import java.util.List;

/**
 * What is backed up for one recording: a summary, not the raw samples (Firestore documents are
 * small and billed per read/write). Pure data; see {@link CloudMapper} for the stored form.
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

    public CloudSession(String name, long createdAtMillis, String sensor, String samplingRate, String kind,
                        String experimentId, String experimentTitle, double durationSeconds, long sampleCount,
                        String resultText, List<Marker> markers, List<Stat> stats, long modifiedAt) {
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
    }
}
