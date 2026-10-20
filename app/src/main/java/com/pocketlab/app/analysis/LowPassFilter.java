package com.pocketlab.app.analysis;

/**
 * First-order (single-pole) low-pass filter that uses the real time step of every
 * sample, so it also works for the uneven spacing of Android sensor timestamps.
 *
 * <p>{@link #filter} lags the signal slightly. {@link #filterZeroPhase} runs the filter
 * forward and backward, which removes the lag (use it for period/peak work), at the cost
 * of a steeper effective cutoff.
 */
public final class LowPassFilter {

    private LowPassFilter() {}

    /** Smoothing factor for one step of {@code dtSeconds} (0 &lt; alpha &lt;= 1). */
    public static double alpha(double cutoffHz, double dtSeconds) {
        if (!(cutoffHz > 0)) throw new IllegalArgumentException("cutoffHz must be > 0");
        if (dtSeconds <= 0) return 1.0; // no time passed: pass the new sample through
        double rc = 1.0 / (2 * Math.PI * cutoffHz);
        return dtSeconds / (rc + dtSeconds);
    }

    public static Series filter(Series s, double cutoffHz) {
        int n = s.size();
        double[] out = new double[n];
        if (n == 0) return s;
        out[0] = s.value(0);
        for (int i = 1; i < n; i++) {
            double a = alpha(cutoffHz, s.time(i) - s.time(i - 1));
            out[i] = out[i - 1] + a * (s.value(i) - out[i - 1]);
        }
        return s.withValues(out);
    }

    public static Series filterZeroPhase(Series s, double cutoffHz) {
        int n = s.size();
        if (n == 0) return s;
        double[] fwd = filter(s, cutoffHz).valuesCopy();
        double[] out = new double[n];
        out[n - 1] = fwd[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            double a = alpha(cutoffHz, s.time(i + 1) - s.time(i));
            out[i] = out[i + 1] + a * (fwd[i] - out[i + 1]);
        }
        return s.withValues(out);
    }
}
