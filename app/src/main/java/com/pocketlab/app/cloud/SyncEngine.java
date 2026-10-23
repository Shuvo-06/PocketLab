package com.pocketlab.app.cloud;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a sync between the local database and the cloud. Both sides are interfaces so the whole
 * algorithm, including failure handling, is tested with fakes. Progress is saved item by item: if a
 * failure stops the run, what was already exchanged stays exchanged and the next run continues.
 */
public final class SyncEngine {

    /** The device's recordings. */
    public interface LocalStore {
        /** Finished, not deleted recordings. */
        List<LocalSession> listSyncable();
        /** The backup form of a recording, or null if it no longer exists. */
        CloudSession snapshot(long localId);
        void markSynced(long localId, String cloudId, long syncedAt);
        /** Takes the cloud's newer name/result; local modified and synced times become {@code remote.modifiedAt}. */
        void applyRemote(long localId, CloudSession remote);
        /** Creates a recording (without raw samples) from a cloud backup. */
        void restore(String cloudId, CloudSession remote);
    }

    /** The cloud collection for the signed-in user. */
    public interface CloudStore {
        Map<String, CloudSession> fetchAll() throws SyncException;
        void put(String cloudId, CloudSession session) throws SyncException;
    }

    private SyncEngine() {}

    public static SyncResult run(LocalStore local, CloudStore cloud, SyncMode mode) throws SyncException {
        Map<String, CloudSession> remote = cloud.fetchAll();
        SyncPlanner.Plan plan = SyncPlanner.plan(local.listSyncable(), remote, mode);

        SyncResult result = new SyncResult();
        result.skipped = plan.skipped;
        for (SyncPlanner.Action a : plan.actions) {
            switch (a.type) {
                case UPLOAD: {
                    CloudSession snap = local.snapshot(a.localId);
                    if (snap == null) continue;                    // deleted meanwhile
                    String id = a.cloudId != null ? a.cloudId : UUID.randomUUID().toString();
                    cloud.put(id, snap);
                    local.markSynced(a.localId, id, snap.modifiedAt);
                    result.uploaded++;
                    break;
                }
                case APPLY_REMOTE:
                    local.applyRemote(a.localId, remote.get(a.cloudId));
                    result.appliedFromCloud++;
                    break;
                default:   // RESTORE
                    local.restore(a.cloudId, remote.get(a.cloudId));
                    result.restored++;
                    break;
            }
            if (a.conflict) result.conflicts++;
        }
        return result;
    }
}
