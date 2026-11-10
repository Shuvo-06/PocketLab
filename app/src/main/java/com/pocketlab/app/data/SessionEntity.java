package com.pocketlab.app.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One saved recording. Samples and markers hang off it (and are deleted with it). */
@Entity(tableName = "sessions", indices = {@Index("createdAt")})
public class SessionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull public String name = "";
    /** Wall-clock start time, milliseconds since 1970. */
    public long createdAt;
    /** {@code SensorType} enum name. */
    @NonNull public String sensorType = "";
    /** {@code SamplingRate} enum name. */
    @NonNull public String samplingRate = "";
    /** {@code SessionKind} enum name. */
    @NonNull public String kind = "";
    @Nullable public String experimentId;
    @Nullable public String experimentTitle;
    public double durationSeconds;
    public long sampleCount;
    /** Experiment result or other saved text; null for free recordings. */
    @Nullable public String resultText;
    /** False while recording / if the app died mid-recording (purged on next start). */
    public boolean complete;
    /** Soft delete so a swipe-delete can be undone (purged on next start). */
    public boolean trashed;

    // ---- cloud sync (database version 2) ----
    /** Time of the last change (ms), stamped by SQL on finish, rename and recover. */
    @ColumnInfo(defaultValue = "0") public long modifiedAt;
    /** Firestore document id of the backup, or null if never backed up. */
    @Nullable public String cloudId;
    /** The {@code modifiedAt} value that was last uploaded or downloaded. */
    @ColumnInfo(defaultValue = "0") public long syncedAt;

    // ---- accounts (database version 3) ----
    /** Firebase uid of the account that owns this recording; "" = recorded without an account. */
    @NonNull @ColumnInfo(defaultValue = "''") public String ownerUid = "";
}
