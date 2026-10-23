package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;

public class RunningStatsTest {

    @Test
    public void matchesBatchStatistics() {
        Random r = new Random(3);
        double[] v = new double[5000];
        for (int i = 0; i < v.length; i++) v[i] = 1e6 + r.nextGaussian() * 2.5;
        RunningStats s = new RunningStats();
        for (double x : v) s.add(x);
        assertEquals(5000, s.getCount());
        assertEquals(Stats.mean(v), s.getMean(), 1e-6);
        assertEquals(Stats.stdDev(v), s.getStdDev(), 1e-6);
        assertEquals(Stats.min(v), s.getMin(), 0);
        assertEquals(Stats.max(v), s.getMax(), 0);
    }

    @Test
    public void emptyAndSingleValueAreNaN() {
        RunningStats s = new RunningStats();
        assertTrue(Double.isNaN(s.getMean()));
        assertTrue(Double.isNaN(s.getMin()));
        s.add(4);
        assertEquals(4, s.getMean(), 0);
        assertTrue(Double.isNaN(s.getStdDev()));
        assertEquals(4, s.getMax(), 0);
    }
}
