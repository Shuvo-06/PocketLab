package com.pocketlab.app.export;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.pocketlab.app.data.AppDatabase;
import com.pocketlab.app.data.MarkerEntity;
import com.pocketlab.app.data.RoomSessionStore;
import com.pocketlab.app.data.SessionEntity;
import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.experiment.ExperimentDefinition;
import com.pocketlab.app.experiment.ExperimentRepository;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;
import com.pocketlab.app.util.BuildConfigVersion;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Exports a saved session to a shareable file and imports PocketLab XML files.
 * All work runs on one background thread; callbacks arrive on the main thread.
 * Exported files live in the app cache ({@code cache/exports/}) and are removed after a day.
 */
public final class TransferManager {

    public enum Format {
        CSV("csv", "text/csv"),
        JSON("json", "application/json"),
        XML("xml", "text/xml"),
        PDF("pdf", "application/pdf");

        public final String extension;
        public final String mime;

        Format(String extension, String mime) {
            this.extension = extension;
            this.mime = mime;
        }
    }

    public interface Callback<T> {
        void onResult(T value);
    }

    public static final class ExportResult {
        @Nullable public final File file;
        public final Format format;
        @Nullable public final String error;

        ExportResult(@Nullable File file, Format format, @Nullable String error) {
            this.file = file;
            this.format = format;
            this.error = error;
        }
    }

    public static final class ImportResult {
        @Nullable public final String name;
        @Nullable public final String error;

        ImportResult(@Nullable String name, @Nullable String error) {
            this.name = name;
            this.error = error;
        }
    }

    private static final long KEEP_MILLIS = 24L * 60 * 60 * 1000;

    private final Context app;
    private final AppDatabase db;
    private final Executor io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public TransferManager(Context context, AppDatabase db) {
        this.app = context.getApplicationContext();
        this.db = db;
    }

    // ------------------------------------------------------------------ export

    public void exportAsync(long sessionId, Format format, Callback<ExportResult> callback) {
        io.execute(() -> {
            ExportResult r;
            try {
                r = new ExportResult(export(sessionId, format), format, null);
            } catch (Exception e) {
                r = new ExportResult(null, format, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            final ExportResult result = r;
            main.post(() -> callback.onResult(result));
        });
    }

    private File export(long sessionId, Format format) throws Exception {
        SessionEntity s = db.sessionDao().get(sessionId);
        if (s == null || s.trashed) throw new IOException("The recording no longer exists.");
        List<Marker> markers = new ArrayList<>();
        for (MarkerEntity m : db.markerDao().forSession(sessionId)) markers.add(new Marker(m.timeSeconds, m.label));

        SensorType sensor = SensorType.valueOf(s.sensorType);
        ExportHeader header = new ExportHeader(s.name, s.createdAt, sensor, SamplingRate.valueOf(s.samplingRate),
                SessionKind.valueOf(s.kind), s.experimentId, s.experimentTitle, s.durationSeconds,
                s.sampleCount, s.resultText);
        SampleSource source = new RoomSampleSource(db, sessionId);

        File dir = new File(app.getCacheDir(), "exports");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create the export folder.");
        deleteOld(dir);
        File out = new File(dir, SafeFileName.of(s.name, s.id) + "." + format.extension);

        try {
            if (format == Format.PDF) {
                ExportSummary summary = ExportSummary.compute(sensor, source, s.sampleCount, 1200);
                String objective = null;
                if (s.experimentId != null) {
                    try {
                        ExperimentDefinition def = ExperimentRepository.find(app, s.experimentId);
                        if (def != null) objective = def.getSummary();
                    } catch (Exception ignored) {
                        // The report is still useful without the objective text.
                    }
                }
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                    PdfReport.write(os, header, markers, summary, objective, BuildConfigVersion.name(app));
                }
            } else {
                try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
                    switch (format) {
                        case CSV: CsvExporter.write(header, markers, source, w); break;
                        case JSON: JsonExporter.write(header, markers, source, w); break;
                        default: XmlExporter.write(header, markers, source, w); break;
                    }
                }
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();   // never leave a half-written file to be shared
            throw e;
        }
        return out;
    }

    private static void deleteOld(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            if (now - f.lastModified() > KEEP_MILLIS) f.delete();
        }
    }

    // ------------------------------------------------------------------ import

    public void importAsync(Uri uri, Callback<ImportResult> callback) {
        io.execute(() -> {
            ImportResult r;
            try (InputStream in = app.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Cannot open the file.");
                ImportedSession session = XmlSessionReader.read(in);
                SessionImporter.save(session, new RoomSessionStore(db));
                r = new ImportResult(session.name, null);
            } catch (ImportException e) {
                r = new ImportResult(null, e.getMessage());
            } catch (IOException | SecurityException e) {
                r = new ImportResult(null, "The file could not be read.");
            } catch (Exception e) {
                r = new ImportResult(null, "The imported data could not be saved.");
            }
            final ImportResult result = r;
            main.post(() -> callback.onResult(result));
        });
    }
}
