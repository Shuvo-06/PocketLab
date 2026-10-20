package com.pocketlab.app.analysis;

/**
 * Estimates the period of an oscillation (for example a pendulum).
 * Both methods return null when fewer than two events are found.
 */
public final class PeriodEstimator {

    private PeriodEstimator() {}

    /** Period from the spacing of detected peaks. Resolution is limited by the sample spacing. */
    public static PeriodResult fromPeaks(Series s, double minProminence, double minSeparationSeconds) {
        int[] peaks = PeakDetector.findPeaks(s, minProminence, minSeparationSeconds);
        double[] times = new double[peaks.length];
        for (int i = 0; i < peaks.length; i++) times[i] = s.time(peaks[i]);
        return fromEventTimes(times);
    }

    /**
     * Period from rising crossings of the signal's mean, with linear interpolation between
     * samples, so it is finer than the sample spacing and ignores any constant offset (such
     * as gravity). {@code hysteresis} (same unit as the values, &gt;= 0) is a noise band: the
     * signal must fall below mean-h and then rise above mean+h to count as one cycle.
     */
    public static PeriodResult fromZeroCrossings(Series s, double hysteresis) {
        int n = s.size();
        if (n < 3) return null;
        if (hysteresis < 0) throw new IllegalArgumentException("hysteresis must be >= 0");
        double mean = Stats.mean(s.valuesCopy());
        double hi = mean + hysteresis, lo = mean - hysteresis;

        double[] crossings = new double[n];
        int count = 0;
        int state = 0; // -1 below the band, +1 above, 0 not decided yet
        for (int j = 0; j < n; j++) {
            double v = s.value(j);
            if (v < lo) {
                state = -1;
            } else if (v > hi) {
                if (state == -1) {
                    int k = j - 1;
                    while (k > 0 && s.value(k) > mean) k--;
                    // s.value(k) <= mean < s.value(k + 1)
                    double v0 = s.value(k), v1 = s.value(k + 1);
                    double frac = v1 == v0 ? 0 : (mean - v0) / (v1 - v0);
                    crossings[count++] = s.time(k) + frac * (s.time(k + 1) - s.time(k));
                }
                state = 1;
            }
        }
        double[] times = new double[count];
        System.arraycopy(crossings, 0, times, 0, count);
        return fromEventTimes(times);
    }

    private static PeriodResult fromEventTimes(double[] times) {
        if (times.length < 2) return null;
        double[] periods = new double[times.length - 1];
        for (int i = 0; i < periods.length; i++) periods[i] = times[i + 1] - times[i];
        return new PeriodResult(Stats.mean(periods), Stats.stdDev(periods), periods.length);
    }
}
