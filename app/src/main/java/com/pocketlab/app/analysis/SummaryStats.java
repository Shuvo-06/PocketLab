package com.pocketlab.app.analysis;

/** Count, mean, sample standard deviation, min and max of one data set. */
public final class SummaryStats {

    private final int count;
    private final double mean;
    private final double stdDev;
    private final double min;
    private final double max;

    private SummaryStats(int count, double mean, double stdDev, double min, double max) {
        this.count = count;
        this.mean = mean;
        this.stdDev = stdDev;
        this.min = min;
        this.max = max;
    }

    public static SummaryStats of(double[] v) {
        return new SummaryStats(v.length, Stats.mean(v), Stats.stdDev(v), Stats.min(v), Stats.max(v));
    }

    public int getCount() { return count; }
    public double getMean() { return mean; }
    /** Sample standard deviation; NaN for a single value. */
    public double getStdDev() { return stdDev; }
    public double getMin() { return min; }
    public double getMax() { return max; }
}
