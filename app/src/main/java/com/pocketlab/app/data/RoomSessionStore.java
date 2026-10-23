package com.pocketlab.app.data;

import com.pocketlab.app.graph.Marker;

import java.util.ArrayList;
import java.util.List;

/** {@link SessionStore} backed by Room. Call from a background thread only. */
public final class RoomSessionStore implements SessionStore {

    private final AppDatabase db;

    public RoomSessionStore(AppDatabase db) {
        this.db = db;
    }

    @Override
    public long insertSession(SessionHeader h) {
        SessionEntity e = new SessionEntity();
        e.name = h.name;
        e.createdAt = h.createdAtMillis;
        e.sensorType = h.sensor.name();
        e.samplingRate = h.rate.name();
        e.kind = h.kind.name();
        e.experimentId = h.experimentId;
        e.experimentTitle = h.experimentTitle;
        e.complete = false;
        return db.sessionDao().insert(e);
    }

    @Override
    public void insertSamples(long sessionId, double[] times, float[] v0, float[] v1, float[] v2, int count) {
        List<SampleEntity> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            SampleEntity s = new SampleEntity();
            s.sessionId = sessionId;
            s.timeSeconds = times[i];
            s.v0 = v0[i];
            s.v1 = v1[i];
            s.v2 = v2[i];
            list.add(s);
        }
        db.sampleDao().insertAll(list);
    }

    @Override
    public void finishSession(long sessionId, double durationSeconds, long sampleCount,
                              String resultText, List<Marker> markers) {
        List<MarkerEntity> rows = new ArrayList<>();
        for (Marker m : markers) {
            MarkerEntity e = new MarkerEntity();
            e.sessionId = sessionId;
            e.timeSeconds = m.getTimeSeconds();
            e.label = m.getLabel();
            rows.add(e);
        }
        db.runInTransaction(() -> {
            if (!rows.isEmpty()) db.markerDao().insertAll(rows);
            db.sessionDao().finish(sessionId, durationSeconds, sampleCount, resultText);
        });
    }

    @Override
    public void deleteSession(long sessionId) {
        db.sessionDao().delete(sessionId);
    }
}
