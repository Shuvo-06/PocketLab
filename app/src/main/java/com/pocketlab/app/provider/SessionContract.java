package com.pocketlab.app.provider;

import android.content.Context;
import android.net.Uri;

/**
 * Public description of the session provider: URIs and column names.
 *
 * <pre>
 * content://{package}.provider/sessions                   all saved sessions (summaries)
 * content://{package}.provider/sessions/{id}              one session
 * content://{package}.provider/sessions/{id}/samples      its samples; ?limit=N (max 10000) &amp;after=ROW_ID to page
 * content://{package}.provider/sessions/{id}/markers      its markers
 * </pre>
 * The provider is read-only. It ignores selection and sort order (rows come oldest-sample-first
 * or newest-session-first) and rejects unknown columns.
 */
public final class SessionContract {

    private SessionContract() {}

    public static final String ID = "_id";

    // sessions
    public static final String NAME = "name";
    public static final String CREATED_AT = "created_at";
    public static final String SENSOR = "sensor";
    public static final String SAMPLING_RATE = "sampling_rate";
    public static final String KIND = "kind";
    public static final String EXPERIMENT_ID = "experiment_id";
    public static final String DURATION_SECONDS = "duration_seconds";
    public static final String SAMPLE_COUNT = "sample_count";
    public static final String RESULT = "result";

    // samples and markers
    public static final String TIME_SECONDS = "time_seconds";
    public static final String V0 = "v0";
    public static final String V1 = "v1";
    public static final String V2 = "v2";
    public static final String LABEL = "label";

    public static final String[] SESSION_COLUMNS = {ID, NAME, CREATED_AT, SENSOR, SAMPLING_RATE, KIND,
            EXPERIMENT_ID, DURATION_SECONDS, SAMPLE_COUNT, RESULT};
    public static final String[] SAMPLE_COLUMNS = {ID, TIME_SECONDS, V0, V1, V2};
    public static final String[] MARKER_COLUMNS = {ID, TIME_SECONDS, LABEL};

    public static String authority(Context context) {
        return context.getPackageName() + ".provider";
    }

    public static Uri sessionsUri(Context context) {
        return Uri.parse("content://" + authority(context) + "/sessions");
    }

    public static Uri sessionUri(Context context, long id) {
        return Uri.parse(sessionsUri(context) + "/" + id);
    }

    public static Uri samplesUri(Context context, long id) {
        return Uri.parse(sessionUri(context, id) + "/samples");
    }

    public static Uri markersUri(Context context, long id) {
        return Uri.parse(sessionUri(context, id) + "/markers");
    }
}
