package com.pocketlab.app.cloud;

public enum SyncMode {
    /** Upload new and changed recordings, and take newer names/results from the cloud. */
    SYNC,
    /** Only upload. */
    BACKUP,
    /** Only download: recordings missing here, and newer changes made elsewhere. */
    RESTORE
}
