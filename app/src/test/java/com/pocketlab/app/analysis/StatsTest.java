package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatsTest {

    private static final double[] DATA = {2, 4, 4, 4, 5, 5, 7, 9};

    @Test
    public void meanMinMaxMedianRms() {
        assertEquals(2.5, Stats.mean(new double[]{1, 2, 3, 4}), 1e-12);
        assertEquals(2.0, Stats.min(DATA), 0);
        assertEquals(9.0, Stats.max(DATA), 0);
        assertEquals(4.5, Stats.median(DATA), 1e-12);
        assertEquals(3.0, Stats.median(new double[]{5, 1, 3}), 1e-12);
        assertEquals(Math.sqrt(12.5), Stats.rms(new double[]{3, 4}), 1e-12);
        assertEquals(5.0, Stats.rms(new double[]{5, -5, 5, -5}), 1e-12);
    }

    @Test
    public void standardDeviations() {
        assertEquals(2.0, Stats.populationStdDev(DATA), 1e-12);
        assertEquals(Math.sqrt(32.0 / 7.0), Stats.stdDev(DATA), 1e-12);
        assertEquals(32.0 / 7.0, Stats.variance(DATA), 1e-12);
    }

    @Test
    public void singleValueStdDevIsNaN() {
        assertTrue(Double.isNaN(Stats.stdDev(new double[]{3})));
        assertEquals(0.0, Stats.populationStdDev(new double[]{3}), 0);
    }

    @Test
    public void largeOffsetStaysAccurate() {
        double[] v = {1e9 + 1, 1e9 + 2, 1e9 + 3};
        assertEquals(1.0, Stats.stdDev(v), 1e-6);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyMeanThrows() {
        Stats.mean(new double[0]);
    }

    @Test
    public void summaryStats() {
        SummaryStats s = SummaryStats.of(DATA);
        assertEquals(8, s.getCount());
        assertEquals(5.0, s.getMean(), 1e-12);
        assertEquals(2.0, s.getMin(), 0);
        assertEquals(9.0, s.getMax(), 0);
    }
}
