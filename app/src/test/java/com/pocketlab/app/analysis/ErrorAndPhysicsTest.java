package com.pocketlab.app.analysis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ErrorAndPhysicsTest {

    @Test
    public void percentageError() {
        assertEquals(10.0, ErrorMetrics.percentageError(9.0, 10.0), 1e-12);
        assertEquals(10.0, ErrorMetrics.percentageError(11.0, 10.0), 1e-12);
        assertEquals(10.0, ErrorMetrics.percentageError(-11.0, -10.0), 1e-12);
        assertEquals(0.0, ErrorMetrics.percentageError(5, 5), 0);
        assertTrue(Double.isNaN(ErrorMetrics.percentageError(1, 0)));
        assertEquals(2.0, ErrorMetrics.absoluteError(3, 5), 0);
    }

    @Test
    public void pendulumGravity() {
        double t = PhysicsFormulas.pendulumPeriod(1.0, PhysicsFormulas.STANDARD_GRAVITY);
        assertEquals(2.0064, t, 1e-3);
        assertEquals(PhysicsFormulas.STANDARD_GRAVITY, PhysicsFormulas.gravityFromPendulum(1.0, t), 1e-9);
        assertEquals(9.87, PhysicsFormulas.gravityFromPendulum(1.0, 2.0), 0.01);
    }

    @Test(expected = IllegalArgumentException.class)
    public void pendulumRejectsZeroPeriod() {
        PhysicsFormulas.gravityFromPendulum(1.0, 0);
    }

    @Test
    public void altitudeFromPressure() {
        assertEquals(0.0, PhysicsFormulas.altitudeFromPressure(1013.25, 1013.25), 1e-9);
        assertEquals(1000.0, PhysicsFormulas.altitudeFromPressure(898.76, 1013.25), 2.0);
    }

    @Test
    public void heightChangeFromPressure() {
        // about 8.3 m per hPa near sea level; pressure drop => going up
        assertEquals(8.3, PhysicsFormulas.heightChangeFromPressure(1013.25, 1012.25), 0.1);
        assertTrue(PhysicsFormulas.heightChangeFromPressure(1000, 999) > 0);
        assertTrue(PhysicsFormulas.heightChangeFromPressure(999, 1000) < 0);
    }
}
