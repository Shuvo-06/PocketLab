package com.pocketlab.app.cloud;

import android.content.Context;

import com.google.firebase.auth.FirebaseUser;
import com.pocketlab.app.data.AppDatabase;

/** Runs one cloud sync with the real Room database and Firestore. Blocking: background threads only. */
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
        AppDatabase db = AppDatabase.get(app);
        SyncPrefs prefs = new SyncPrefs(app);

        // Backup links belong to one account. After switching accounts, forget them so everything
        // is uploaded to the new account instead of pointing at someone else's documents.
        if (!user.getUid().equals(prefs.getLastUid())) {
            db.sessionDao().clearSyncState();
            prefs.setLastUid(user.getUid());
        }
        return SyncEngine.run(new RoomLocalStore(db), new FirestoreCloudStore(user.getUid()), mode);
    }
}
