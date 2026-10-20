package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SeriesTest {

    @Test
    public void basicsAndRate() {
        Series s = new Series(new double[]{0, 0.1, 0.2, 0.3, 0.4}, new double[]{1, 2, 3, 4, 5});
        assertEquals(5, s.size());
        assertEquals(0.4, s.duration(), 1e-12);
        assertEquals(10.0, s.averageRateHz(), 1e-9);
    }

    @Test
    public void sliceIsInclusive() {
        Series s = new Series(new double[]{0, 1, 2, 3, 4}, new double[]{10, 11, 12, 13, 14});
        Series c = s.slice(1, 3);
        assertEquals(3, c.size());
        assertEquals(11, c.value(0), 0);
        assertEquals(13, c.value(2), 0);
        assertEquals(0, s.slice(10, 20).size());
    }

    @Test
    public void demeanedHasZeroMean() {
        Series s = new Series(new double[]{0, 1, 2}, new double[]{9, 10, 11});
        assertEquals(0.0, Stats.mean(s.demeaned().valuesCopy()), 1e-12);
    }

    @Test
    public void inputArraysAreCopied() {
        double[] v = {1, 2};
        Series s = new Series(new double[]{0, 1}, v);
        v[0] = 99;
        assertEquals(1, s.value(0), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void lengthMismatchRejected() {
        new Series(new double[]{0, 1}, new double[]{1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void decreasingTimeRejected() {
        new Series(new double[]{0, 2, 1}, new double[]{1, 2, 3});
    }

    @Test(expected = IllegalArgumentException.class)
    public void nanRejected() {
        new Series(new double[]{0, 1}, new double[]{1, Double.NaN});
    }
}
