package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PeriodEstimatorTest {

    @Test
    public void zeroCrossingsGivesSubSampleAccuracy() {
        Series s = TestSignals.sine(0.8, 1.0, 0, 50, 10, 0, 1); // sample spacing 0.02 s
        PeriodResult r = PeriodEstimator.fromZeroCrossings(s, 0);
        assertNotNull(r);
        assertEquals(0.8, r.getPeriodSeconds(), 1e-3);
        assertEquals(1.25, r.getFrequencyHz(), 0.01);
        assertTrue(r.getStdDevSeconds() < 1e-3);
        assertTrue(r.getPeriodCount() >= 10);
    }

    @Test
    public void ignoresConstantOffsetLikeGravity() {
        Series s = TestSignals.sine(1.6, 0.5, 9.81, 100, 20, 0, 1);
        PeriodResult r = PeriodEstimator.fromZeroCrossings(s, 0);
        assertEquals(1.6, r.getPeriodSeconds(), 1e-3);
    }

    @Test
    public void hysteresisSurvivesNoise() {
        Series s = TestSignals.sine(0.8, 1.0, 0, 100, 12, 0.15, 42);
        PeriodResult noisyNoBand = PeriodEstimator.fromZeroCrossings(s, 0);
        PeriodResult withBand = PeriodEstimator.fromZeroCrossings(s, 0.3);
        assertNotNull(withBand);
        assertEquals(0.8, withBand.getPeriodSeconds(), 0.01);
        assertTrue(noisyNoBand == null || noisyNoBand.getPeriodSeconds() <= withBand.getPeriodSeconds() + 1e-9);
    }

    @Test
    public void peaksMethodWithinOneSample() {
        Series s = TestSignals.sine(0.8, 1.0, 0, 50, 10, 0, 1);
        PeriodResult r = PeriodEstimator.fromPeaks(s, 0.5, 0.3);
        assertNotNull(r);
        assertEquals(0.8, r.getPeriodSeconds(), 0.02);
    }

    @Test
    public void tooFewEventsGivesNull() {
        Series s = TestSignals.sine(10, 1.0, 0, 50, 5, 0, 1); // half a cycle
        assertNull(PeriodEstimator.fromZeroCrossings(s, 0));
        assertNull(PeriodEstimator.fromPeaks(s, 0.1, 0.1));
        assertNull(PeriodEstimator.fromZeroCrossings(new Series(new double[]{0, 1}, new double[]{0, 1}), 0));
    }

    @Test
    public void flatSignalGivesNull() {
        Series s = TestSignals.sine(1, 0, 3.0, 50, 5, 0, 1);
        assertNull(PeriodEstimator.fromZeroCrossings(s, 0.1));
    }
}
