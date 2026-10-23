package com.pocketlab.app.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Shows the provider being used the way any client would: through a ContentResolver. Used by
 * the "Provider demo" button in the About dialog.
 */
public final class ProviderDemo {

    public interface Callback {
        void onResult(String text);
    }

    private ProviderDemo() {}

    public static void runAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Executors.newSingleThreadExecutor().execute(() -> {
            String text;
            try {
                text = run(app);
            } catch (Exception e) {
                text = "The query failed: " + e.getMessage();
            }
            final String result = text;
            main.post(() -> callback.onResult(result));
        });
    }

    static String run(Context context) {
        ContentResolver cr = context.getContentResolver();
        StringBuilder sb = new StringBuilder();
        long newestId = -1;

        sb.append("Query 1: ").append(SessionContract.sessionsUri(context)).append('\n');
        try (Cursor c = cr.query(SessionContract.sessionsUri(context),
                new String[]{SessionContract.ID, SessionContract.NAME, SessionContract.SENSOR,
                        SessionContract.SAMPLE_COUNT}, null, null, null)) {
            if (c == null) return "The provider returned no cursor.";
            sb.append(c.getCount()).append(" saved session(s)\n");
            int shown = 0;
            while (c.moveToNext() && shown < 5) {
                if (shown == 0) newestId = c.getLong(0);
                sb.append("  #").append(c.getLong(0)).append(" ").append(c.getString(1))
                        .append(" (").append(c.getString(2)).append(", ").append(c.getLong(3)).append(" samples)\n");
                shown++;
            }
        }
        if (newestId < 0) return sb.append("\nRecord something first, then try again.").toString();

        sb.append("\nQuery 2: ").append(SessionContract.samplesUri(context, newestId)).append("?limit=3\n");
        try (Cursor c = cr.query(SessionContract.samplesUri(context, newestId).buildUpon()
                        .appendQueryParameter("limit", "3").build(),
                new String[]{SessionContract.TIME_SECONDS, SessionContract.V0}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                sb.append(String.format(Locale.US, "  t=%.3f s  v0=%.4f\n", c.getDouble(0), c.getDouble(1)));
            }
        }

        sb.append("\nQuery 3: ").append(SessionContract.markersUri(context, newestId)).append('\n');
        try (Cursor c = cr.query(SessionContract.markersUri(context, newestId), null, null, null, null)) {
            sb.append(c == null ? 0 : c.getCount()).append(" marker(s)\n");
        }

        try {
            cr.delete(SessionContract.sessionUri(context, newestId), null, null);
            sb.append("\nDelete: unexpectedly allowed!");
        } catch (UnsupportedOperationException e) {
            sb.append("\nDelete: refused (the provider is read-only)");
        }
        return sb.toString();
    }
}
