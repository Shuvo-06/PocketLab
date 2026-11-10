package com.pocketlab.app.data;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reads and edits saved sessions off the main thread; results are delivered on the main thread.
 * All work runs on ONE background thread, so a rename or delete is always visible to a read
 * that is requested after it.
 */
public final class SessionRepository {

    public interface Callback<T> {
        void onResult(T value);
    }

    /** Detail screens load at most this many samples; longer recordings are thinned evenly. */
    public static final int MAX_DETAIL_SAMPLES = 100_000;

    /** Tells the repository whose recordings to show: the signed-in account's uid, or "" for none. */
    public interface OwnerProvider {
        String currentOwner();
    }

    private final AppDatabase db;
    private final Executor io;
    private final OwnerProvider owner;
    private final Handler main = new Handler(Looper.getMainLooper());

    public SessionRepository(AppDatabase db, Executor io, OwnerProvider owner) {
        this.db = db;
        this.io = io;
        this.owner = owner;
    }

    /** A recorder that saves one recording into this database (its own writer thread). */
    public SessionRecorder newRecorder(SessionHeader header) {
        return new SessionRecorder(new RoomSessionStore(db), Executors.newSingleThreadExecutor(), header);
    }

    /** At app start: keeps what an interrupted recording already saved and removes deleted items. */
    public void recoverAndPurge() {
        io.execute(() -> {
            try {
                SessionRecovery.run(db);
            } catch (Exception ignored) {
                // Retried on the next start.
            }
        });
    }

    public void loadSessions(String query, Callback<List<SessionEntity>> callback) {
        final String who = owner.currentOwner();
        io.execute(() -> {
            List<SessionEntity> result;
            try {
                result = db.sessionDao().search(who, SessionText.escapeLike(query));
            } catch (Exception e) {
                result = new ArrayList<>();
            }
            final List<SessionEntity> r = result;
            main.post(() -> callback.onResult(r));
        });
    }

    /** Delivers null if the session does not exist, was deleted, or could not be read. */
    public void loadDetail(long id, Callback<SessionDetail> callback) {
        io.execute(() -> {
            SessionDetail result = null;
            try {
                SessionEntity s = db.sessionDao().get(id);
                if (s != null && !s.trashed) {
                    int total = db.sampleDao().count(id);
                    int step = Math.max(1, (int) Math.ceil(total / (double) MAX_DETAIL_SAMPLES));
                    result = new SessionDetail(s, db.sampleDao().decimated(id, step),
                            db.markerDao().forSession(id), total);
                }
            } catch (Exception e) {
                result = null;
            }
            final SessionDetail r = result;
            main.post(() -> callback.onResult(r));
        });
    }

    /** Delivers {total backed-up-able sessions, sessions not yet backed up}. */
    public void loadSyncCounts(Callback<int[]> callback) {
        final String who = owner.currentOwner();
        io.execute(() -> {
            int[] counts = new int[]{0, 0};
            try {
                counts[0] = db.sessionDao().countSyncable(who);
                counts[1] = db.sessionDao().countUnsynced(who);
            } catch (Exception ignored) {
                // Show zeros rather than crash.
            }
            main.post(() -> callback.onResult(counts));
        });
    }

    /** After signing in: recordings made without an account now belong to {@code uid}. Delivers how many. */
    public void claimGuestSessions(String uid, Callback<Integer> callback) {
        io.execute(() -> {
            int n = 0;
            try {
                n = db.sessionDao().claimGuestSessions(uid);
            } catch (Exception ignored) {
                // They simply stay unowned; claiming is tried again at the next sign-in.
            }
            final int claimed = n;
            main.post(() -> callback.onResult(claimed));
        });
    }

    public void rename(long id, String name) {
        io.execute(() -> db.sessionDao().rename(id, name));
    }

    public void setTrashed(long id, boolean trashed) {
        io.execute(() -> db.sessionDao().setTrashed(id, trashed));
    }
}
