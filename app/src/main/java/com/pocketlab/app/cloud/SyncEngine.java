package com.pocketlab.app.cloud;

import com.pocketlab.app.export.SampleSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a sync between the local database and the cloud. Both sides are interfaces so the whole
 * algorithm, including failure handling, is tested with fakes. Progress is saved item by item: if a
 * failure stops the run, what was already exchanged stays exchanged and the next run continues.
 *
 * <p>Two cloud services take part: Cloud Firestore holds the summary of each recording, and the
 * Realtime Database holds the raw samples (uploaded once, because samples never change).
 */
public final class SyncEngine {

    /** Recordings larger than this are restored as a summary only, to protect memory. */
    public static final int MAX_RESTORE_SAMPLES = 500_000;

    /** The device's recordings. */
    public interface LocalStore {
        /** Finished, not deleted recordings of the signed-in account. */
        List<LocalSession> listSyncable();
        /** The backup form of a recording, or null if it no longer exists. */
        CloudSession snapshot(long localId);
        /** The raw samples of a recording, to upload. */
        SampleSource openSamples(long localId);
        void markSynced(long localId, String cloudId, long syncedAt);
        /** Takes the cloud's newer name/result; local modified and synced times become {@code remote.modifiedAt}. */
        void applyRemote(long localId, CloudSession remote);
        /**
         * Creates a recording from a cloud backup. {@code samples} is null when only the summary is
         * available; otherwise the session and its samples are stored together or not at all.
         */
        void restore(String cloudId, CloudSession remote, RawSamples samples);
    }

    /** The signed-in user's cloud data. */
    public interface CloudStore {
        Map<String, CloudSession> fetchAll() throws SyncException;
        void put(String cloudId, CloudSession session) throws SyncException;

        /** False when the Realtime Database is not available (then only summaries are synced). */
        boolean supportsSamples();
        /** @return the number of pieces written */
        int putSamples(String cloudId, SampleSource source) throws SyncException;
        RawSamples fetchSamples(String cloudId, int chunks) throws SyncException;
        /** Best effort clean-up of samples whose upload did not complete; never throws. */
        void deleteSamples(String cloudId);
    }

    private SyncEngine() {}

    public static SyncResult run(LocalStore local, CloudStore cloud, SyncMode mode) throws SyncException {
        Map<String, CloudSession> remote = cloud.fetchAll();
        SyncPlanner.Plan plan = SyncPlanner.plan(local.listSyncable(), remote, mode);

        SyncResult result = new SyncResult();
        result.skipped = plan.skipped;
        for (SyncPlanner.Action a : plan.actions) {
            switch (a.type) {
                case UPLOAD:
                    if (!upload(local, cloud, remote, a)) continue;
                    result.uploaded++;
                    break;
                case APPLY_REMOTE:
                    local.applyRemote(a.localId, remote.get(a.cloudId));
                    result.appliedFromCloud++;
                    break;
                default:   // RESTORE
                    restore(local, cloud, remote.get(a.cloudId), a.cloudId);
                    result.restored++;
                    break;
            }
            if (a.conflict) result.conflicts++;
        }
        return result;
    }

    /** @return false if the recording disappeared meanwhile */
    private static boolean upload(LocalStore local, CloudStore cloud, Map<String, CloudSession> remote,
                                  SyncPlanner.Action a) throws SyncException {
        CloudSession snap = local.snapshot(a.localId);
        if (snap == null) return false;
        String id = a.cloudId != null ? a.cloudId : UUID.randomUUID().toString();

        int chunks;
        if (a.fullUpload) {
            chunks = 0;
            if (snap.sampleCount > 0 && cloud.supportsSamples()) {
                try {
                    chunks = cloud.putSamples(id, local.openSamples(a.localId));
                } catch (SyncException e) {
                    cloud.deleteSamples(id);
                    throw e;
                }
            }
        } else {
            CloudSession existing = a.cloudId == null ? null : remote.get(a.cloudId);
            chunks = existing != null ? existing.sampleChunks : 0;
        }

        try {
            cloud.put(id, snap.withSampleChunks(chunks));
        } catch (SyncException e) {
            if (a.fullUpload && chunks > 0) cloud.deleteSamples(id);   // do not leave unreferenced data behind
            throw e;
        }
        local.markSynced(a.localId, id, snap.modifiedAt);
        return true;
    }

    private static void restore(LocalStore local, CloudStore cloud, CloudSession r, String cloudId)
            throws SyncException {
        RawSamples raw = null;
        if (r.sampleChunks > 0 && r.sampleCount > 0 && r.sampleCount <= MAX_RESTORE_SAMPLES && cloud.supportsSamples()) {
            raw = cloud.fetchSamples(cloudId, r.sampleChunks);   // fails before anything is stored locally
        }
        local.restore(cloudId, r, raw);
    }
}
