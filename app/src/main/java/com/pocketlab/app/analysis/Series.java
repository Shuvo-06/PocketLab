package com.pocketlab.app.analysis;

import java.util.Arrays;

/**
 * Immutable time series: times in seconds (non-decreasing) and one value per time.
 * Arrays are copied on the way in and out.
 */
public final class Series {

    private final double[] times;
    private final double[] values;

    public Series(double[] times, double[] values) {
        if (times == null || values == null || times.length != values.length) {
            throw new IllegalArgumentException("times and values must have the same length");
        }
        for (int i = 0; i < times.length; i++) {
            if (Double.isNaN(times[i]) || Double.isNaN(values[i])) {
                throw new IllegalArgumentException("NaN at index " + i);
            }
            if (i > 0 && times[i] < times[i - 1]) {
                throw new IllegalArgumentException("times must be non-decreasing (index " + i + ")");
            }
        }
        this.times = times.clone();
        this.values = values.clone();
    }

    public int size() { return times.length; }
    public double time(int i) { return times[i]; }
    public double value(int i) { return values[i]; }
    public double[] timesCopy() { return times.clone(); }
    public double[] valuesCopy() { return values.clone(); }

    /** Last time minus first time (0 for fewer than two samples). */
    public double duration() {
        return times.length < 2 ? 0 : times[times.length - 1] - times[0];
    }

    /** Average sampling rate in Hz, or 0 if it cannot be determined. */
    public double averageRateHz() {
        double d = duration();
        return d > 0 ? (times.length - 1) / d : 0;
    }

    /** Samples with t0 &lt;= time &lt;= t1. */
    public Series slice(double t0, double t1) {
        int from = 0;
        while (from < times.length && times[from] < t0) from++;
        int to = from;
        while (to < times.length && times[to] <= t1) to++;
        return new Series(Arrays.copyOfRange(times, from, to), Arrays.copyOfRange(values, from, to));
    }

    /** Same times, mean subtracted from the values. */
    public Series demeaned() {
        if (values.length == 0) return this;
        double m = Stats.mean(values);
        double[] out = new double[values.length];
        for (int i = 0; i < out.length; i++) out[i] = values[i] - m;
        return new Series(times, out);
    }

    /** Same times, new values (must have the same length). */
    public Series withValues(double[] newValues) {
        return new Series(times, newValues);
    }
}
