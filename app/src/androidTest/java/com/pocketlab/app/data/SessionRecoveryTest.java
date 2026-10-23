package com.pocketlab.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
public class SessionRecoveryTest {

    private AppDatabase db;
    private RoomSessionStore store;

    @Before
    public void create() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        store = new RoomSessionStore(db);
    }

    @After
    public void close() {
        db.close();
    }

    private long unfinished(String name, int samples) {
        long id = store.insertSession(new SessionHeader(name, 1, SensorType.LIGHT, SamplingRate.UI,
                SessionKind.FREE, null, null));
        double[] t = new double[samples];
        float[] v = new float[samples];
        for (int i = 0; i < samples; i++) { t[i] = i * 0.5; v[i] = i; }
        if (samples > 0) store.insertSamples(id, t, v, v, v, samples);
        return id;
    }

    @Test
    public void interruptedRecordingKeepsItsSavedSamples() {
        long id = unfinished("Cut short", 20);
        assertEquals(1, SessionRecovery.run(db));
        SessionEntity s = db.sessionDao().get(id);
        assertTrue(s.complete);
        assertEquals("Cut short (recovered)", s.name);
        assertEquals(20, s.sampleCount);
        assertEquals(9.5, s.durationSeconds, 1e-9);
        assertEquals(1, db.sessionDao().search("").size());
    }

    @Test
    public void emptyUnfinishedAndTrashedSessionsAreRemoved() {
        long empty = unfinished("Nothing saved", 0);
        long trashed = unfinished("Deleted", 3);
        store.finishSession(trashed, 1, 3, null, Collections.emptyList());
        db.sessionDao().setTrashed(trashed, true);
        assertEquals(0, SessionRecovery.run(db));
        assertNull(db.sessionDao().get(empty));
        assertNull(db.sessionDao().get(trashed));
    }

    @Test
    public void finishedSessionsAreLeftAlone() {
        long id = unfinished("Fine", 5);
        store.finishSession(id, 2, 5, "r", Collections.emptyList());
        assertEquals(0, SessionRecovery.run(db));
        assertEquals("Fine", db.sessionDao().get(id).name);
    }
}
