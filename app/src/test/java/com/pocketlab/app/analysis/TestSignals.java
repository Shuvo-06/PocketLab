package com.pocketlab.app.analysis;

import java.util.Random;

/** Synthetic signals for tests. */
final class TestSignals {
    private TestSignals() {}

    /** offset + amplitude * sin(2 pi t / period) sampled at rateHz for durationS seconds. */
    static Series sine(double period, double amplitude, double offset, double rateHz, double durationS, double noise, long seed) {
        int n = (int) Math.round(durationS * rateHz) + 1;
        double[] t = new double[n], v = new double[n];
        Random rnd = new Random(seed);
        for (int i = 0; i < n; i++) {
            t[i] = i / rateHz;
            v[i] = offset + amplitude * Math.sin(2 * Math.PI * t[i] / period)
                    + (noise > 0 ? (rnd.nextDouble() * 2 - 1) * noise : 0);
        }
        return new Series(t, v);
    }
}
