package com.pocketlab.app.analysis;

/**
 * Statistics computed while streaming values one at a time (Welford's algorithm), so a huge
 * recording never has to be held in memory. Same results as {@link Stats}.
 */
public final class RunningStats {

    private long count;
    private double mean;
    private double m2;
    private double min = Double.NaN;
    private double max = Double.NaN;

    public void add(double x) {
        count++;
        double d = x - mean;
        mean += d / count;
        m2 += d * (x - mean);
        if (count == 1 || x < min) min = x;
        if (count == 1 || x > max) max = x;
    }

    public long getCount() { return count; }
    /** NaN when empty. */
    public double getMean() { return count == 0 ? Double.NaN : mean; }
    /** Sample standard deviation; NaN when fewer than two values. */
    public double getStdDev() { return count < 2 ? Double.NaN : Math.sqrt(m2 / (count - 1)); }
    public double getMin() { return min; }
    public double getMax() { return max; }
}
