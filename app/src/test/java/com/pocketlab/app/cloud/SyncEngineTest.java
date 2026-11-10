package com.pocketlab.app.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.export.SampleSource;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SyncEngineTest {

    // ---------------- fakes ----------------

    static CloudSession cs(String name, long modifiedAt) {
        return new CloudSession(name, 1, "LIGHT", "UI", "FREE", null, null, 1, 1, null,
                Collections.emptyList(), Collections.emptyList(), modifiedAt);
    }

    static CloudSession csSamples(String name, long modifiedAt, long samples, int chunks) {
        return new CloudSession(name, 1, "LIGHT", "UI", "FREE", null, null, 1, samples, null,
                Collections.emptyList(), Collections.emptyList(), modifiedAt, chunks);
    }

    static class FakeLocal implements SyncEngine.LocalStore {
        final Map<Long, LocalSession> rows = new LinkedHashMap<>();
        final Map<Long, String> names = new LinkedHashMap<>();
        final Map<Long, Integer> sampleCounts = new LinkedHashMap<>();
        final List<String> restored = new ArrayList<>();
        final Map<String, RawSamples> restoredSamples = new LinkedHashMap<>();
        final List<String> log = new ArrayList<>();
        long nextId = 100;

        void add(long id, String cloudId, long modified, long synced, String name) {
            add(id, cloudId, modified, synced, name, 0);
        }

        void add(long id, String cloudId, long modified, long synced, String name, int samples) {
            rows.put(id, new LocalSession(id, cloudId, modified, synced));
            names.put(id, name);
            sampleCounts.put(id, samples);
        }

        @Override public List<LocalSession> listSyncable() { return new ArrayList<>(rows.values()); }
        @Override public CloudSession snapshot(long id) {
            return rows.containsKey(id) ? csSamples(names.get(id), rows.get(id).modifiedAt, sampleCounts.get(id), 0) : null;
        }
        @Override public SampleSource openSamples(long id) {
            final int n = sampleCounts.get(id);
            return () -> new SampleSource.Cursor() {
                int pos;
                @Override public int read(double[] t, float[][] v, int max) {
                    int k = Math.min(max, n - pos);
                    for (int i = 0; i < k; i++) { t[i] = pos + i; v[0][i] = pos + i; }
                    pos += k;
                    return k;
                }
            };
        }
        @Override public void markSynced(long id, String cloudId, long syncedAt) {
            LocalSession o = rows.get(id);
            rows.put(id, new LocalSession(id, cloudId, o.modifiedAt, syncedAt));
            log.add("synced " + id);
        }
        @Override public void applyRemote(long id, CloudSession r) {
            names.put(id, r.name);
            rows.put(id, new LocalSession(id, rows.get(id).cloudId, r.modifiedAt, r.modifiedAt));
            log.add("applied " + id);
        }
        @Override public void restore(String cloudId, CloudSession r, RawSamples samples) {
            long id = nextId++;
            rows.put(id, new LocalSession(id, cloudId, r.modifiedAt, r.modifiedAt));
            names.put(id, r.name);
            sampleCounts.put(id, samples == null ? 0 : samples.count);
            restored.add(cloudId);
            if (samples != null) restoredSamples.put(cloudId, samples);
        }
    }

    static class FakeCloud implements SyncEngine.CloudStore {
        final Map<String, CloudSession> docs = new LinkedHashMap<>();
        final Map<String, Integer> sampleData = new LinkedHashMap<>();   // cloudId -> sample count stored
        final List<String> deleted = new ArrayList<>();
        SyncException failFetch;
        int failPutAfter = Integer.MAX_VALUE;   // fail once this many summaries were written
        boolean failSamples;
        boolean samplesSupported = true;
        int puts;
        int fetchSamplesCalls;

        @Override public Map<String, CloudSession> fetchAll() throws SyncException {
            if (failFetch != null) throw failFetch;
            return new LinkedHashMap<>(docs);
        }
        @Override public void put(String id, CloudSession s) throws SyncException {
            if (puts >= failPutAfter) throw new SyncException(SyncException.Category.NETWORK, "offline");
            puts++;
            docs.put(id, s);
        }
        @Override public boolean supportsSamples() { return samplesSupported; }
        @Override public int putSamples(String id, SampleSource source) throws SyncException {
            if (failSamples) throw new SyncException(SyncException.Category.NETWORK, "samples offline");
            try {
                SampleSource.Cursor c = source.open();
                double[] t = new double[500];
                float[][] v = new float[3][500];
                int total = 0, n;
                while ((n = c.read(t, v, 500)) > 0) total += n;
                sampleData.put(id, total);
                return (total + 499) / 500;
            } catch (java.io.IOException e) {
                throw new SyncException(SyncException.Category.OTHER, "io");
            }
        }
        @Override public RawSamples fetchSamples(String id, int chunks) throws SyncException {
            fetchSamplesCalls++;
            if (failSamples) throw new SyncException(SyncException.Category.NETWORK, "samples offline");
            int n = sampleData.containsKey(id) ? sampleData.get(id) : chunks * 500;
            return new RawSamples(n, new double[n], new float[3][n]);
        }
        @Override public void deleteSamples(String id) { deleted.add(id); sampleData.remove(id); }
    }

    // ---------------- planner rules ----------------

    private static SyncPlanner.Plan plan(LocalSession l, CloudSession r, SyncMode mode) {
        Map<String, CloudSession> remote = new LinkedHashMap<>();
        if (r != null) remote.put("c1", r);
        return SyncPlanner.plan(Collections.singletonList(l), remote, mode);
    }

    @Test
    public void unchangedEverywhereDoesNothing() {
        assertEquals(0, plan(new LocalSession(1, "c1", 50, 50), cs("a", 50), SyncMode.SYNC).actions.size());
    }

    @Test
    public void neverBackedUpIsUploadedWithNewIdAndSamples() {
        SyncPlanner.Plan p = plan(new LocalSession(1, null, 50, 0), null, SyncMode.SYNC);
        assertEquals(SyncPlanner.Type.UPLOAD, p.actions.get(0).type);
        assertNull(p.actions.get(0).cloudId);
        assertTrue(p.actions.get(0).fullUpload);
    }

    @Test
    public void missingCloudCopyIsRecreatedUnderTheSameIdWithSamples() {
        SyncPlanner.Action a = plan(new LocalSession(1, "c1", 50, 50), null, SyncMode.SYNC).actions.get(0);
        assertEquals("c1", a.cloudId);
        assertEquals(SyncPlanner.Type.UPLOAD, a.type);
        assertTrue(a.fullUpload);
    }

    @Test
    public void localChangeUploadsSummaryOnlyAndRemoteChangeIsApplied() {
        SyncPlanner.Action up = plan(new LocalSession(1, "c1", 60, 50), cs("a", 50), SyncMode.SYNC).actions.get(0);
        assertEquals(SyncPlanner.Type.UPLOAD, up.type);
        assertTrue(!up.fullUpload);           // samples never change: do not upload them again
        assertEquals(SyncPlanner.Type.APPLY_REMOTE, plan(new LocalSession(1, "c1", 50, 50), cs("a", 60), SyncMode.SYNC).actions.get(0).type);
    }

    @Test
    public void conflictsAreWonByTheNewerChange() {
        SyncPlanner.Action localWins = plan(new LocalSession(1, "c1", 80, 50), cs("a", 70), SyncMode.SYNC).actions.get(0);
        assertEquals(SyncPlanner.Type.UPLOAD, localWins.type);
        assertTrue(localWins.conflict);
        SyncPlanner.Action remoteWins = plan(new LocalSession(1, "c1", 60, 50), cs("a", 90), SyncMode.SYNC).actions.get(0);
        assertEquals(SyncPlanner.Type.APPLY_REMOTE, remoteWins.type);
        assertTrue(remoteWins.conflict);
        assertEquals(SyncPlanner.Type.UPLOAD, plan(new LocalSession(1, "c1", 70, 50), cs("a", 70), SyncMode.SYNC).actions.get(0).type); // tie: local wins
    }

    @Test
    public void unknownCloudDocumentsAreRestored() {
        Map<String, CloudSession> remote = new LinkedHashMap<>();
        remote.put("new", cs("x", 5));
        SyncPlanner.Plan p = SyncPlanner.plan(Collections.emptyList(), remote, SyncMode.SYNC);
        assertEquals(SyncPlanner.Type.RESTORE, p.actions.get(0).type);
        assertEquals("new", p.actions.get(0).cloudId);
    }

    @Test
    public void backupAndRestoreModesOnlyDoTheirOwnHalf() {
        LocalSession up = new LocalSession(1, "c1", 60, 50);
        Map<String, CloudSession> remote = new LinkedHashMap<>();
        remote.put("c1", cs("a", 50));
        remote.put("other", cs("b", 5));
        SyncPlanner.Plan backup = SyncPlanner.plan(Collections.singletonList(up), remote, SyncMode.BACKUP);
        assertEquals(1, backup.actions.size());
        assertEquals(SyncPlanner.Type.UPLOAD, backup.actions.get(0).type);
        assertEquals(1, backup.skipped);
        SyncPlanner.Plan restore = SyncPlanner.plan(Collections.singletonList(up), remote, SyncMode.RESTORE);
        assertEquals(1, restore.actions.size());
        assertEquals(SyncPlanner.Type.RESTORE, restore.actions.get(0).type);
        assertEquals(1, restore.skipped);
    }

    // ---------------- engine: summaries ----------------

    @Test
    public void fullSyncUploadsAppliesAndRestores() throws Exception {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "new local");
        local.add(2, "c2", 70, 50, "renamed here");
        local.add(3, "c3", 50, 50, "old name");
        local.add(4, "c4", 50, 50, "same");
        FakeCloud cloud = new FakeCloud();
        cloud.docs.put("c2", cs("old", 50));
        cloud.docs.put("c3", cs("new cloud name", 90));
        cloud.docs.put("c4", cs("same", 50));
        cloud.docs.put("c9", cs("only in cloud", 20));

        SyncResult r = SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals(2, r.uploaded);
        assertEquals(1, r.appliedFromCloud);
        assertEquals(1, r.restored);
        assertEquals(0, r.conflicts);
        assertEquals(5, cloud.docs.size());
        assertEquals("renamed here", cloud.docs.get("c2").name);
        assertEquals("new cloud name", local.names.get(3L));
        assertTrue(local.restored.contains("c9"));
        assertNotNull(local.rows.get(1L).cloudId);
        assertEquals(10, local.rows.get(1L).syncedAt);
        assertEquals(70, local.rows.get(2L).syncedAt);

        SyncResult again = SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals(0, again.uploaded + again.appliedFromCloud + again.restored);
    }

    @Test
    public void deletedMeanwhileIsSkippedNotCrashed() throws Exception {
        FakeLocal local = new FakeLocal() {
            @Override public CloudSession snapshot(long id) { return null; }
        };
        local.add(1, null, 10, 0, "gone");
        assertEquals(0, SyncEngine.run(local, new FakeCloud(), SyncMode.SYNC).uploaded);
    }

    @Test
    public void failureMidwayKeepsProgressAndNextRunContinues() {
        FakeLocal local = new FakeLocal();
        for (long i = 1; i <= 3; i++) local.add(i, null, 10 + i, 0, "s" + i);
        FakeCloud cloud = new FakeCloud();
        cloud.failPutAfter = 2;
        try {
            SyncEngine.run(local, cloud, SyncMode.BACKUP);
            throw new AssertionError("expected a failure");
        } catch (SyncException e) {
            assertTrue(e.isTransient());
        }
        assertEquals(2, cloud.docs.size());
        assertEquals(java.util.Arrays.asList("synced 1", "synced 2"), local.log);
        cloud.failPutAfter = Integer.MAX_VALUE;
        try {
            assertEquals(1, SyncEngine.run(local, cloud, SyncMode.BACKUP).uploaded);
        } catch (SyncException e) {
            throw new AssertionError(e);
        }
        assertEquals(3, cloud.docs.size());
    }

    @Test
    public void fetchFailureStopsBeforeAnyChange() {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "s");
        FakeCloud cloud = new FakeCloud();
        cloud.failFetch = new SyncException(SyncException.Category.PERMISSION, "rules");
        try {
            SyncEngine.run(local, cloud, SyncMode.SYNC);
            throw new AssertionError("expected a failure");
        } catch (SyncException e) {
            assertTrue(!e.isTransient());
            assertEquals(SyncException.Category.PERMISSION, e.getCategory());
        }
        assertTrue(local.log.isEmpty());
    }

    @Test
    public void conflictsAreCounted() throws Exception {
        FakeLocal local = new FakeLocal();
        local.add(1, "c1", 80, 50, "mine");
        FakeCloud cloud = new FakeCloud();
        cloud.docs.put("c1", cs("theirs", 70));
        SyncResult r = SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals(1, r.conflicts);
        assertEquals("mine", cloud.docs.get("c1").name);
    }

    @Test
    public void transientClassification() {
        assertTrue(new SyncException(SyncException.Category.NETWORK, "x").isTransient());
        assertTrue(new SyncException(SyncException.Category.OTHER, "x").isTransient());
        assertTrue(!new SyncException(SyncException.Category.AUTH, "x").isTransient());
        assertTrue(!new SyncException(SyncException.Category.NOT_CONFIGURED, "x").isTransient());
        assertTrue(!new SyncException(SyncException.Category.NOT_SIGNED_IN, "x").isTransient());
    }

    // ---------------- engine: raw samples (Realtime Database) ----------------

    @Test
    public void newRecordingUploadsItsRawSamplesInChunks() throws Exception {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "with data", 1200);
        FakeCloud cloud = new FakeCloud();
        SyncResult r = SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals(1, r.uploaded);
        String id = local.rows.get(1L).cloudId;
        assertEquals(1200, (int) cloud.sampleData.get(id));
        assertEquals(3, cloud.docs.get(id).sampleChunks);            // 500 + 500 + 200
    }

    @Test
    public void renameUploadsOnlyTheSummaryAndKeepsTheChunkCount() throws Exception {
        FakeLocal local = new FakeLocal();
        local.add(1, "c1", 70, 50, "renamed", 1200);
        FakeCloud cloud = new FakeCloud();
        cloud.docs.put("c1", csSamples("old", 50, 1200, 3));
        cloud.sampleData.put("c1", 1200);
        SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals("renamed", cloud.docs.get("c1").name);
        assertEquals(3, cloud.docs.get("c1").sampleChunks);
        assertTrue(cloud.deleted.isEmpty());
    }

    @Test
    public void withoutRealtimeDatabaseOnlySummariesAreSynced() throws Exception {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "with data", 800);
        FakeCloud cloud = new FakeCloud();
        cloud.samplesSupported = false;
        SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals(0, cloud.docs.get(local.rows.get(1L).cloudId).sampleChunks);
        assertTrue(cloud.sampleData.isEmpty());
    }

    @Test
    public void failedSampleUploadCleansUpAndDoesNotMarkSynced() {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "with data", 800);
        FakeCloud cloud = new FakeCloud();
        cloud.failSamples = true;
        try {
            SyncEngine.run(local, cloud, SyncMode.SYNC);
            throw new AssertionError("expected a failure");
        } catch (SyncException e) {
            assertTrue(e.isTransient());
        }
        assertEquals(1, cloud.deleted.size());
        assertTrue(cloud.docs.isEmpty());                              // the summary is written only after the samples
        assertNull(local.rows.get(1L).cloudId);
        assertTrue(local.log.isEmpty());
    }

    @Test
    public void failedSummaryAfterSamplesRemovesTheSamples() {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "with data", 800);
        FakeCloud cloud = new FakeCloud();
        cloud.failPutAfter = 0;
        try {
            SyncEngine.run(local, cloud, SyncMode.SYNC);
            throw new AssertionError("expected a failure");
        } catch (SyncException expected) {
            assertEquals(1, cloud.deleted.size());
            assertTrue(cloud.sampleData.isEmpty());
        }
    }

    @Test
    public void restoreDownloadsRawSamplesToo() throws Exception {
        FakeLocal local = new FakeLocal();
        FakeCloud cloud = new FakeCloud();
        cloud.docs.put("c9", csSamples("from other phone", 20, 900, 2));
        cloud.sampleData.put("c9", 900);
        SyncResult r = SyncEngine.run(local, cloud, SyncMode.RESTORE);
        assertEquals(1, r.restored);
        assertEquals(900, local.restoredSamples.get("c9").count);
    }

    @Test
    public void restoreFallsBackToSummaryWhenThereIsNoDataOrItIsTooBig() throws Exception {
        FakeLocal local = new FakeLocal();
        FakeCloud cloud = new FakeCloud();
        cloud.docs.put("none", csSamples("no samples uploaded", 20, 900, 0));
        cloud.docs.put("huge", csSamples("huge", 20, SyncEngine.MAX_RESTORE_SAMPLES + 1, 1000));
        SyncEngine.run(local, cloud, SyncMode.RESTORE);
        assertEquals(2, local.restored.size());
        assertTrue(local.restoredSamples.isEmpty());
        assertEquals(0, cloud.fetchSamplesCalls);
    }

    @Test
    public void failedSampleDownloadRestoresNothingAndCanBeRetried() {
        FakeLocal local = new FakeLocal();
        FakeCloud cloud = new FakeCloud();
        cloud.docs.put("c9", csSamples("x", 20, 900, 2));
        cloud.failSamples = true;
        try {
            SyncEngine.run(local, cloud, SyncMode.RESTORE);
            throw new AssertionError("expected a failure");
        } catch (SyncException e) {
            assertTrue(e.isTransient());
        }
        assertTrue(local.restored.isEmpty());                          // nothing half-restored
        cloud.failSamples = false;
        cloud.sampleData.put("c9", 900);
        try {
            assertEquals(1, SyncEngine.run(local, cloud, SyncMode.RESTORE).restored);
        } catch (SyncException e) {
            throw new AssertionError(e);
        }
        assertEquals(900, local.restoredSamples.get("c9").count);
    }
}
