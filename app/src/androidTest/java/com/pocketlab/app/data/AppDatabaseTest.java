package com.pocketlab.app.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/** Runs on a device or emulator (Room needs real SQLite). */
@RunWith(AndroidJUnit4.class)
public class AppDatabaseTest {

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

    private long newSession(String name) {
        return store.insertSession(new SessionHeader(name, 1000, SensorType.ACCELEROMETER,
                SamplingRate.GAME, SessionKind.FREE, null, null));
    }

    private void addSamples(long id, int n) {
        double[] t = new double[n];
        float[] v = new float[n];
        for (int i = 0; i < n; i++) { t[i] = i * 0.02; v[i] = i; }
        store.insertSamples(id, t, v, v, v, n);
    }

    @Test
    public void unfinishedSessionsAreHiddenUntilFinished() {
        long id = newSession("Run A");
        assertEquals(0, db.sessionDao().search("").size());
        store.finishSession(id, 1.0, 50, "ok", Collections.singletonList(new Marker(0.5, "M1")));
        List<SessionEntity> all = db.sessionDao().search("");
        assertEquals(1, all.size());
        assertEquals("ok", all.get(0).resultText);
        assertEquals(1, db.markerDao().forSession(id).size());
    }

    @Test
    public void samplesAreStoredInOrderAndDecimated() {
        long id = newSession("Run");
        addSamples(id, 1000);
        store.finishSession(id, 20, 1000, null, Collections.emptyList());
        assertEquals(1000, db.sampleDao().count(id));
        List<SampleEntity> all = db.sampleDao().decimated(id, 1);
        assertEquals(1000, all.size());
        assertEquals(0.0, all.get(0).timeSeconds, 0);
        assertEquals(100, db.sampleDao().decimated(id, 10).size());
    }

    @Test
    public void deletingASessionDeletesItsSamplesAndMarkers() {
        long id = newSession("Run");
        addSamples(id, 10);
        store.finishSession(id, 1, 10, null, Collections.singletonList(new Marker(0.1, "M1")));
        db.sessionDao().delete(id);
        assertEquals(0, db.sampleDao().count(id));
        assertEquals(0, db.markerDao().forSession(id).size());
        assertNull(db.sessionDao().get(id));
    }

    @Test
    public void trashHidesRestoreShowsPurgeRemoves() {
        long a = newSession("A");
        long b = newSession("B");
        store.finishSession(a, 1, 0, null, Collections.emptyList());
        store.finishSession(b, 1, 0, null, Collections.emptyList());
        db.sessionDao().setTrashed(a, true);
        assertEquals(1, db.sessionDao().search("").size());
        db.sessionDao().setTrashed(a, false);
        assertEquals(2, db.sessionDao().search("").size());
        db.sessionDao().setTrashed(a, true);
        newSession("never finished");
        assertEquals(2, db.sessionDao().purge());   // the trashed one and the unfinished one
        assertNull(db.sessionDao().get(a));
        assertNotNull(db.sessionDao().get(b));
    }

    @Test
    public void searchMatchesPartOfNameAndTreatsWildcardsLiterally() {
        long a = newSession("Pendulum 100% test");
        long b = newSession("Pendulum other");
        store.finishSession(a, 1, 0, null, Collections.emptyList());
        store.finishSession(b, 1, 0, null, Collections.emptyList());
        assertEquals(2, db.sessionDao().search(SessionText.escapeLike("pendulum")).size());
        assertEquals(1, db.sessionDao().search(SessionText.escapeLike("100%")).size());
        assertEquals(1, db.sessionDao().search(SessionText.escapeLike("%")).size()); // only the name containing a literal %
    }

    @Test
    public void discardedRecorderLeavesNothingBehind() {
        SessionRecorder rec = new SessionRecorder(store, Runnable::run, new SessionHeader("E2E", 5,
                SensorType.LIGHT, SamplingRate.UI, SessionKind.EXPERIMENT, "light", "Light intensity"));
        rec.begin();
        for (int i = 0; i < 1200; i++) rec.onSample(i * 0.1, new float[]{i});
        rec.discard();
        assertEquals(0, db.sessionDao().search("").size());
        assertNull(rec.getError());
    }
}
