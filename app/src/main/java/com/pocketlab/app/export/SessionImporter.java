package com.pocketlab.app.export;

import com.pocketlab.app.data.SessionHeader;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.data.SessionStore;

import java.util.Arrays;

/** Stores an {@link ImportedSession} through a {@link SessionStore}; undoes everything if it fails. */
public final class SessionImporter {

    static final int CHUNK = 500;

    private SessionImporter() {}

    /** @return the id of the new session. Call from a background thread. */
    public static long save(ImportedSession s, SessionStore store) throws Exception {
        long id = store.insertSession(new SessionHeader(s.name, s.createdAtMillis, s.sensor, s.rate,
                SessionKind.IMPORTED, s.experimentId, s.experimentTitle));
        try {
            for (int from = 0; from < s.count; from += CHUNK) {
                int to = Math.min(s.count, from + CHUNK);
                store.insertSamples(id, Arrays.copyOfRange(s.times, from, to),
                        Arrays.copyOfRange(s.values[0], from, to),
                        Arrays.copyOfRange(s.values[1], from, to),
                        Arrays.copyOfRange(s.values[2], from, to), to - from);
            }
            store.finishSession(id, s.durationSeconds, s.count, s.resultText, s.markers);
            return id;
        } catch (Exception e) {
            try {
                store.deleteSession(id);
            } catch (Exception ignored) {
                // The unfinished row is purged on the next app start anyway.
            }
            throw e;
        }
    }
}
