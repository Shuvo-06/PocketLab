package com.pocketlab.app.cloud;

import com.pocketlab.app.analysis.RunningStats;
import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.MarkerEntity;
import com.pocketlab.app.data.SessionEntity;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.export.ExportSummary;
import com.pocketlab.app.export.RoomSampleSource;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SensorType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** The device's recordings, as seen by the sync engine. Call from a background thread. */
final class RoomLocalStore implements SyncEngine.LocalStore {

    private final AppDatabase db;

    RoomLocalStore(AppDatabase db) {
        this.db = db;
    }

    @Override
    public List<LocalSession> listSyncable() {
        List<LocalSession> out = new ArrayList<>();
        for (SessionEntity e : db.sessionDao().syncable()) {
            out.add(new LocalSession(e.id, e.cloudId, e.modifiedAt, e.syncedAt));
        }
        return out;
    }

    @Override
    public CloudSession snapshot(long id) {
        SessionEntity e = db.sessionDao().get(id);
        if (e == null || e.trashed || !e.complete) return null;
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
    public void markSynced(long localId, String cloudId, long syncedAt) {
        db.sessionDao().markSynced(localId, cloudId, syncedAt);
    }

    @Override
    public void applyRemote(long localId, CloudSession remote) {
        db.sessionDao().applyRemote(localId, remote.name, remote.modifiedAt);
    }

    @Override
    public void restore(String cloudId, CloudSession remote) {
        SessionEntity e = new SessionEntity();
        e.name = remote.name;
        e.createdAt = remote.createdAtMillis;
        e.sensorType = remote.sensor;
        e.samplingRate = remote.samplingRate;
        e.kind = SessionKind.RESTORED.name();
        e.experimentId = remote.experimentId;
        e.experimentTitle = remote.experimentTitle;
        e.durationSeconds = remote.durationSeconds;
        e.sampleCount = remote.sampleCount;
        e.resultText = CloudMapper.restoredResultText(remote);
        e.complete = true;
        e.cloudId = cloudId;
        e.modifiedAt = remote.modifiedAt;
        e.syncedAt = remote.modifiedAt;

        List<MarkerEntity> markers = new ArrayList<>();
        db.runInTransaction(() -> {
            long id = db.sessionDao().insert(e);
            for (Marker m : remote.markers) {
                MarkerEntity row = new MarkerEntity();
                row.sessionId = id;
                row.timeSeconds = m.getTimeSeconds();
                row.label = m.getLabel();
                markers.add(row);
            }
            if (!markers.isEmpty()) db.markerDao().insertAll(markers);
        });
    }
}
