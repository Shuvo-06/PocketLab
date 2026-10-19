package com.pocketlab.app.recording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import org.junit.Test;

public class RecordingSessionTest {

    private static final long SEC = 1_000_000_000L;

    private static SensorSample accel(long ts, float x) {
        return new SensorSample(SensorType.ACCELEROMETER, ts, new float[]{x, 0f, 9.8f}, 3);
    }

    private RecordingSession newSession(int capacity) {
        return new RecordingSession(SensorType.ACCELEROMETER, SamplingRate.GAME, capacity);
    }

    @Test
    public void ignoresSamplesBeforeStart() {
        RecordingSession s = newSession(10);
        s.onSample(accel(SEC, 1f));
        assertEquals(0, s.getTotalSamples());
    }

    @Test
    public void recordsSamplesWithRelativeTime() {
        RecordingSession s = newSession(10);
        s.start();
        s.onSample(accel(5 * SEC, 1f));
        s.onSample(accel(5 * SEC + SEC / 2, 2f));
        assertEquals(2, s.getTotalSamples());
        assertEquals(0.5, s.getElapsedSeconds(), 1e-9);
        assertEquals(3, s.getSeries().size());
        assertEquals(0.0, s.getSeries().get(0).getTime(0), 1e-9);
        assertEquals(2f, s.getSeries().get(0).getValue(1), 0);
        assertEquals(9.8f, s.getLatestValues()[2], 0);
    }

    @Test
    public void ignoresOtherSensorTypes() {
        RecordingSession s = newSession(10);
        s.start();
        s.onSample(new SensorSample(SensorType.LIGHT, SEC, new float[]{100f}, 3));
        assertEquals(0, s.getTotalSamples());
    }

    @Test
    public void pausedSamplesAreDroppedAndGapIsClosedOnResume() {
        RecordingSession s = newSession(10);
        s.start();
        s.onSample(accel(1 * SEC, 0f));                // t = 0.0
        s.onSample(accel(1 * SEC + SEC / 10, 0f));     // t = 0.1
        s.pause();
        s.onSample(accel(3 * SEC, 0f));                // dropped
        assertEquals(2, s.getTotalSamples());
        s.resume();
        s.onSample(accel(10 * SEC, 0f));               // gap closed: t stays 0.1
        s.onSample(accel(10 * SEC + SEC / 10, 0f));    // t = 0.2
        assertEquals(4, s.getTotalSamples());
        assertEquals(0.2, s.getElapsedSeconds(), 1e-9);
    }

    @Test
    public void markersUseCurrentTimeAndOnlyWhileRecording() {
        RecordingSession s = newSession(10);
        assertNull(s.addMarker());                     // idle
        s.start();
        assertNull(s.addMarker());                     // no samples yet
        s.onSample(accel(SEC, 0f));
        s.onSample(accel(3 * SEC, 0f));
        assertNotNull(s.addMarker());
        assertEquals(1, s.getMarkers().size());
        assertEquals(2.0, s.getMarkers().get(0).getTimeSeconds(), 1e-9);
        assertEquals("M1", s.getMarkers().get(0).getLabel());
        s.pause();
        assertNull(s.addMarker());
    }

    @Test
    public void graphBufferIsBoundedButTotalKeepsCounting() {
        RecordingSession s = newSession(5);
        s.start();
        for (int i = 0; i < 20; i++) s.onSample(accel(SEC + i * (SEC / 100), (float) i));
        assertEquals(20, s.getTotalSamples());
        assertEquals(5, s.getSeries().get(0).size());
        assertTrue(s.isGraphTruncated());
        assertEquals(19f, s.getSeries().get(0).getValue(4), 0);
    }

    @Test
    public void stopEndsRecording() {
        RecordingSession s = newSession(10);
        s.start();
        s.onSample(accel(SEC, 0f));
        s.stop();
        assertEquals(RecordingState.STOPPED, s.getState());
        s.onSample(accel(2 * SEC, 0f));
        assertEquals(1, s.getTotalSamples());
        assertFalse(s.isGraphTruncated());
    }

    @Test(expected = IllegalStateException.class)
    public void startTwiceThrows() {
        RecordingSession s = newSession(10);
        s.start();
        s.start();
    }

    @Test(expected = IllegalStateException.class)
    public void resumeWithoutPauseThrows() {
        RecordingSession s = newSession(10);
        s.start();
        s.resume();
    }

    @Test(expected = IllegalStateException.class)
    public void stopFromIdleThrows() {
        newSession(10).stop();
    }
}
