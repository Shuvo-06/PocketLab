package com.pocketlab.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SessionDao {

    /** Current time in milliseconds, computed by SQLite, used to stamp every change for cloud sync. */
    String NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";

    @Insert
    long insert(SessionEntity session);

    /**
     * Finished, not deleted sessions of one account ("" = no account) whose name contains {@code query}
     * (already LIKE-escaped), newest first.
     */
    @Query("SELECT * FROM sessions WHERE complete = 1 AND trashed = 0 AND ownerUid = :owner "
            + "AND name LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY createdAt DESC")
    List<SessionEntity> search(String owner, String query);

    @Query("SELECT * FROM sessions WHERE id = :id")
    SessionEntity get(long id);

    @Query("UPDATE sessions SET durationSeconds = :duration, sampleCount = :count, "
            + "resultText = :resultText, complete = 1, modifiedAt = " + NOW_MS + " WHERE id = :id")
    void finish(long id, double duration, long count, String resultText);

    @Query("UPDATE sessions SET name = :name, modifiedAt = " + NOW_MS + " WHERE id = :id")
    void rename(long id, String name);

    @Query("UPDATE sessions SET trashed = :trashed WHERE id = :id")
    void setTrashed(long id, boolean trashed);

    @Query("DELETE FROM sessions WHERE id = :id")
    void delete(long id);

    /** Sessions whose recording never finished (app or phone died mid-recording). */
    @Query("SELECT * FROM sessions WHERE complete = 0 AND trashed = 0")
    List<SessionEntity> incomplete();

    /** Removes only the sessions the user deleted; returns how many. */
    @Query("DELETE FROM sessions WHERE trashed = 1")
    int purgeTrashed();

    /** Finishes an interrupted session with what was saved before the interruption. */
    @Query("UPDATE sessions SET name = :name, durationSeconds = :duration, sampleCount = :count, "
            + "complete = 1, modifiedAt = " + NOW_MS + " WHERE id = :id")
    void recover(long id, String name, double duration, long count);

    // ---- cloud sync ----

    /** Finished, not deleted sessions of one account: what can be backed up. */
    @Query("SELECT * FROM sessions WHERE complete = 1 AND trashed = 0 AND ownerUid = :owner")
    List<SessionEntity> syncable(String owner);

    @Query("UPDATE sessions SET cloudId = :cloudId, syncedAt = :syncedAt WHERE id = :id")
    void markSynced(long id, String cloudId, long syncedAt);

    /** Takes a newer name from the cloud; both times become the cloud's change time. */
    @Query("UPDATE sessions SET name = :name, modifiedAt = :time, syncedAt = :time WHERE id = :id")
    void applyRemote(long id, String name, long time);

    /**
     * Gives the recordings made without an account to the account that just signed in. Their backup
     * state is reset so they are uploaded to that account.
     */
    @Query("UPDATE sessions SET ownerUid = :uid, cloudId = NULL, syncedAt = 0, modifiedAt = " + NOW_MS
            + " WHERE ownerUid = '' AND complete = 1")
    int claimGuestSessions(String uid);

    @Query("SELECT COUNT(*) FROM sessions WHERE complete = 1 AND trashed = 0 AND ownerUid = :owner")
    int countSyncable(String owner);

    @Query("SELECT COUNT(*) FROM sessions WHERE complete = 1 AND trashed = 0 AND ownerUid = :owner "
            + "AND (cloudId IS NULL OR modifiedAt > syncedAt)")
    int countUnsynced(String owner);

    /** Removes unfinished and trashed sessions; returns how many were removed. */
    @Query("DELETE FROM sessions WHERE complete = 0 OR trashed = 1")
    int purge();
}
