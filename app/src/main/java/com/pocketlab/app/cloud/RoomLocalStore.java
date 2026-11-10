package com.pocketlab.app.cloud;

import com.pocketlab.app.analysis.RunningStats;
import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.MarkerEntity;
import com.pocketlab.app.data.SampleEntity;
import com.pocketlab.app.data.SessionEntity;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.export.ExportSummary;
import com.pocketlab.app.export.RoomSampleSource;
import com.pocketlab.app.export.SampleSource;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SensorType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The signed-in account's recordings on this device, as seen by the sync engine. Background threads only. */
final class RoomLocalStore implements SyncEngine.LocalStore {

    private static final int INSERT_CHUNK = 1000;

    private final AppDatabase db;
    private final String owner;

    RoomLocalStore(AppDatabase db, String owner) {
        this.db = db;
        this.owner = owner;
    }

    @Override
    public List<LocalSession> listSyncable() {
        List<LocalSession> out = new ArrayList<>();
        for (SessionEntity e : db.sessionDao().syncable(owner)) {
            out.add(new LocalSession(e.id, e.cloudId, e.modifiedAt, e.syncedAt));
        }
        return out;
    }

    @Override
    public CloudSession snapshot(long id) {
        SessionEntity e = db.sessionDao().get(id);
        if (e == null || e.trashed || !e.complete || !e.ownerUid.equals(owner)) return null;
        SensorType type = SensorType.valueOf(e.sensorType);

        List<CloudSession.Stat> stats = new ArrayList<>();
        try {
            // Exact statistics over every stored sample, streamed from the database.
            RunningStats[] rs = ExportSummary.compute(type, new RoomSampleSource(db, id), e.sampleCount, 0).stats;
            for (int a = 0; a < rs.length; a++) {
                stats.add(new CloudSession.Stat(type.getAxes() > 1 ? type.getAxisLabel(a) : type.getDisplayName(),
                        rs[a].getMean(), rs[a].getStdDev(), rs[a].getMin(), rs[a].getMax()));
            }
        } catch (IOException ignored) {
            // Back up without statistics rather than not at all.
        }
        List<Marker> markers = new ArrayList<>();
        for (MarkerEntity m : db.markerDao().forSession(id)) markers.add(new Marker(m.timeSeconds, m.label));

        return new CloudSession(e.name, e.createdAt, e.sensorType, e.samplingRate, e.kind, e.experimentId,
                e.experimentTitle, e.durationSeconds, e.sampleCount, e.resultText, markers, stats, e.modifiedAt);
    }

    @Override
    public SampleSource openSamples(long localId) {
        return new RoomSampleSource(db, localId);
    }

    @Override
    public void markSynced(long localId, String cloudId, long syncedAt) {
        db.sessionDao().markSynced(localId, cloudId, syncedAt);
    }

    @Override
    public void applyRemote(long localId, CloudSession remote) {
        db.sessionDao().applyRemote(localId, remote.name, remote.modifiedAt);
    }

    @Override
    public void restore(String cloudId, CloudSession remote, RawSamples raw) {
        SessionEntity e = new SessionEntity();
        e.name = remote.name;
        e.createdAt = remote.createdAtMillis;
        e.sensorType = remote.sensor;
        e.samplingRate = remote.samplingRate;
        e.kind = SessionKind.RESTORED.name();
        e.experimentId = remote.experimentId;
        e.experimentTitle = remote.experimentTitle;
        e.durationSeconds = remote.durationSeconds;
        e.sampleCount = raw != null ? raw.count : remote.sampleCount;
        // With the raw data restored the detail screen computes its own statistics; without it, show the backed-up ones.
        e.resultText = CloudMapper.restoredResultText(remote, raw == null);
        e.complete = true;
        e.cloudId = cloudId;
        e.modifiedAt = remote.modifiedAt;
        e.syncedAt = remote.modifiedAt;
        e.ownerUid = owner;

        // Session, markers and samples are stored together or not at all.
        db.runInTransaction(() -> {
            long id = db.sessionDao().insert(e);
            List<MarkerEntity> markers = new ArrayList<>();
            for (Marker m : remote.markers) {
                MarkerEntity row = new MarkerEntity();
                row.sessionId = id;
                row.timeSeconds = m.getTimeSeconds();
                row.label = m.getLabel();
                markers.add(row);
            }
            if (!markers.isEmpty()) db.markerDao().insertAll(markers);
            if (raw != null) {
                for (int from = 0; from < raw.count; from += INSERT_CHUNK) {
                    int to = Math.min(raw.count, from + INSERT_CHUNK);
                    List<SampleEntity> rows = new ArrayList<>(to - from);
                    for (int i = from; i < to; i++) {
                        SampleEntity s = new SampleEntity();
                        s.sessionId = id;
                        s.timeSeconds = raw.times[i];
                        s.v0 = raw.values[0][i];
                        s.v1 = raw.values[1][i];
                        s.v2 = raw.values[2][i];
                        rows.add(s);
                    }
                    db.sampleDao().insertAll(rows);
                }
            }
        });
    }
}
