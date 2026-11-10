package com.pocketlab.app.net;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Client for the free Open-Meteo REST API (no key needed):
 * {@code GET /v1/forecast?latitude=..&longitude=..&current=pressure_msl} answers with JSON such as
 * {@code {"current": {"pressure_msl": 1012.3, ...}}}. Used by the "Relative height" experiment to turn
 * barometer readings into altitudes above sea level. Pure JDK + org.json, so it is unit-tested against
 * a local test server.
 */
public final class WeatherApi {

    public static final String DEFAULT_URL = "https://api.open-meteo.com/v1/forecast";
    private static final int MAX_BYTES = 64 * 1024;
    /** Sea-level pressure on Earth stays well inside this range; anything else is treated as bad data. */
    private static final double MIN_HPA = 850, MAX_HPA = 1090;

    private final String baseUrl;
    private final int timeoutMs;
    private final boolean allowLocalhostHttp;

    public WeatherApi() {
        this(DEFAULT_URL, 8000, false);
    }

    public WeatherApi(String baseUrl, int timeoutMs, boolean allowLocalhostHttp) {
        this.baseUrl = baseUrl;
        this.timeoutMs = timeoutMs;
        this.allowLocalhostHttp = allowLocalhostHttp;
    }

    /**
     * @return the current mean-sea-level pressure in hPa at the given place
     * @throws IllegalArgumentException for impossible coordinates
     * @throws IOException for network, server or data problems (message is user-readable)
     */
    public double fetchSeaLevelPressure(double latitude, double longitude) throws IOException {
        if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
            throw new IllegalArgumentException("Latitude must be -90..90 and longitude -180..180.");
        }
        String url = String.format(Locale.US, "%s?latitude=%.4f&longitude=%.4f&current=pressure_msl",
                baseUrl, latitude, longitude);
        URL u = new URL(url);
        boolean https = u.getProtocol().equals("https");
        boolean localhost = u.getProtocol().equals("http")
                && (u.getHost().equals("localhost") || u.getHost().equals("127.0.0.1"));
        if (!https && !(allowLocalhostHttp && localhost)) throw new IOException("Only https:// addresses are allowed.");

        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new HttpStatusException(code);
            String body;
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (out.size() + n > MAX_BYTES) throw new IOException("The weather service answer is too large.");
                    out.write(buf, 0, n);
                }
                body = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
            return parse(body);
        } finally {
            c.disconnect();
        }
    }

    /** Reads {@code current.pressure_msl} and rejects missing or implausible values. */
    static double parse(String body) throws IOException {
        try {
            double hpa = new JSONObject(body).getJSONObject("current").getDouble("pressure_msl");
            if (Double.isNaN(hpa) || hpa < MIN_HPA || hpa > MAX_HPA) {
                throw new IOException("The weather service sent an unlikely pressure value.");
            }
            return hpa;
        } catch (JSONException e) {
            throw new IOException("The weather service sent an unexpected answer.");
        }
    }
}
