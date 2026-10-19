package com.pocketlab.app.sensor;

/** Estimates the real delivery rate (Hz) from sensor timestamps over a sliding window. */
public class RateEstimator {

    private final long[] window;
    private int count;
    private int next;

    public RateEstimator(int windowSize) {
        if (windowSize < 2) throw new IllegalArgumentException("windowSize must be >= 2");
        this.window = new long[windowSize];
    }

    public void add(long timestampNanos) {
        window[next] = timestampNanos;
        next = (next + 1) % window.length;
        if (count < window.length) count++;
    }

    public void reset() {
        count = 0;
        next = 0;
    }

    /** Estimated Hz, or 0 when fewer than two samples (or no time elapsed). */
    public double getHz() {
        if (count < 2) return 0;
        int newestIdx = (next - 1 + window.length) % window.length;
        int oldestIdx = count < window.length ? 0 : next;
        long spanNanos = window[newestIdx] - window[oldestIdx];
        if (spanNanos <= 0) return 0;
        return (count - 1) * 1_000_000_000.0 / spanNanos;
    }
}
