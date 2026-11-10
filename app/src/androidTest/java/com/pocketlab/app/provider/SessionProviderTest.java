package com.pocketlab.app.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.RoomSessionStore;
import com.pocketlab.app.data.SessionHeader;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/** Uses the app's real database through its ContentProvider; cleans up after itself. */
@RunWith(AndroidJUnit4.class)
public class SessionProviderTest {

    private Context context;
    private ContentResolver resolver;
    private AppDatabase db;
    private long id;

    @Before
    public void createSession() {
        context = ApplicationProvider.getApplicationContext();
        resolver = context.getContentResolver();
        db = AppDatabase.get(context);
        RoomSessionStore store = new RoomSessionStore(db);
        // The provider shows only the signed-in account's recordings ("" when nobody is signed in).
        id = store.insertSession(new SessionHeader("Provider test", 1, SensorType.ACCELEROMETER,
                SamplingRate.GAME, SessionKind.FREE, null, null, AuthManager.uid(context)));
        double[] t = {0, 1, 2, 3, 4};
        float[] v = {1, 2, 3, 4, 5};
        store.insertSamples(id, t, v, v, v, 5);
        store.finishSession(id, 4, 5, "result", Collections.singletonList(new Marker(2, "M1")));
    }

    @After
    public void cleanUp() {
        db.sessionDao().delete(id);
    }

    @Test
    public void listsAndReadsOneSession() {
        try (Cursor c = resolver.query(SessionContract.sessionUri(context, id), null, null, null, null)) {
            assertNotNull(c);
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals("Provider test", c.getString(c.getColumnIndexOrThrow(SessionContract.NAME)));
            assertEquals(5, c.getLong(c.getColumnIndexOrThrow(SessionContract.SAMPLE_COUNT)));
        }
        try (Cursor all = resolver.query(SessionContract.sessionsUri(context),
                new String[]{SessionContract.ID}, null, null, null)) {
            assertTrue(all.getCount() >= 1);
        }
    }

    @Test
    public void samplesSupportLimitAndPaging() {
        try (Cursor c = resolver.query(SessionContract.samplesUri(context, id).buildUpon()
                .appendQueryParameter("limit", "2").build(), null, null, null, null)) {
            assertEquals(2, c.getCount());
            c.moveToLast();
            long lastRowId = c.getLong(c.getColumnIndexOrThrow(SessionContract.ID));
            try (Cursor next = resolver.query(SessionContract.samplesUri(context, id).buildUpon()
                    .appendQueryParameter("after", String.valueOf(lastRowId)).build(), null, null, null, null)) {
                assertEquals(3, next.getCount());
            }
        }
    }

    @Test
    public void markersAreReadable() {
        try (Cursor c = resolver.query(SessionContract.markersUri(context, id), null, null, null, null)) {
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals("M1", c.getString(c.getColumnIndexOrThrow(SessionContract.LABEL)));
        }
    }

    @Test
    public void trashedSessionsAreInvisible() {
        db.sessionDao().setTrashed(id, true);
        try (Cursor c = resolver.query(SessionContract.sessionUri(context, id), null, null, null, null)) {
            assertEquals(0, c.getCount());
        }
        try (Cursor c = resolver.query(SessionContract.samplesUri(context, id), null, null, null, null)) {
            assertEquals(0, c.getCount());
        }
    }

    @Test
    public void providerIsReadOnlyAndStrict() {
        try {
            resolver.delete(SessionContract.sessionUri(context, id), null, null);
            fail("delete must be refused");
        } catch (UnsupportedOperationException expected) {
            // refused, as intended
        }
        try {
            resolver.query(SessionContract.sessionsUri(context), new String[]{"password"}, null, null, null);
            fail("unknown column must be rejected");
        } catch (IllegalArgumentException expected) {
            // rejected, as intended
        }
        try {
            resolver.query(SessionContract.sessionsUri(context), null, "1=1", null, null);
            fail("selection must be rejected");
        } catch (IllegalArgumentException expected) {
            // rejected, as intended
        }
    }
}
