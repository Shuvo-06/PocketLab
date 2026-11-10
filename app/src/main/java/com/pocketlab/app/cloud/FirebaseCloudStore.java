package com.pocketlab.app.cloud;

import com.pocketlab.app.export.SampleSource;

import java.util.Map;

/**
 * The signed-in user's cloud data, split over two Firebase services: summaries in Cloud Firestore
 * ({@link FirestoreCloudStore}) and raw samples in the Realtime Database ({@link RtdbSampleStore}).
 * If the project has no Realtime Database, {@link #supportsSamples()} is false and only summaries sync.
 */
final class FirebaseCloudStore implements SyncEngine.CloudStore {

    private final FirestoreCloudStore summaries;
    private final RtdbSampleStore samples;   // null when the Realtime Database is not set up

    FirebaseCloudStore(String uid) {
        summaries = new FirestoreCloudStore(uid);
        samples = RtdbSampleStore.available() ? new RtdbSampleStore(uid) : null;
    }

    @Override
    public Map<String, CloudSession> fetchAll() throws SyncException {
        return summaries.fetchAll();
    }

    @Override
    public void put(String cloudId, CloudSession session) throws SyncException {
        summaries.put(cloudId, session);
    }

    @Override
    public boolean supportsSamples() {
        return samples != null;
    }

    @Override
    public int putSamples(String cloudId, SampleSource source) throws SyncException {
        return samples.putSamples(cloudId, source);
    }

    @Override
    public RawSamples fetchSamples(String cloudId, int chunks) throws SyncException {
        return samples.fetchSamples(cloudId, chunks);
    }

    @Override
    public void deleteSamples(String cloudId) {
        if (samples != null) samples.deleteSamples(cloudId);
    }
}
