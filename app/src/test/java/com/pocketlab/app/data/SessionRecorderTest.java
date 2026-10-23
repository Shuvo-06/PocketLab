package com.pocketlab.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SessionRecorderTest {

    private static final long SEC = 1_000_000_000L;

    /** In-memory stand-in for the database. */
    static class FakeStore implements SessionStore {
        final List<String> calls = new ArrayList<>();
        final List<Integer> chunkSizes = new ArrayList<>();
        final List<Double> allTimes = new ArrayList<>();
        final List<Float> allV1 = new ArrayList<>();
        long nextId = 42;
        boolean failInsertSession;
        String resultText;
        double duration;
        long sampleCount;
        List<Marker> markers;
        long deletedId = -1;

        @Override public long insertSession(SessionHeader h) throws Exception {
            if (failInsertSession) throw new Exception("disk full");
            calls.add("insertSession");
            return nextId;
        }
        @Override public void insertSamples(long id, double[] t, float[] a, float[] b, float[] c, int count) {
            assertEquals(nextId, id);
            calls.add("insertSamples");
            chunkSizes.add(count);
            for (int i = 0; i < count; i++) { allTimes.add(t[i]); allV1.add(b[i]); }
        }
        @Override public void finishSession(long id, double d, long n, String text, List<Marker> m) {
            calls.add("finishSession"); duration = d; sampleCount = n; resultText = text; markers = m;
        }
        @Override public void deleteSession(long id) { calls.add("deleteSession"); deletedId = id; }
    }

    private static SessionHeader header() {
        return new SessionHeader("n", 0, SensorType.ACCELEROMETER, SamplingRate.GAME, SessionKind.FREE, null, null);
    }

    private static RecordingSession recordingFeeding(SessionRecorder rec, int samples) {
        RecordingSession s = new RecordingSession(SensorType.ACCELEROMETER, SamplingRate.GAME, 100);
        s.setSampleSink(rec);
        s.start();
        for (int i = 0; i < samples; i++) {
            s.onSample(new SensorSample(SensorType.ACCELEROMETER, SEC + i * (SEC / 100), new float[]{i, i * 2f, 9.8f}, 3));
        }
        return s;
    }

    @Test
    public void everySampleIsStoredInOrderEvenBeyondTheGraphBuffer() {
        FakeStore store = new FakeStore();
        SessionRecorder rec = new SessionRecorder(store, Runnable::run, header());
        rec.begin();
        RecordingSession s = recordingFeeding(rec, 1200);   // graph buffer holds only 100
        s.addMarker();
        rec.finish(s, "result");

        assertEquals("insertSession", store.calls.get(0));
        assertEquals("finishSession", store.calls.get(store.calls.size() - 1));
        assertEquals(java.util.Arrays.asList(500, 500, 200), store.chunkSizes);
        assertEquals(1200, store.allTimes.size());
        for (int i = 1; i < store.allTimes.size(); i++) assertTrue(store.allTimes.get(i) >= store.allTimes.get(i - 1));
        assertEquals(2f * 1199, store.allV1.get(1199), 0);
        assertEquals(1200, store.sampleCount);
        assertEquals(11.99, store.duration, 1e-9);
        assertEquals("result", store.resultText);
        assertEquals(1, store.markers.size());
        assertNull(rec.getError());
    }

    @Test
    public void singleAxisSamplesPadWithZero() {
        FakeStore store = new FakeStore();
        SessionRecorder rec = new SessionRecorder(store, Runnable::run, header());
        rec.begin();
        RecordingSession s = new RecordingSession(SensorType.LIGHT, SamplingRate.UI, 10);
        s.setSampleSink(rec);
        s.start();
        s.onSample(new SensorSample(SensorType.LIGHT, SEC, new float[]{123f}, 3));
        rec.finish(s, null);
        assertEquals(1, store.allV1.size());
        assertEquals(0f, store.allV1.get(0), 0);
        assertNull(store.resultText);
    }

    @Test
    public void discardDeletesAndIgnoresFurtherSamples() {
        FakeStore store = new FakeStore();
        SessionRecorder rec = new SessionRecorder(store, Runnable::run, header());
        rec.begin();
        RecordingSession s = recordingFeeding(rec, 700);
        rec.discard();
        rec.onSample(1.0, new float[]{1, 2, 3});
        rec.finish(s, "late");   // ignored: already closed
        assertEquals(42, store.deletedId);
        assertTrue(!store.calls.contains("finishSession"));
        assertEquals(1, store.chunkSizes.size());   // only the full chunk written before discard
    }

    @Test
    public void storageFailureDoesNotCrashAndIsReported() {
        FakeStore store = new FakeStore();
        store.failInsertSession = true;
        SessionRecorder rec = new SessionRecorder(store, Runnable::run, header());
        rec.begin();
        RecordingSession s = recordingFeeding(rec, 600);
        rec.finish(s, "x");
        assertNotNull(rec.getError());
        assertEquals("disk full", rec.getError().getMessage());
        assertEquals(0, store.chunkSizes.size());
        assertTrue(store.calls.isEmpty());
    }

    @Test
    public void realSingleThreadExecutorFinishesQueuedWorkThenShutsDown() throws Exception {
        FakeStore store = new FakeStore();
        ExecutorService io = Executors.newSingleThreadExecutor();
        SessionRecorder rec = new SessionRecorder(store, io, header());
        rec.begin();
        RecordingSession s = recordingFeeding(rec, 1100);
        rec.finish(s, "done");
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS));
        synchronized (store) {
            assertEquals("insertSession", store.calls.get(0));
            assertEquals("finishSession", store.calls.get(store.calls.size() - 1));
            assertEquals(1100, store.allTimes.size());
        }
    }
}
