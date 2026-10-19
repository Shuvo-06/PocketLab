package com.pocketlab.app.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SamplingRateTest {

    @Test
    public void ratesGetFasterInDeclaredOrder() {
        SamplingRate[] r = SamplingRate.values();
        for (int i = 1; i < r.length; i++) {
            assertTrue(r[i].getPeriodUs() < r[i - 1].getPeriodUs());
        }
    }

    @Test
    public void nominalHz() {
        assertEquals(5.0, SamplingRate.NORMAL.getNominalHz(), 1e-9);
        assertEquals(50.0, SamplingRate.GAME.getNominalHz(), 1e-9);
        assertEquals(0.0, SamplingRate.FASTEST.getNominalHz(), 0);
    }
}
