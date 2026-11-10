package com.pocketlab.app.cloud;

import android.content.Context;

import com.google.firebase.auth.FirebaseUser;
import com.pocketlab.app.data.AppDatabase;

/**
 * Runs one cloud sync for the signed-in account with the real Room database, Firestore (summaries)
 * and the Realtime Database (raw samples). Blocking: background threads only.
 */
public final class CloudSync {

    private CloudSync() {}

    public static SyncResult run(Context context, SyncMode mode) throws SyncException {
        Context app = context.getApplicationContext();
        if (!AuthManager.isConfigured(app)) {
            throw new SyncException(SyncException.Category.NOT_CONFIGURED, "Cloud backup is not set up in this build.");
        }
        FirebaseUser user = AuthManager.user(app);
        if (user == null) {
            throw new SyncException(SyncException.Category.NOT_SIGNED_IN, "Sign in to use cloud backup.");
        }
        // Recordings are owned by one account, so only this account's recordings are compared with the
        // cloud, and switching accounts on a device can never mix their data.
        return SyncEngine.run(new RoomLocalStore(AppDatabase.get(app), user.getUid()),
                new FirebaseCloudStore(user.getUid()), mode);
    }
}
