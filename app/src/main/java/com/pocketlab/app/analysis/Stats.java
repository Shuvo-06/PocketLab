package com.pocketlab.app.analysis;

import java.util.Arrays;

/** Basic descriptive statistics. Empty input throws IllegalArgumentException. */
public final class Stats {

    private Stats() {}

    public static double mean(double[] v) {
        requireNonEmpty(v);
        double sum = 0;
        for (double x : v) sum += x;
        return sum / v.length;
    }

    /** Sample variance (divides by n-1). NaN when n &lt; 2. Two-pass for numerical stability. */
    public static double variance(double[] v) {
        requireNonEmpty(v);
        if (v.length < 2) return Double.NaN;
        double m = mean(v);
        double ss = 0;
        for (double x : v) ss += (x - m) * (x - m);
        return ss / (v.length - 1);
    }

    /** Sample standard deviation (n-1). NaN when n &lt; 2. */
    public static double stdDev(double[] v) {
        return Math.sqrt(variance(v));
    }

    /** Population standard deviation (divides by n). */
    public static double populationStdDev(double[] v) {
        requireNonEmpty(v);
        double m = mean(v);
        double ss = 0;
        for (double x : v) ss += (x - m) * (x - m);
        return Math.sqrt(ss / v.length);
    }

    public static double min(double[] v) {
        requireNonEmpty(v);
        double m = v[0];
        for (double x : v) if (x < m) m = x;
        return m;
    }

    public static double max(double[] v) {
        requireNonEmpty(v);
        double m = v[0];
        for (double x : v) if (x > m) m = x;
        return m;
    }

    public static double median(double[] v) {
        requireNonEmpty(v);
        double[] c = v.clone();
        Arrays.sort(c);
        int n = c.length;
        return n % 2 == 1 ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2.0;
    }

    /** Root mean square. */
    public static double rms(double[] v) {
        requireNonEmpty(v);
        double ss = 0;
        for (double x : v) ss += x * x;
        return Math.sqrt(ss / v.length);
    }

    private static void requireNonEmpty(double[] v) {
        if (v == null || v.length == 0) throw new IllegalArgumentException("empty data");
    }
}
