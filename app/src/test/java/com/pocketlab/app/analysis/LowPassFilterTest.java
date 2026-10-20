package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LowPassFilterTest {

    private static double peakAmplitudeAfterWarmup(Series s, double warmupSeconds) {
        double max = 0;
        for (int i = 0; i < s.size(); i++) {
            if (s.time(i) >= warmupSeconds) max = Math.max(max, Math.abs(s.value(i)));
        }
        return max;
    }

    @Test
    public void constantSignalUnchanged() {
        Series s = TestSignals.sine(1, 0, 5.0, 100, 2, 0, 1);
        Series f = LowPassFilter.filter(s, 2);
        for (int i = 0; i < f.size(); i++) assertEquals(5.0, f.value(i), 1e-12);
    }

    @Test
    public void attenuatesHighFrequency() {
        Series s = TestSignals.sine(0.1, 1, 0, 200, 5, 0, 1); // 10 Hz
        double amp = peakAmplitudeAfterWarmup(LowPassFilter.filter(s, 1.0), 1.0);
        assertTrue("amp=" + amp, amp < 0.2);
    }

    @Test
    public void passesLowFrequency() {
        Series s = TestSignals.sine(10, 1, 0, 100, 40, 0, 1); // 0.1 Hz
        double amp = peakAmplitudeAfterWarmup(LowPassFilter.filter(s, 5.0), 5.0);
        assertTrue("amp=" + amp, amp > 0.95);
    }

    @Test
    public void stepResponseRisesMonotonicallyTowardsTarget() {
        double[] t = new double[200], v = new double[200];
        for (int i = 0; i < 200; i++) { t[i] = i * 0.01; v[i] = i < 10 ? 0 : 1; }
        Series f = LowPassFilter.filter(new Series(t, v), 2.0);
        for (int i = 11; i < 200; i++) assertTrue(f.value(i) >= f.value(i - 1));
        assertTrue(f.value(199) > 0.99);
    }

    @Test
    public void zeroPhaseKeepsPeakPosition() {
        Series s = TestSignals.sine(2, 1, 0, 100, 10, 0, 1);
        Series zp = LowPassFilter.filterZeroPhase(s, 3.0);
        Series lag = LowPassFilter.filter(s, 3.0);
        // sin(2 pi t / 2) peaks at t = 0.5, 2.5, ... ; look at the peak near t = 4.5
        int iz = argMax(zp, 4.0, 5.0), il = argMax(lag, 4.0, 5.0);
        assertEquals(4.5, zp.time(iz), 0.02);
        assertTrue(lag.time(il) > zp.time(iz));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveCutoffRejected() {
        LowPassFilter.alpha(0, 0.01);
    }

    private static int argMax(Series s, double t0, double t1) {
        int best = -1;
        for (int i = 0; i < s.size(); i++) {
            if (s.time(i) < t0 || s.time(i) > t1) continue;
            if (best < 0 || s.value(i) > s.value(best)) best = i;
        }
        return best;
    }
}
