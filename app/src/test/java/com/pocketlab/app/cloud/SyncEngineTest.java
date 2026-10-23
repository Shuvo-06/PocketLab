package com.pocketlab.app.cloud;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    static class FakeLocal implements SyncEngine.LocalStore {
        final Map<Long, LocalSession> rows = new LinkedHashMap<>();
        final Map<Long, String> names = new LinkedHashMap<>();
        final List<String> restored = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        long nextId = 100;

        void add(long id, String cloudId, long modified, long synced, String name) {
            rows.put(id, new LocalSession(id, cloudId, modified, synced));
            names.put(id, name);
        }

        @Override public List<LocalSession> listSyncable() { return new ArrayList<>(rows.values()); }
        @Override public CloudSession snapshot(long id) { return rows.containsKey(id) ? cs(names.get(id), rows.get(id).modifiedAt) : null; }
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
        @Override public void restore(String cloudId, CloudSession r) {
            long id = nextId++;
            rows.put(id, new LocalSession(id, cloudId, r.modifiedAt, r.modifiedAt));
            names.put(id, r.name);
            restored.add(cloudId);
        }
    }

    static class FakeCloud implements SyncEngine.CloudStore {
        final Map<String, CloudSession> docs = new LinkedHashMap<>();
        SyncException failFetch;
        int failPutAfter = Integer.MAX_VALUE;   // fail once this many puts succeeded
        int puts;

        @Override public Map<String, CloudSession> fetchAll() throws SyncException {
            if (failFetch != null) throw failFetch;
            return new LinkedHashMap<>(docs);
        }
        @Override public void put(String id, CloudSession s) throws SyncException {
            if (puts >= failPutAfter) throw new SyncException(SyncException.Category.NETWORK, "offline");
            puts++;
            docs.put(id, s);
        }
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
    public void neverBackedUpIsUploadedWithNewId() {
        SyncPlanner.Plan p = plan(new LocalSession(1, null, 50, 0), null, SyncMode.SYNC);
        assertEquals(SyncPlanner.Type.UPLOAD, p.actions.get(0).type);
        assertNull(p.actions.get(0).cloudId);
    }

    @Test
    public void missingCloudCopyIsRecreatedUnderTheSameId() {
        SyncPlanner.Plan p = plan(new LocalSession(1, "c1", 50, 50), null, SyncMode.SYNC);
        assertEquals("c1", p.actions.get(0).cloudId);
        assertEquals(SyncPlanner.Type.UPLOAD, p.actions.get(0).type);
    }

    @Test
    public void localChangeUploadsAndRemoteChangeIsApplied() {
        assertEquals(SyncPlanner.Type.UPLOAD, plan(new LocalSession(1, "c1", 60, 50), cs("a", 50), SyncMode.SYNC).actions.get(0).type);
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
        LocalSession up = new LocalSession(1, "c1", 60, 50);                       // needs upload
        Map<String, CloudSession> remote = new LinkedHashMap<>();
        remote.put("c1", cs("a", 50));
        remote.put("other", cs("b", 5));                                          // needs restore
        SyncPlanner.Plan backup = SyncPlanner.plan(Collections.singletonList(up), remote, SyncMode.BACKUP);
        assertEquals(1, backup.actions.size());
        assertEquals(SyncPlanner.Type.UPLOAD, backup.actions.get(0).type);
        assertEquals(1, backup.skipped);
        SyncPlanner.Plan restore = SyncPlanner.plan(Collections.singletonList(up), remote, SyncMode.RESTORE);
        assertEquals(1, restore.actions.size());
        assertEquals(SyncPlanner.Type.RESTORE, restore.actions.get(0).type);
        assertEquals(1, restore.skipped);
    }

    // ---------------- engine ----------------

    @Test
    public void fullSyncUploadsAppliesAndRestores() throws Exception {
        FakeLocal local = new FakeLocal();
        local.add(1, null, 10, 0, "new local");            // upload as new
        local.add(2, "c2", 70, 50, "renamed here");        // upload change
        local.add(3, "c3", 50, 50, "old name");            // cloud renamed it
        local.add(4, "c4", 50, 50, "same");                // untouched
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
        assertEquals(5, cloud.docs.size());                                    // c2 updated, one new id added
        assertEquals("renamed here", cloud.docs.get("c2").name);
        assertEquals("new cloud name", local.names.get(3L));
        assertTrue(local.restored.contains("c9"));
        assertTrue(local.rows.get(1L).cloudId != null);                        // got a generated id
        assertEquals(10, local.rows.get(1L).syncedAt);
        assertEquals(70, local.rows.get(2L).syncedAt);

        // A second run finds nothing left to do.
        SyncResult again = SyncEngine.run(local, cloud, SyncMode.SYNC);
        assertEquals(0, again.uploaded + again.appliedFromCloud + again.restored);
    }

    @Test
    public void deletedMeanwhileIsSkippedNotCrashed() throws Exception {
        FakeLocal local = new FakeLocal() {
            @Override public CloudSession snapshot(long id) { return null; }
        };
        local.add(1, null, 10, 0, "gone");
        SyncResult r = SyncEngine.run(local, new FakeCloud(), SyncMode.SYNC);
        assertEquals(0, r.uploaded);
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
            SyncResult r = SyncEngine.run(local, cloud, SyncMode.BACKUP);
            assertEquals(1, r.uploaded);                                         // only the one that was left
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
}
