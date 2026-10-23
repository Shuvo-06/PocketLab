package com.pocketlab.app.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class ExperimentNetworkTest {

    private static String bundled() throws IOException {
        return new String(Files.readAllBytes(Paths.get("src/main/assets/experiments.json")), StandardCharsets.UTF_8);
    }

    private static File tempDir() throws IOException {
        File d = Files.createTempDirectory("pocketlab-cache").toFile();
        d.deleteOnExit();
        return d;
    }

    /** Tiny test server: /ok (with ETag), /bad, /empty, /missing (404), /boom (500), /big. */
    private static HttpServer server(AtomicInteger hits) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/ok", ex -> {
            hits.incrementAndGet();
            if ("\"v1\"".equals(ex.getRequestHeaders().getFirst("If-None-Match"))) {
                ex.sendResponseHeaders(304, -1);
            } else {
                byte[] body = bundled().getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("ETag", "\"v1\"");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream o = ex.getResponseBody()) { o.write(body); }
            }
            ex.close();
        });
        s.createContext("/bad", ex -> reply(ex, 200, "{\"experiments\": [{\"id\": \"x\"}]}"));
        s.createContext("/empty", ex -> reply(ex, 200, "{\"experiments\": []}"));
        s.createContext("/missing", ex -> reply(ex, 404, "nope"));
        s.createContext("/boom", ex -> reply(ex, 500, "oops"));
        s.createContext("/big", ex -> reply(ex, 200, new String(new char[ExperimentDownloader.MAX_BYTES + 10]).replace('\0', 'a')));
        s.start();
        return s;
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        ex.close();
    }

    private static String url(HttpServer s, String path) {
        return "http://127.0.0.1:" + s.getAddress().getPort() + path;
    }

    private static ExperimentDownloader downloader() {
        return new ExperimentDownloader(3000, true);
    }

    @Test
    public void downloadsValidatesCachesAndThenUsesEtag() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer s = server(hits);
        try {
            ExperimentCache cache = new ExperimentCache(tempDir());
            assertNull(cache.readJson());

            ExperimentUpdater.Result first = ExperimentUpdater.update(cache, downloader(), url(s, "/ok"));
            assertEquals(ExperimentUpdater.Status.UPDATED, first.status);
            assertEquals(4, first.count);
            assertEquals("\"v1\"", cache.etag());
            assertNotNull(cache.readJson());
            assertTrue(cache.savedAt() > 0);

            ExperimentUpdater.Result second = ExperimentUpdater.update(cache, downloader(), url(s, "/ok"));
            assertEquals(ExperimentUpdater.Status.UNCHANGED, second.status);
            assertEquals(2, hits.get());
        } finally {
            s.stop(0);
        }
    }

    @Test
    public void badServerResponsesNeverTouchTheCache() throws Exception {
        HttpServer s = server(new AtomicInteger());
        try {
            ExperimentCache cache = new ExperimentCache(tempDir());
            cache.save("{\"experiments\": []}", "\"old\"");
            String before = cache.readJson();

            ExperimentUpdater.Result invalid = ExperimentUpdater.update(cache, downloader(), url(s, "/bad"));
            assertEquals(ExperimentUpdater.Status.FAILED, invalid.status);
            assertTrue(invalid.message.contains("invalid"));
            assertTrue(!invalid.transientFailure);

            assertEquals(ExperimentUpdater.Status.FAILED, ExperimentUpdater.update(cache, downloader(), url(s, "/empty")).status);

            ExperimentUpdater.Result missing = ExperimentUpdater.update(cache, downloader(), url(s, "/missing"));
            assertTrue(missing.message.contains("404"));
            assertTrue(!missing.transientFailure);

            ExperimentUpdater.Result boom = ExperimentUpdater.update(cache, downloader(), url(s, "/boom"));
            assertTrue(boom.message.contains("500"));
            assertTrue(boom.transientFailure);                       // server errors are worth retrying

            ExperimentUpdater.Result big = ExperimentUpdater.update(cache, downloader(), url(s, "/big"));
            assertEquals(ExperimentUpdater.Status.FAILED, big.status);
            assertTrue(big.message.contains("too large"));

            assertEquals(before, cache.readJson());                  // cache untouched by every failure
            assertEquals("\"old\"", cache.etag());
        } finally {
            s.stop(0);
        }
    }

    @Test
    public void unreachableServerIsATransientNetworkFailure() throws Exception {
        HttpServer s = server(new AtomicInteger());
        String dead = url(s, "/ok");
        s.stop(0);                                                   // now nothing is listening
        ExperimentUpdater.Result r = ExperimentUpdater.update(new ExperimentCache(tempDir()), downloader(), dead);
        assertEquals(ExperimentUpdater.Status.FAILED, r.status);
        assertTrue(r.transientFailure);
        assertTrue(r.message, r.message.contains("internet"));
    }

    @Test
    public void notConfiguredAndInsecureAddressesAreRefused() throws Exception {
        ExperimentCache cache = new ExperimentCache(tempDir());
        assertEquals(ExperimentUpdater.Status.NOT_CONFIGURED, ExperimentUpdater.update(cache, downloader(), "").status);
        assertEquals(ExperimentUpdater.Status.NOT_CONFIGURED, ExperimentUpdater.update(cache, downloader(), null).status);
        ExperimentUpdater.Result plain = ExperimentUpdater.update(cache, new ExperimentDownloader(), "http://example.com/experiments.json");
        assertEquals(ExperimentUpdater.Status.FAILED, plain.status);
        assertTrue(plain.message.contains("https"));
        ExperimentUpdater.Result file = ExperimentUpdater.update(cache, downloader(), "file:///etc/passwd");
        assertEquals(ExperimentUpdater.Status.FAILED, file.status);
    }

    @Test
    public void cacheSurvivesRoundTripAndClear() throws Exception {
        ExperimentCache cache = new ExperimentCache(tempDir());
        cache.save("{\"a\":1}", null);
        assertEquals("{\"a\":1}", cache.readJson());
        assertNull(cache.etag());
        cache.save("{\"a\":2}", "\"t\"");
        assertEquals("\"t\"", cache.etag());
        cache.clear();
        assertNull(cache.readJson());
        assertEquals(0, cache.savedAt());
    }
}
