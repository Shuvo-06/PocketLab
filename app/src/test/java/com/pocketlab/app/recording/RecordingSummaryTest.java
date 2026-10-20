package com.pocketlab.app.recording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import org.junit.Test;

public class RecordingSummaryTest {

    private static final long SEC = 1_000_000_000L;

    private RecordingSession pendulum(int capacity, double seconds) {
        RecordingSession s = new RecordingSession(SensorType.ACCELEROMETER, SamplingRate.GAME, capacity);
        s.start();
        int n = (int) (seconds * 50);
        for (int i = 0; i < n; i++) {
            double t = i / 50.0;
            float x = (float) (1.5 * Math.sin(2 * Math.PI * t / 1.6));
            s.onSample(new SensorSample(SensorType.ACCELEROMETER, SEC + Math.round(t * SEC),
                    new float[]{x, 0.01f * (i % 3), 9.81f}, 3));
        }
        return s;
    }

    @Test
    public void statsAndPeriodOfOscillatingAxis() {
        RecordingSummary sum = RecordingSummary.of(pendulum(6000, 20));
        assertEquals(3, sum.getAxes().size());
        assertEquals(9.81, sum.getAxes().get(2).getStats().getMean(), 1e-4);
        assertEquals(1000, sum.getSamplesUsed());
        assertNotNull(sum.getPeriod());
        assertEquals(0, sum.getPeriodAxis());
        assertEquals(1.6, sum.getPeriod().getPeriodSeconds(), 0.01);
        assertTrue(!sum.isTruncated());
    }

    @Test
    public void truncatedRecordingStillSummarisesNewestSamples() {
        RecordingSummary sum = RecordingSummary.of(pendulum(300, 20));
        assertTrue(sum.isTruncated());
        assertEquals(300, sum.getSamplesUsed());
        assertNotNull(sum.getPeriod());
    }

    @Test
    public void emptyRecordingGivesEmptySummary() {
        RecordingSession s = new RecordingSession(SensorType.LIGHT, SamplingRate.UI, 100);
        s.start();
        RecordingSummary sum = RecordingSummary.of(s);
        assertEquals(0, sum.getAxes().size());
        assertNull(sum.getPeriod());
    }

    @Test
    public void steadySignalHasNoPeriod() {
        RecordingSession s = new RecordingSession(SensorType.LIGHT, SamplingRate.UI, 100);
        s.start();
        for (int i = 0; i < 50; i++) {
            s.onSample(new SensorSample(SensorType.LIGHT, SEC + i * (SEC / 10), new float[]{300f}, 3));
        }
        RecordingSummary sum = RecordingSummary.of(s);
        assertEquals(300.0, sum.getAxes().get(0).getStats().getMean(), 1e-9);
        assertNull(sum.getPeriod());
    }
}
