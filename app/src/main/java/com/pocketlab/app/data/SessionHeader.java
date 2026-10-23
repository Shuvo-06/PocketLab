package com.pocketlab.app.data;

import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

/** What is known about a recording when it starts. experimentId/experimentTitle may be null. */
public final class SessionHeader {
    public final String name;
    public final long createdAtMillis;
    public final SensorType sensor;
    public final SamplingRate rate;
    public final SessionKind kind;
    public final String experimentId;
    public final String experimentTitle;

    public SessionHeader(String name, long createdAtMillis, SensorType sensor, SamplingRate rate,
                         SessionKind kind, String experimentId, String experimentTitle) {
        this.name = name;
        this.createdAtMillis = createdAtMillis;
        this.sensor = sensor;
        this.rate = rate;
        this.kind = kind;
        this.experimentId = experimentId;
        this.experimentTitle = experimentTitle;
    }
}
