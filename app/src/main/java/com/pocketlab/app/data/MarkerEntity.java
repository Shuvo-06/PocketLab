package com.pocketlab.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "markers",
        foreignKeys = @ForeignKey(entity = SessionEntity.class, parentColumns = "id",
                childColumns = "sessionId", onDelete = ForeignKey.CASCADE),
        indices = {@Index("sessionId")})
public class MarkerEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long sessionId;
    public double timeSeconds;
    @NonNull public String label = "";
}
