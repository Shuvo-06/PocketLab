package com.pocketlab.app.graph;

/** "Nice" axis tick spacing (1, 2, 5 x 10^n), after Heckbert's algorithm. */
public final class NiceScale {

    private NiceScale() {}

    /** A round tick spacing giving roughly {@code maxTicks} ticks across {@code range}. */
    public static double niceStep(double range, int maxTicks) {
        if (!(range > 0) || maxTicks < 2) return 1;
        double raw = range / (maxTicks - 1);
        double exp = Math.floor(Math.log10(raw));
        double f = raw / Math.pow(10, exp);
        double nf;
        if (f < 1.5) nf = 1;
        else if (f < 3) nf = 2;
        else if (f < 7) nf = 5;
        else nf = 10;
        return nf * Math.pow(10, exp);
    }

    /** All multiples of {@code step} inside [min, max]. */
    public static double[] ticks(double min, double max, double step) {
        if (!(step > 0) || max < min) return new double[0];
        long first = (long) Math.ceil(min / step - 1e-9);
        long last = (long) Math.floor(max / step + 1e-9);
        int n = (int) Math.min(1000, Math.max(0, last - first + 1));
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = (first + i) * step;
        return out;
    }

    /** Decimal places needed to print multiples of {@code step} exactly (0..6). */
    public static int decimalsForStep(double step) {
        if (!(step > 0)) return 0;
        int d = (int) Math.ceil(-Math.log10(step) - 1e-9);
        return Math.max(0, Math.min(6, d));
    }
}
