package com.pocketlab.app.cloud;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Summaries in Cloud Firestore (see {@link FirebaseCloudStore} for the raw samples): {@code users/{uid}/sessions/{cloudId}}. Blocking calls; use only
 * from a background thread (WorkManager). Required security rules (Firebase console):
 * <pre>
 * match /users/{uid}/{document=**} { allow read, write: if request.auth != null &amp;&amp; request.auth.uid == uid; }
 * </pre>
 */
final class FirestoreCloudStore {

    private static final long TIMEOUT_SECONDS = 30;

    private final CollectionReference sessions;

    FirestoreCloudStore(String uid) {
        sessions = FirebaseFirestore.getInstance().collection("users").document(uid).collection("sessions");
    }

    Map<String, CloudSession> fetchAll() throws SyncException {
        try {
            // Source.SERVER: when offline this fails instead of silently returning stale cached data.
            QuerySnapshot snap = Tasks.await(sessions.get(Source.SERVER), TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Map<String, CloudSession> out = new LinkedHashMap<>();
            for (DocumentSnapshot d : snap.getDocuments()) {
                try {
                    out.put(d.getId(), CloudMapper.fromMap(d.getData()));
                } catch (IllegalArgumentException unreadable) {
                    // A damaged or newer-format document must not block everything else.
                }
            }
            return out;
        } catch (Exception e) {
            throw translate(e);
        }
    }

    void put(String cloudId, CloudSession session) throws SyncException {
        try {
            Tasks.await(sessions.document(cloudId).set(CloudMapper.toMap(session)), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw translate(e);
        }
    }

    static SyncException translate(Exception e) {
        Throwable t = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        if (t instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) t).getCode()) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return new SyncException(SyncException.Category.NETWORK, "No connection to the cloud.");
                case PERMISSION_DENIED:
                    return new SyncException(SyncException.Category.PERMISSION,
                            "The cloud refused access. Check the Firestore security rules.");
                case UNAUTHENTICATED:
                    return new SyncException(SyncException.Category.AUTH, "Please sign in again.");
                default:
                    break;
            }
        }
        if (t instanceof TimeoutException) {
            return new SyncException(SyncException.Category.NETWORK, "The cloud did not answer in time.");
        }
        if (t instanceof InterruptedException) Thread.currentThread().interrupt();
        return new SyncException(SyncException.Category.OTHER, t.getMessage() == null ? "Unknown cloud error." : t.getMessage());
    }
}
