package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LinearRegressionTest {

    @Test
    public void exactLine() {
        double[] x = {0, 1, 2, 3, 4};
        double[] y = {1, 3, 5, 7, 9};
        LinearRegression r = LinearRegression.fit(x, y);
        assertEquals(2.0, r.getSlope(), 1e-12);
        assertEquals(1.0, r.getIntercept(), 1e-12);
        assertEquals(1.0, r.getRSquared(), 1e-12);
        assertEquals(0.0, r.getSlopeStdError(), 1e-9);
        assertEquals(21.0, r.predict(10), 1e-9);
    }

    @Test
    public void noisyDataHasLowerRSquared() {
        double[] x = {0, 1, 2, 3, 4, 5};
        double[] y = {0.1, 0.9, 2.3, 2.8, 4.2, 4.9};
        LinearRegression r = LinearRegression.fit(x, y);
        assertEquals(1.0, r.getSlope(), 0.05);
        assertTrue(r.getRSquared() < 1.0 && r.getRSquared() > 0.97);
        assertTrue(r.getSlopeStdError() > 0);
    }

    @Test
    public void constantYGivesZeroSlope() {
        LinearRegression r = LinearRegression.fit(new double[]{0, 1, 2}, new double[]{4, 4, 4});
        assertEquals(0.0, r.getSlope(), 1e-12);
        assertEquals(1.0, r.getRSquared(), 0);
    }

    @Test
    public void twoPointsHaveNoStdError() {
        LinearRegression r = LinearRegression.fit(new double[]{0, 2}, new double[]{0, 4});
        assertEquals(2.0, r.getSlope(), 1e-12);
        assertTrue(Double.isNaN(r.getSlopeStdError()));
    }

    @Test
    public void fitSeriesUsesTimeAsX() {
        Series s = new Series(new double[]{0, 1, 2}, new double[]{5, 4, 3});
        assertEquals(-1.0, LinearRegression.fit(s).getSlope(), 1e-12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void identicalXRejected() {
        LinearRegression.fit(new double[]{1, 1, 1}, new double[]{1, 2, 3});
    }

    @Test(expected = IllegalArgumentException.class)
    public void singlePointRejected() {
        LinearRegression.fit(new double[]{1}, new double[]{1});
    }
}
