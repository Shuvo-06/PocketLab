package com.pocketlab.app.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.MarkerEntity;
import com.pocketlab.app.data.SampleEntity;
import com.pocketlab.app.data.SessionEntity;

import java.util.List;

/**
 * Read-only ContentProvider for saved sessions, their samples and markers (see
 * {@link SessionContract}). Queries run on the caller's binder/background thread, never the UI
 * thread. Only finished, non-deleted sessions are visible. insert, update and delete are refused.
 *
 * <p>It is not exported (only this app can use it). To let other apps read the data, set
 * {@code android:exported="true"} and protect it with a {@code readPermission} of protection level
 * "signature" or "dangerous": session data can be personal.
 */
public final class SessionProvider extends ContentProvider {

    static final int DEFAULT_LIMIT = 1000;
    static final int MAX_LIMIT = 10_000;

    @Override
    public boolean onCreate() {
        return true;   // the database is opened lazily on first query
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (selection != null || (selectionArgs != null && selectionArgs.length > 0)) {
            throw new IllegalArgumentException("selection is not supported; use the URI and its parameters");
        }
        ProviderRoute route = ProviderRoute.parse(uri.getPathSegments());
        if (route == null) throw new IllegalArgumentException("Unknown URI: " + uri);
        AppDatabase db = AppDatabase.get(getContext());

        switch (route.type) {
            case SESSIONS: {
                String[] cols = columns(projection, SessionContract.SESSION_COLUMNS);
                MatrixCursor c = new MatrixCursor(cols);
                for (SessionEntity s : db.sessionDao().search("")) addSession(c, cols, s);
                return c;
            }
            case SESSION: {
                String[] cols = columns(projection, SessionContract.SESSION_COLUMNS);
                MatrixCursor c = new MatrixCursor(cols);
                SessionEntity s = visible(db, route.sessionId);
                if (s != null) addSession(c, cols, s);
                return c;
            }
            case SAMPLES: {
                String[] cols = columns(projection, SessionContract.SAMPLE_COLUMNS);
                MatrixCursor c = new MatrixCursor(cols);
                if (visible(db, route.sessionId) == null) return c;
                int limit = ProviderRoute.parseLimit(uri.getQueryParameter("limit"), DEFAULT_LIMIT, MAX_LIMIT);
                long after = ProviderRoute.parseAfter(uri.getQueryParameter("after"));
                List<SampleEntity> rows = db.sampleDao().page(route.sessionId, after, limit);
                for (SampleEntity e : rows) {
                    MatrixCursor.RowBuilder b = c.newRow();
                    for (String col : cols) b.add(sampleValue(col, e));
                }
                return c;
            }
            default: {   // MARKERS
                String[] cols = columns(projection, SessionContract.MARKER_COLUMNS);
                MatrixCursor c = new MatrixCursor(cols);
                if (visible(db, route.sessionId) == null) return c;
                for (MarkerEntity e : db.markerDao().forSession(route.sessionId)) {
                    MatrixCursor.RowBuilder b = c.newRow();
                    for (String col : cols) b.add(markerValue(col, e));
                }
                return c;
            }
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        ProviderRoute route = ProviderRoute.parse(uri.getPathSegments());
        if (route == null) return null;
        switch (route.type) {
            case SESSIONS: return "vnd.android.cursor.dir/vnd.pocketlab.session";
            case SESSION: return "vnd.android.cursor.item/vnd.pocketlab.session";
            case SAMPLES: return "vnd.android.cursor.dir/vnd.pocketlab.sample";
            default: return "vnd.android.cursor.dir/vnd.pocketlab.marker";
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("This provider is read-only");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("This provider is read-only");
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        throw new UnsupportedOperationException("This provider is read-only");
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private static SessionEntity visible(AppDatabase db, long id) {
        SessionEntity s = db.sessionDao().get(id);
        return s != null && s.complete && !s.trashed ? s : null;
    }

    private static String[] columns(@Nullable String[] projection, String[] all) {
        if (projection == null) return all;
        for (String p : projection) {
            boolean known = false;
            for (String a : all) if (a.equals(p)) known = true;
            if (!known) throw new IllegalArgumentException("Unknown column: " + p);
        }
        return projection;
    }

    private static void addSession(MatrixCursor c, String[] cols, SessionEntity s) {
        MatrixCursor.RowBuilder b = c.newRow();
        for (String col : cols) {
            switch (col) {
                case SessionContract.ID: b.add(s.id); break;
                case SessionContract.NAME: b.add(s.name); break;
                case SessionContract.CREATED_AT: b.add(s.createdAt); break;
                case SessionContract.SENSOR: b.add(s.sensorType); break;
                case SessionContract.SAMPLING_RATE: b.add(s.samplingRate); break;
                case SessionContract.KIND: b.add(s.kind); break;
                case SessionContract.EXPERIMENT_ID: b.add(s.experimentId); break;
                case SessionContract.DURATION_SECONDS: b.add(s.durationSeconds); break;
                case SessionContract.SAMPLE_COUNT: b.add(s.sampleCount); break;
                default: b.add(s.resultText); break;   // RESULT
            }
        }
    }

    private static Object sampleValue(String col, SampleEntity e) {
        switch (col) {
            case SessionContract.ID: return e.id;
            case SessionContract.TIME_SECONDS: return e.timeSeconds;
            case SessionContract.V0: return e.v0;
            case SessionContract.V1: return e.v1;
            default: return e.v2;   // V2
        }
    }

    private static Object markerValue(String col, MarkerEntity e) {
        switch (col) {
            case SessionContract.ID: return e.id;
            case SessionContract.TIME_SECONDS: return e.timeSeconds;
            default: return e.label;   // LABEL
        }
    }
}
