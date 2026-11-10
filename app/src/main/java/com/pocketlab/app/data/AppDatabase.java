package com.pocketlab.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * The app's SQLite database (Room).
 * Version 1: sessions, samples, markers. Version 2: cloud-sync columns on sessions.
 * Version 3: {@code ownerUid}, so every account only sees its own recordings.
 */
@Database(entities = {SessionEntity.class, SampleEntity.class, MarkerEntity.class},
        version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract SessionDao sessionDao();
    public abstract SampleDao sampleDao();
    public abstract MarkerDao markerDao();

    /** Adds the cloud-sync columns. Existing sessions count as changed so the first sync uploads them. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN modifiedAt INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sessions ADD COLUMN cloudId TEXT");
            db.execSQL("ALTER TABLE sessions ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0");
            db.execSQL("UPDATE sessions SET modifiedAt = createdAt");
        }
    };

    /** Existing recordings belong to nobody ("") until someone signs in on this device and claims them. */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''");
        }
    };

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "pocketlab.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }
}
