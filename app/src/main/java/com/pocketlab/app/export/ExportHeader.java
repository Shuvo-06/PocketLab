package com.pocketlab.app.export;

import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

/** Session facts that go at the top of every export. experimentId/experimentTitle/resultText may be null. */
public final class ExportHeader {
    public final String name;
    public final long createdAtMillis;
    public final SensorType sensor;
    public final SamplingRate rate;
    public final SessionKind kind;
    public final String experimentId;
    public final String experimentTitle;
    public final double durationSeconds;
    public final long sampleCount;
    public final String resultText;

    public ExportHeader(String name, long createdAtMillis, SensorType sensor, SamplingRate rate,
                        SessionKind kind, String experimentId, String experimentTitle,
                        double durationSeconds, long sampleCount, String resultText) {
        this.name = name;
        this.createdAtMillis = createdAtMillis;
        this.sensor = sensor;
        this.rate = rate;
        this.kind = kind;
        this.experimentId = experimentId;
        this.experimentTitle = experimentTitle;
        this.durationSeconds = durationSeconds;
        this.sampleCount = sampleCount;
        this.resultText = resultText;
    }
}
