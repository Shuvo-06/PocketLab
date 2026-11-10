package com.pocketlab.app.cloud;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a {@link CloudSession} to and from the plain maps Firestore stores, and checks data
 * coming back from the cloud: it may be old, newer, or edited by hand, so nothing is trusted.
 */
public final class CloudMapper {

    public static final int SCHEMA = 1;
    /** Upper bound that keeps a hostile document from making us request millions of chunks. */
    public static final int MAX_CHUNKS = 5000;
    private static final int MAX_NAME = 80, MAX_LABEL = 40, MAX_RESULT = 20_000, MAX_MARKERS = 1000, MAX_STATS = 3;

    private CloudMapper() {}

    public static Map<String, Object> toMap(CloudSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", (long) SCHEMA);
        m.put("name", s.name);
        m.put("createdAt", s.createdAtMillis);
        m.put("sensor", s.sensor);
        m.put("samplingRate", s.samplingRate);
        m.put("kind", s.kind);
        m.put("experimentId", s.experimentId);
        m.put("experimentTitle", s.experimentTitle);
        m.put("durationSeconds", s.durationSeconds);
        m.put("sampleCount", s.sampleCount);
        m.put("resultText", s.resultText);
        m.put("modifiedAt", s.modifiedAt);
        m.put("sampleChunks", (long) s.sampleChunks);
        List<Map<String, Object>> markers = new ArrayList<>();
        for (Marker k : s.markers) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("time", k.getTimeSeconds());
            x.put("label", k.getLabel());
            markers.add(x);
        }
        m.put("markers", markers);
        List<Map<String, Object>> stats = new ArrayList<>();
        for (CloudSession.Stat t : s.stats) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("axis", t.axis);
            x.put("mean", finiteOrNull(t.mean));
            x.put("stdDev", finiteOrNull(t.stdDev));
            x.put("min", finiteOrNull(t.min));
            x.put("max", finiteOrNull(t.max));
            stats.add(x);
        }
        m.put("stats", stats);
        return m;
    }

    private static Double finiteOrNull(double d) {
        return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
    }

    /** @throws IllegalArgumentException with a short reason if the data is unusable */
    public static CloudSession fromMap(Map<String, Object> m) {
        if (m == null) throw new IllegalArgumentException("empty document");
        Object schema = m.get("schema");
        if (!(schema instanceof Number) || ((Number) schema).longValue() != SCHEMA) {
            throw new IllegalArgumentException("unsupported schema (made by a newer app version?)");
        }
        String name = clip(string(m, "name", true), MAX_NAME);
        if (name.trim().isEmpty()) throw new IllegalArgumentException("name is empty");
        String sensor = string(m, "sensor", true);
        String rate = string(m, "samplingRate", true);
        try {
            SensorType.valueOf(sensor);
            SamplingRate.valueOf(rate);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown sensor or sampling rate");
        }
        double duration = number(m, "durationSeconds", true);
        long samples = (long) number(m, "sampleCount", true);
        long created = (long) number(m, "createdAt", true);
        long modified = (long) number(m, "modifiedAt", true);
        if (duration < 0 || samples < 0 || modified < 0) throw new IllegalArgumentException("negative value");
        double chunksValue = number(m, "sampleChunks", false);       // absent in older backups
        int chunks = Double.isNaN(chunksValue) ? 0 : (int) chunksValue;
        if (chunks < 0 || chunks > MAX_CHUNKS) throw new IllegalArgumentException("bad sampleChunks");

        List<Marker> markers = new ArrayList<>();
        for (Object o : list(m, "markers", MAX_MARKERS)) {
            Map<String, Object> x = asMap(o);
            String label = clip(string(x, "label", false), MAX_LABEL);
            markers.add(new Marker(number(x, "time", true), label == null || label.isEmpty() ? "M" : label));
        }
        List<CloudSession.Stat> stats = new ArrayList<>();
        for (Object o : list(m, "stats", MAX_STATS)) {
            Map<String, Object> x = asMap(o);
            stats.add(new CloudSession.Stat(string(x, "axis", true), number(x, "mean", false),
                    number(x, "stdDev", false), number(x, "min", false), number(x, "max", false)));
        }
        String result = clip(string(m, "resultText", false), MAX_RESULT);
        return new CloudSession(name.trim(), created, sensor, rate, string(m, "kind", false) == null ? "FREE" : string(m, "kind", false),
                clip(string(m, "experimentId", false), 100), clip(string(m, "experimentTitle", false), 100),
                duration, samples, result, markers, stats, modified, chunks);
    }

    /** The saved result text plus the backed-up statistics, for a recording restored without raw data. */
    public static String restoredResultText(CloudSession s) {
        return restoredResultText(s, true);
    }

    /**
     * @param includeStats false when the raw samples were restored too: the detail screen then computes
     *                     the statistics itself and a second copy would only be noise
     */
    public static String restoredResultText(CloudSession s, boolean includeStats) {
        StringBuilder sb = new StringBuilder();
        if (s.resultText != null && !s.resultText.trim().isEmpty()) sb.append(s.resultText.trim());
        StringBuilder stats = new StringBuilder();
        String unit = SensorType.valueOf(s.sensor).getUnit();
        for (CloudSession.Stat t : s.stats) {
            if (Double.isNaN(t.mean)) continue;
            stats.append('\n').append(t.axis).append(": mean ").append(fmt(t.mean))
                    .append(" · σ ").append(Double.isNaN(t.stdDev) ? "–" : fmt(t.stdDev))
                    .append(" · min ").append(fmt(t.min)).append(" · max ").append(fmt(t.max)).append(' ').append(unit);
        }
        if (includeStats && stats.length() > 0) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("Summary from the cloud backup (raw measurements stay on the original device):").append(stats);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    // ------------------------------------------------------------ helpers

    private static String string(Map<String, Object> m, String key, boolean required) {
        Object v = m.get(key);
        if (v == null) {
            if (required) throw new IllegalArgumentException("missing " + key);
            return null;
        }
        if (!(v instanceof String)) throw new IllegalArgumentException(key + " is not text");
        return (String) v;
    }

    private static double number(Map<String, Object> m, String key, boolean required) {
        Object v = m.get(key);
        if (v == null) {
            if (required) throw new IllegalArgumentException("missing " + key);
            return Double.NaN;
        }
        if (!(v instanceof Number)) throw new IllegalArgumentException(key + " is not a number");
        double d = ((Number) v).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            if (required) throw new IllegalArgumentException(key + " is not finite");
            return Double.NaN;
        }
        return d;
    }

    private static List<?> list(Map<String, Object> m, String key, int max) {
        Object v = m.get(key);
        if (v == null) return new ArrayList<>();
        if (!(v instanceof List)) throw new IllegalArgumentException(key + " is not a list");
        List<?> l = (List<?>) v;
        if (l.size() > max) throw new IllegalArgumentException("too many entries in " + key);
        return l;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (!(o instanceof Map)) throw new IllegalArgumentException("list entry is not an object");
        return (Map<String, Object>) o;
    }

    private static String clip(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
