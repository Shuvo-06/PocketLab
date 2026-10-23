package com.pocketlab.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SampleDao {

    /** Room runs a list insert in a single transaction. */
    @Insert
    void insertAll(List<SampleEntity> samples);

    @Query("SELECT COUNT(*) FROM samples WHERE sessionId = :sessionId")
    int count(long sessionId);

    /** Time of the last sample of a session (0 if it has none). */
    @Query("SELECT MAX(timeSeconds) FROM samples WHERE sessionId = :sessionId")
    double lastTime(long sessionId);

    /** The next {@code limit} rows after {@code afterId}, in insertion order (for streaming exports). */
    @Query("SELECT * FROM samples WHERE sessionId = :sessionId AND id > :afterId ORDER BY id LIMIT :limit")
    List<SampleEntity> page(long sessionId, long afterId, int limit);

    /** Every {@code step}-th row (step 1 = all rows), oldest first. */
    @Query("SELECT * FROM samples WHERE sessionId = :sessionId AND (id % :step) = 0 "
            + "ORDER BY timeSeconds, id")
    List<SampleEntity> decimated(long sessionId, int step);
}
