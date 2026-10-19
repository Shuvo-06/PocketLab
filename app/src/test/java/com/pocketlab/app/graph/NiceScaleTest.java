package com.pocketlab.app.graph;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NiceScaleTest {

    @Test
    public void niceStepValues() {
        assertEquals(2.0, NiceScale.niceStep(10, 5), 1e-12);
        assertEquals(0.2, NiceScale.niceStep(1, 5), 1e-12);
        assertEquals(20.0, NiceScale.niceStep(100, 6), 1e-12);
    }

    @Test
    public void invalidRangeFallsBackToOne() {
        assertEquals(1.0, NiceScale.niceStep(0, 5), 0);
        assertEquals(1.0, NiceScale.niceStep(-3, 5), 0);
    }

    @Test
    public void ticksAreMultiplesInsideRange() {
        double[] t = NiceScale.ticks(0, 10, 2);
        assertEquals(6, t.length);
        assertEquals(0, t[0], 1e-12);
        assertEquals(10, t[5], 1e-12);

        double[] u = NiceScale.ticks(-1, 1, 0.5);
        assertEquals(5, u.length);
        assertEquals(-1, u[0], 1e-12);
    }

    @Test
    public void ticksNotStartingAtZero() {
        double[] t = NiceScale.ticks(0.3, 4.1, 1);
        assertEquals(4, t.length);
        assertEquals(1, t[0], 1e-12);
        assertEquals(4, t[3], 1e-12);
    }

    @Test
    public void decimalsForStep() {
        assertEquals(0, NiceScale.decimalsForStep(20));
        assertEquals(0, NiceScale.decimalsForStep(1));
        assertEquals(1, NiceScale.decimalsForStep(0.5));
        assertEquals(1, NiceScale.decimalsForStep(0.1));
        assertEquals(2, NiceScale.decimalsForStep(0.05));
    }
}
