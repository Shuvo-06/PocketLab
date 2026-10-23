package com.pocketlab.app.cloud;

/** Counts from one sync run. */
public final class SyncResult {
    public int uploaded;
    public int appliedFromCloud;
    public int restored;
    public int conflicts;     // changed on both sides; the newer change won
    public int skipped;       // not done because of the chosen mode

    public boolean changedAnything() {
        return uploaded + appliedFromCloud + restored > 0;
    }
}
