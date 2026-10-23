package com.pocketlab.app.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One measurement. Single-axis sensors use only v0. */
@Entity(tableName = "samples",
        foreignKeys = @ForeignKey(entity = SessionEntity.class, parentColumns = "id",
                childColumns = "sessionId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("sessionId")})
public class SampleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long sessionId;
    public double timeSeconds;
    public float v0;
    public float v1;
    public float v2;
}
