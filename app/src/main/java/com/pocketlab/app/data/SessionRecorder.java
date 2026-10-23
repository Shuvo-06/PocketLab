package com.pocketlab.app.data;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.recording.RecordingSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * Streams every sample of one recording into a {@link SessionStore} without blocking the UI.
 *
 * <p>Samples are collected in chunks of {@link #CHUNK} on the calling (main) thread and
 * handed to a <em>single-threaded</em> executor, so the session row is always created before
 * its samples and chunks are written in order. Each chunk is one database transaction.
 *
 * <p>Lifecycle: {@link #begin()}, then {@link #onSample} for every sample (register this
 * object with {@code RecordingSession.setSampleSink}), then exactly one of
 * {@link #finish} or {@link #discard()}. Errors never crash the app; see {@link #getError()}.
 */
public final class SessionRecorder implements RecordingSession.SampleSink {

    public static final int CHUNK = 500;

    private interface Task { void run() throws Exception; }

    private final SessionStore store;
    private final Executor io;
    private final SessionHeader header;

    // Main thread only
    private final double[] times = new double[CHUNK];
    private final float[] v0 = new float[CHUNK];
    private final float[] v1 = new float[CHUNK];
    private final float[] v2 = new float[CHUNK];
    private int n;
    private boolean closed;

    // Background thread only (read after the task that wrote it, on the same thread)
    private long sessionId = -1;
    private volatile Throwable error;

    public SessionRecorder(SessionStore store, Executor io, SessionHeader header) {
        this.store = store;
        this.io = io;
        this.header = header;
    }

    public void begin() {
        io.execute(() -> run(() -> sessionId = store.insertSession(header)));
    }

    @Override
    public void onSample(double timeSeconds, float[] values) {
        if (closed) return;
        times[n] = timeSeconds;
        v0[n] = values.length > 0 ? values[0] : 0f;
        v1[n] = values.length > 1 ? values[1] : 0f;
        v2[n] = values.length > 2 ? values[2] : 0f;
        n++;
        if (n == CHUNK) flush();
    }

    private void flush() {
        if (n == 0) return;
        final int count = n;
        final double[] t = Arrays.copyOf(times, count);
        final float[] a = Arrays.copyOf(v0, count);
        final float[] b = Arrays.copyOf(v1, count);
        final float[] c = Arrays.copyOf(v2, count);
        n = 0;
        io.execute(() -> run(() -> {
            if (sessionId >= 0) store.insertSamples(sessionId, t, a, b, c, count);
        }));
    }

    /** Writes the remaining samples, the markers and the result, and marks the session complete. */
    public void finish(RecordingSession session, String resultText) {
        if (closed) return;
        flush();
        closed = true;
        final double duration = session.getElapsedSeconds();
        final long total = session.getTotalSamples();
        final List<Marker> markers = new ArrayList<>(session.getMarkers());
        io.execute(() -> run(() -> {
            if (sessionId >= 0) store.finishSession(sessionId, duration, total, resultText, markers);
        }));
        shutdown();
    }

    /** Throws the recording away, including anything already written. */
    public void discard() {
        if (closed) return;
        closed = true;
        n = 0;
        io.execute(() -> run(() -> {
            if (sessionId >= 0) store.deleteSession(sessionId);
        }));
        shutdown();
    }

    /** First error that happened while saving, or null. May be set from a background thread. */
    public Throwable getError() {
        return error;
    }

    private void run(Task task) {
        try {
            task.run();
        } catch (Throwable e) {
            if (error == null) error = e;
        }
    }

    private void shutdown() {
        // Queued tasks still run; only new tasks are refused.
        if (io instanceof ExecutorService) ((ExecutorService) io).shutdown();
    }
}
