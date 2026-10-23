package com.pocketlab.app.recording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.graph.SeriesBuffer;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecordingSessionSinkTest {

    private static final long SEC = 1_000_000_000L;

    @Test
    public void sinkGetsEveryAcceptedSampleAndNothingElse() {
        RecordingSession s = new RecordingSession(SensorType.ACCELEROMETER, SamplingRate.GAME, 5);
        List<Double> times = new ArrayList<>();
        s.setSampleSink((t, v) -> times.add(t));
        s.onSample(new SensorSample(SensorType.ACCELEROMETER, SEC, new float[]{1, 2, 3}, 3)); // idle: ignored
        s.start();
        for (int i = 0; i < 20; i++) {
            s.onSample(new SensorSample(SensorType.ACCELEROMETER, SEC + i * (SEC / 10), new float[]{i, 0, 0}, 3));
        }
        s.pause();
        s.onSample(new SensorSample(SensorType.ACCELEROMETER, 9 * SEC, new float[]{0, 0, 0}, 3)); // paused: ignored
        assertEquals(20, times.size());                 // more than the graph buffer (5)
        assertEquals(1.9, times.get(19), 1e-9);
        assertEquals(5, s.getSeries().get(0).size());
    }

    @Test
    public void summaryCanBeComputedFromRebuiltBuffers() {
        List<SeriesBuffer> buffers = new ArrayList<>();
        for (int k = 0; k < 3; k++) buffers.add(new SeriesBuffer(2000));
        for (int i = 0; i < 1000; i++) {
            double t = i / 50.0;
            buffers.get(0).add(t, (float) (1.5 * Math.sin(2 * Math.PI * t / 1.6)));
            buffers.get(1).add(t, 0f);
            buffers.get(2).add(t, 9.81f);
        }
        RecordingSummary sum = RecordingSummary.of(buffers, false);
        assertEquals(3, sum.getAxes().size());
        assertEquals(1000, sum.getSamplesUsed());
        assertEquals(1.6, sum.getPeriod().getPeriodSeconds(), 0.01);
        assertTrue(!sum.isTruncated());
        assertEquals(0, RecordingSummary.of(Arrays.asList(new SeriesBuffer(5)), false).getAxes().size());
    }
}
