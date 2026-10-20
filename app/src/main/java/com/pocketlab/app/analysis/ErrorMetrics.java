package com.pocketlab.app.analysis;

/** Comparing a measured value with a theoretical or reference value. */
public final class ErrorMetrics {

    private ErrorMetrics() {}

    public static double absoluteError(double measured, double reference) {
        return Math.abs(measured - reference);
    }

    /** |measured - reference| / |reference| x 100. NaN when the reference is 0 (undefined). */
    public static double percentageError(double measured, double reference) {
        if (reference == 0) return Double.NaN;
        return Math.abs(measured - reference) / Math.abs(reference) * 100.0;
    }
}
