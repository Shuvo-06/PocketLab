package com.pocketlab.app.net;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The last good downloaded experiment file, kept on disk. Files are written to a temporary name
 * and moved into place, so a crash or full disk never leaves a half-written cache.
 */
public final class ExperimentCache {

    private final File jsonFile;
    private final File etagFile;

    public ExperimentCache(File dir) {
        this.jsonFile = new File(dir, "experiments-cache.json");
        this.etagFile = new File(dir, "experiments-cache.etag");
    }

    /** The cached JSON, or null if there is none (or it cannot be read). */
    public String readJson() {
        try {
            return jsonFile.isFile() ? new String(Files.readAllBytes(jsonFile.toPath()), StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public String etag() {
        try {
            return etagFile.isFile() ? new String(Files.readAllBytes(etagFile.toPath()), StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** When the cache was last written (ms), or 0. */
    public long savedAt() {
        return jsonFile.isFile() ? jsonFile.lastModified() : 0;
    }

    public void save(String json, String etag) throws IOException {
        write(jsonFile, json);
        if (etag != null) write(etagFile, etag); else Files.deleteIfExists(etagFile.toPath());
    }

    public void clear() throws IOException {
        Files.deleteIfExists(jsonFile.toPath());
        Files.deleteIfExists(etagFile.toPath());
    }

    private static void write(File target, String text) throws IOException {
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
