package com.pocketlab.app.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpServer;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public class WeatherApiTest {

    private static HttpServer server(int code, String body, AtomicReference<String> seenQuery) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/v1/forecast", ex -> {
            if (seenQuery != null) seenQuery.set(ex.getRequestURI().getRawQuery());
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
            ex.close();
        });
        s.start();
        return s;
    }

    private static WeatherApi api(HttpServer s) {
        return new WeatherApi("http://127.0.0.1:" + s.getAddress().getPort() + "/v1/forecast", 3000, true);
    }

    private static final String OK = "{\"latitude\":22.8,\"current\":{\"time\":\"2026-01-01T00:00\",\"interval\":900,\"pressure_msl\":1011.7}}";

    @Test
    public void readsSeaLevelPressureAndSendsTheRightQuery() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer s = server(200, OK, query);
        try {
            assertEquals(1011.7, api(s).fetchSeaLevelPressure(22.8456, 89.5403), 1e-9);
            assertTrue(query.get(), query.get().contains("latitude=22.8456"));
            assertTrue(query.get().contains("longitude=89.5403"));
            assertTrue(query.get().contains("current=pressure_msl"));
        } finally {
            s.stop(0);
        }
    }

    @Test
    public void badAnswersAreReportedNotCrashed() throws Exception {
        String[] bodies = {"not json", "{}", "{\"current\":{}}", "{\"current\":{\"pressure_msl\":\"high\"}}",
                "{\"current\":{\"pressure_msl\":5.0}}", "{\"current\":{\"pressure_msl\":99999}}"};
        for (String body : bodies) {
            HttpServer s = server(200, body, null);
            try {
                api(s).fetchSeaLevelPressure(10, 10);
                throw new AssertionError("accepted: " + body);
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("weather service"));
            } finally {
                s.stop(0);
            }
        }
    }

    @Test
    public void serverErrorsAndOfflineAreIOExceptions() throws Exception {
        HttpServer s = server(500, "oops", null);
        try {
            api(s).fetchSeaLevelPressure(10, 10);
            throw new AssertionError("expected failure");
        } catch (HttpStatusException e) {
            assertEquals(500, e.getCode());
        } finally {
            s.stop(0);
        }
        WeatherApi dead = api(s);          // server already stopped
        try {
            dead.fetchSeaLevelPressure(10, 10);
            throw new AssertionError("expected failure");
        } catch (IOException expected) {
            // offline
        }
    }

    @Test
    public void impossibleCoordinatesAndInsecureUrlsAreRefused() throws Exception {
        for (double[] c : new double[][]{{91, 0}, {-91, 0}, {0, 181}, {0, -181}, {Double.NaN, 0}}) {
            try {
                new WeatherApi().fetchSeaLevelPressure(c[0], c[1]);
                throw new AssertionError("accepted " + c[0] + "," + c[1]);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
        try {
            new WeatherApi("http://example.com/v1/forecast", 1000, false).fetchSeaLevelPressure(10, 10);
            throw new AssertionError("plain http accepted");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("https"));
        }
    }
}
