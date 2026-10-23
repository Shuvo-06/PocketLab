package com.pocketlab.app.cloud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides what a sync must do, without doing it (pure Java, fully unit-tested).
 *
 * <p>Each side remembers {@code syncedAt}, the change time that was last exchanged. A recording
 * changed locally has {@code modifiedAt > syncedAt}; one changed in the cloud has a cloud
 * {@code modifiedAt > syncedAt}. If both changed (a conflict) the newer change wins.
 */
public final class SyncPlanner {

    public enum Type { UPLOAD, APPLY_REMOTE, RESTORE }

    public static final class Action {
        public final Type type;
        public final long localId;       // 0 for RESTORE
        public final String cloudId;     // null only for UPLOAD of a never-backed-up recording
        public final boolean conflict;

        Action(Type type, long localId, String cloudId, boolean conflict) {
            this.type = type;
            this.localId = localId;
            this.cloudId = cloudId;
            this.conflict = conflict;
        }
    }

    public static final class Plan {
        public final List<Action> actions = new ArrayList<>();
        public int skipped;
    }

    private SyncPlanner() {}

    public static Plan plan(List<LocalSession> locals, Map<String, CloudSession> remote, SyncMode mode) {
        Plan plan = new Plan();
        Set<String> known = new HashSet<>();
        boolean canUpload = mode != SyncMode.RESTORE;
        boolean canDownload = mode != SyncMode.BACKUP;

        for (LocalSession l : locals) {
            CloudSession r = l.cloudId == null ? null : remote.get(l.cloudId);
            if (l.cloudId != null) known.add(l.cloudId);

            if (l.cloudId == null || r == null) {
                // Never backed up, or the cloud copy is gone: (re)create it.
                add(plan, canUpload, new Action(Type.UPLOAD, l.localId, l.cloudId, false));
                continue;
            }
            boolean localChanged = l.modifiedAt > l.syncedAt;
            boolean remoteChanged = r.modifiedAt > l.syncedAt;
            if (!localChanged && !remoteChanged) continue;
            if (localChanged && !remoteChanged) {
                add(plan, canUpload, new Action(Type.UPLOAD, l.localId, l.cloudId, false));
            } else if (!localChanged) {
                add(plan, canDownload, new Action(Type.APPLY_REMOTE, l.localId, l.cloudId, false));
            } else if (l.modifiedAt >= r.modifiedAt) {                       // conflict: local is newer
                add(plan, canUpload, new Action(Type.UPLOAD, l.localId, l.cloudId, true));
            } else {                                                          // conflict: cloud is newer
                add(plan, canDownload, new Action(Type.APPLY_REMOTE, l.localId, l.cloudId, true));
            }
        }
        for (String cloudId : remote.keySet()) {
            if (!known.contains(cloudId)) add(plan, canDownload, new Action(Type.RESTORE, 0, cloudId, false));
        }
        return plan;
    }

    private static void add(Plan plan, boolean allowed, Action a) {
        if (allowed) plan.actions.add(a); else plan.skipped++;
    }
}
