package com.pocketlab.app.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Downloads the experiment definitions with a conditional GET (ETag), so an unchanged file costs
 * almost nothing. HTTPS only (plain HTTP is allowed to localhost for tests), with timeouts and a
 * size limit. Uses only the JDK HTTP client, so it also runs in plain unit tests.
 */
public final class ExperimentDownloader {

    public static final int MAX_BYTES = 1024 * 1024;

    public static final class Response {
        /** 200 (new content) or 304 (not modified). */
        public final int status;
        public final String body;    // null for 304
        public final String etag;    // may be null

        Response(int status, String body, String etag) {
            this.status = status;
            this.body = body;
            this.etag = etag;
        }
    }

    private final int timeoutMs;
    private final boolean allowLocalhostHttp;

    public ExperimentDownloader() {
        this(10_000, false);
    }

    public ExperimentDownloader(int timeoutMs, boolean allowLocalhostHttp) {
        this.timeoutMs = timeoutMs;
        this.allowLocalhostHttp = allowLocalhostHttp;
    }

    public Response fetch(String url, String etag) throws IOException {
        URL u = new URL(url);
        boolean https = u.getProtocol().equals("https");
        boolean localhost = u.getProtocol().equals("http")
                && (u.getHost().equals("localhost") || u.getHost().equals("127.0.0.1"));
        if (!https && !(allowLocalhostHttp && localhost)) {
            throw new IOException("Only https:// addresses are allowed.");
        }
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("Accept", "application/json");
            if (etag != null) c.setRequestProperty("If-None-Match", etag);
            int code = c.getResponseCode();
            if (code == 304) return new Response(304, null, etag);
            if (code != 200) throw new HttpStatusException(code);

            String newTag = c.getHeaderField("ETag");
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > MAX_BYTES) throw new IOException("The file is too large.");
                    out.write(buf, 0, n);
                }
                return new Response(200, new String(out.toByteArray(), StandardCharsets.UTF_8), newTag);
            }
        } finally {
            c.disconnect();
        }
    }
}
