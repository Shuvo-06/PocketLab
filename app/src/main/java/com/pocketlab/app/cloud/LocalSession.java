package com.pocketlab.app.cloud;

/** Sync-relevant facts about one saved recording. {@code cloudId} is null if never backed up. */
public final class LocalSession {
    public final long localId;
    public final String cloudId;
    public final long modifiedAt;
    /** The {@code modifiedAt} value that was last uploaded or downloaded. */
    public final long syncedAt;

    public LocalSession(long localId, String cloudId, long modifiedAt, long syncedAt) {
        this.localId = localId;
        this.cloudId = cloudId;
        this.modifiedAt = modifiedAt;
        this.syncedAt = syncedAt;
    }
}
