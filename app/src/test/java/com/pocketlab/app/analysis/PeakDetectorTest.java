package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PeakDetectorTest {

    @Test
    public void findsAllPeaksOfSine() {
        Series s = TestSignals.sine(1.0, 1.0, 0, 100, 5, 0, 1); // peaks at 0.25 + k
        int[] p = PeakDetector.findPeaks(s, 0.5, 0.2);
        assertEquals(5, p.length);
        for (int k = 0; k < p.length; k++) assertEquals(0.25 + k, s.time(p[k]), 0.011);
    }

    @Test
    public void prominenceRejectsSmallNoiseBumps() {
        // Big sine with small ripple: ripple creates many tiny local maxima.
        int n = 1001;
        double[] t = new double[n], v = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = i * 0.01;
            v[i] = Math.sin(2 * Math.PI * t[i]) + 0.05 * Math.sin(2 * Math.PI * 25 * t[i]);
        }
        Series s = new Series(t, v);
        assertEquals(10, PeakDetector.findPeaks(s, 0.5, 0).length);
        assertEquals(true, PeakDetector.findPeaks(s, 0.0, 0).length > 10);
    }

    @Test
    public void minSeparationKeepsTheHigherPeak() {
        // peaks at index 2 (value 5) and index 4 (value 8), 0.2 s apart
        Series s = new Series(new double[]{0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6},
                new double[]{0, 1, 5, 1, 8, 1, 0});
        int[] p = PeakDetector.findPeaks(s, 1.0, 0.5);
        assertEquals(1, p.length);
        assertEquals(4, p[0]);
        assertEquals(2, PeakDetector.findPeaks(s, 1.0, 0.1).length);
    }

    @Test
    public void flatTopCountsOnce() {
        Series s = new Series(new double[]{0, 1, 2, 3, 4}, new double[]{0, 3, 3, 3, 0});
        assertEquals(1, PeakDetector.findPeaks(s, 1.0, 0).length);
    }

    @Test
    public void edgesAndShortInputsGiveNoPeaks() {
        assertEquals(0, PeakDetector.findPeaks(new Series(new double[]{0, 1}, new double[]{1, 2}), 0, 0).length);
        Series ramp = new Series(new double[]{0, 1, 2, 3}, new double[]{0, 1, 2, 3});
        assertEquals(0, PeakDetector.findPeaks(ramp, 0, 0).length);
    }
}
