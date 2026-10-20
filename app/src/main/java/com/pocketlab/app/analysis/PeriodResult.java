package com.pocketlab.app.analysis;

/** Result of a period estimate: mean period, spread, and how many periods were measured. */
public final class PeriodResult {

    private final double periodSeconds;
    private final double stdDevSeconds;
    private final int periodCount;

    PeriodResult(double periodSeconds, double stdDevSeconds, int periodCount) {
        this.periodSeconds = periodSeconds;
        this.stdDevSeconds = stdDevSeconds;
        this.periodCount = periodCount;
    }

    /** Mean of the individual periods, in seconds. */
    public double getPeriodSeconds() { return periodSeconds; }
    /** Sample standard deviation of the individual periods; NaN if only one period was measured. */
    public double getStdDevSeconds() { return stdDevSeconds; }
    /** Number of full periods measured (events minus one). */
    public int getPeriodCount() { return periodCount; }
    public double getFrequencyHz() { return 1.0 / periodSeconds; }
}
