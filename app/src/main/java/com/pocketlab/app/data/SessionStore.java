package com.pocketlab.app.data;

import com.pocketlab.app.graph.Marker;

import java.util.List;

/**
 * Where recordings are written. The app uses {@link RoomSessionStore}; tests use an in-memory
 * fake, so the writing logic in {@link SessionRecorder} can be tested without a database.
 * All methods are called from a background thread, never the UI thread.
 */
public interface SessionStore {

    /** Creates an unfinished session and returns its id. */
    long insertSession(SessionHeader header) throws Exception;

    /** Appends {@code count} samples (arrays may be longer than count). */
    void insertSamples(long sessionId, double[] times, float[] v0, float[] v1, float[] v2, int count)
            throws Exception;

    /** Writes the markers and marks the session complete, atomically. */
    void finishSession(long sessionId, double durationSeconds, long sampleCount, String resultText,
                       List<Marker> markers) throws Exception;

    /** Removes the session and everything stored for it. */
    void deleteSession(long sessionId) throws Exception;
}
