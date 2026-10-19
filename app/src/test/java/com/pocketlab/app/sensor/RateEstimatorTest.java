package com.pocketlab.app.sensor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RateEstimatorTest {

    @Test
    public void lessThanTwoSamplesGivesZero() {
        RateEstimator e = new RateEstimator(10);
        assertEquals(0, e.getHz(), 0);
        e.add(1_000_000_000L);
        assertEquals(0, e.getHz(), 0);
    }

    @Test
    public void tenMillisecondSpacingIs100Hz() {
        RateEstimator e = new RateEstimator(50);
        for (int i = 0; i < 20; i++) e.add(i * 10_000_000L);
        assertEquals(100.0, e.getHz(), 1e-6);
    }

    @Test
    public void windowForgetsOldRate() {
        RateEstimator e = new RateEstimator(5);
        for (int i = 0; i < 10; i++) e.add(i * 100_000_000L);        // 10 Hz
        long start = 10 * 100_000_000L;
        for (int i = 0; i < 10; i++) e.add(start + i * 20_000_000L); // 50 Hz
        assertEquals(50.0, e.getHz(), 1e-6);
    }

    @Test
    public void resetClearsState() {
        RateEstimator e = new RateEstimator(5);
        e.add(0);
        e.add(10_000_000L);
        e.reset();
        assertEquals(0, e.getHz(), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void windowTooSmallRejected() {
        new RateEstimator(1);
    }
}
