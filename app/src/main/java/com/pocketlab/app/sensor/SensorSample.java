package com.pocketlab.app.sensor;

import java.util.Arrays;

/**
 * One immutable measurement. {@code timestampNanos} is the sensor's own event time
 * (nanoseconds, monotonic), far more accurate for period/frequency work than the
 * time our callback happens to run.
 */
public final class SensorSample {

    private final SensorType type;
    private final long timestampNanos;
    private final float[] values;
    private final int accuracy;

    public SensorSample(SensorType type, long timestampNanos, float[] values, int accuracy) {
        this.type = type;
        this.timestampNanos = timestampNanos;
        this.values = Arrays.copyOf(values, type.getAxes());
        this.accuracy = accuracy;
    }

    public SensorType getType() { return type; }
    public long getTimestampNanos() { return timestampNanos; }
    public float getValue(int index) { return values[index]; }
    public float[] getValues() { return values.clone(); }
    /** One of SensorManager.SENSOR_STATUS_ACCURACY_* (0 = unreliable ... 3 = high). */
    public int getAccuracy() { return accuracy; }
}
