package com.pocketlab.app.experiment;

import android.content.Context;

import androidx.annotation.Nullable;

import com.pocketlab.app.net.ExperimentCache;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Source of experiment definitions. Order of preference:
 * <ol>
 *   <li>the last valid file downloaded from the experiment server (cached on disk), then</li>
 *   <li>the file bundled in the app's assets, which always works, offline or not.</li>
 * </ol>
 * A cached file that no longer parses is ignored, so a bad download can never break experiments.
 */
public final class ExperimentRepository {

    private static List<ExperimentDefinition> loaded;
    private static boolean fromDownload;

    private ExperimentRepository() {}

    public static ExperimentCache diskCache(Context context) {
        return new ExperimentCache(context.getApplicationContext().getFilesDir());
    }

    public static synchronized List<ExperimentDefinition> load(Context context)
            throws IOException, JSONException {
        if (loaded == null) {
            List<ExperimentDefinition> downloaded = fromCache(context);
            if (downloaded != null) {
                loaded = downloaded;
                fromDownload = true;
            } else {
                loaded = parseBundled(context);
                fromDownload = false;
            }
        }
        return loaded;
    }

    /** Forces the next {@link #load} to read the cache again (call after a successful download). */
    public static synchronized void invalidate() {
        loaded = null;
    }

    /** True if the definitions currently in use came from the server rather than the app bundle. */
    public static synchronized boolean isFromDownload() {
        return fromDownload;
    }

    @Nullable
    public static ExperimentDefinition find(Context context, @Nullable String id)
            throws IOException, JSONException {
        for (ExperimentDefinition d : load(context)) {
            if (d.getId().equals(id)) return d;
        }
        return null;
    }

    @Nullable
    private static List<ExperimentDefinition> fromCache(Context context) {
        String json = diskCache(context).readJson();
        if (json == null) return null;
        try {
            List<ExperimentDefinition> defs = ExperimentParser.parse(json);
            return defs.isEmpty() ? null : defs;
        } catch (JSONException e) {
            return null;
        }
    }

    private static List<ExperimentDefinition> parseBundled(Context context) throws IOException, JSONException {
        try (InputStream in = context.getApplicationContext().getAssets().open("experiments.json");
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return ExperimentParser.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }
}
