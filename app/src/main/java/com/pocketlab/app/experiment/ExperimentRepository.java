package com.pocketlab.app.experiment;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Source of experiment definitions. Today: the JSON bundled in assets.
 * Phase 9 will add a downloaded/cached copy in front of this one.
 */
public final class ExperimentRepository {

    private static List<ExperimentDefinition> cache;

    private ExperimentRepository() {}

    public static synchronized List<ExperimentDefinition> load(Context context)
            throws IOException, JSONException {
        if (cache == null) {
            try (InputStream in = context.getApplicationContext().getAssets().open("experiments.json");
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                cache = ExperimentParser.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return cache;
    }

    @Nullable
    public static ExperimentDefinition find(Context context, @Nullable String id)
            throws IOException, JSONException {
        for (ExperimentDefinition d : load(context)) {
            if (d.getId().equals(id)) return d;
        }
        return null;
    }
}
