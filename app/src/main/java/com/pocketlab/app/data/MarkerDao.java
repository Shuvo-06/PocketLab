package com.pocketlab.app.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MarkerDao {

    @Insert
    void insertAll(List<MarkerEntity> markers);

    @Query("SELECT * FROM markers WHERE sessionId = :sessionId ORDER BY timeSeconds")
    List<MarkerEntity> forSession(long sessionId);
}
