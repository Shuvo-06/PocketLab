package com.pocketlab.app.cloud;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.pocketlab.app.export.SampleSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Raw measurements in the Firebase Realtime Database: {@code samples/{uid}/{cloudId}/{n}}, each node
 * holding 500 samples as the arrays {@code t}, {@code a}, {@code b}, {@code c} (b and c are left out
 * when all zero, as for single-axis sensors). The Realtime Database suits this bulk data (priced by
 * volume, not per write). Blocking calls: background threads only. Rules (Firebase console):
 * <pre>
 * { "rules": { "samples": { "$uid": { ".read": "auth != null &amp;&amp; auth.uid == $uid",
 *                                      ".write": "auth != null &amp;&amp; auth.uid == $uid" } } } }
 * </pre>
 */
final class RtdbSampleStore {

    private static final int CHUNK = 500;
    private static final long TIMEOUT_SECONDS = 60;
    static final String DB_URL = "https://pocketlab-6-default-rtdb.asia-southeast1.firebasedatabase.app";

    private final DatabaseReference root;

    RtdbSampleStore(String uid) {
        root = FirebaseDatabase.getInstance(DB_URL).getReference("samples").child(uid);
    }

    /** False when the project has no Realtime Database (no database URL): raw data is then not synced. */
    static boolean available() {
        try {
            FirebaseDatabase.getInstance(DB_URL);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    int putSamples(String cloudId, SampleSource source) throws SyncException {
        DatabaseReference base = root.child(cloudId);
        double[] t = new double[CHUNK];
        float[][] v = new float[3][CHUNK];
        int chunks = 0, n;
        try {
            SampleSource.Cursor cursor = source.open();
            while ((n = cursor.read(t, v, CHUNK)) > 0) {
                Tasks.await(base.child(String.valueOf(chunks)).setValue(chunk(t, v, n)), TIMEOUT_SECONDS, TimeUnit.SECONDS);
                chunks++;
            }
            return chunks;
        } catch (IOException e) {
            throw new SyncException(SyncException.Category.OTHER, "Could not read the recording: " + e.getMessage());
        } catch (Exception e) {
            throw translate(e);
        }
    }

    RawSamples fetchSamples(String cloudId, int chunks) throws SyncException {
        double[] times = new double[Math.max(CHUNK, 16)];
        float[][] values = new float[3][times.length];
        int count = 0;
        try {
            for (int i = 0; i < chunks; i++) {
                DataSnapshot snap = Tasks.await(root.child(cloudId).child(String.valueOf(i)).get(), TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!snap.exists()) throw new SyncException(SyncException.Category.OTHER, "The backup of the measurements is incomplete.");
                Object raw = snap.getValue();
                if (!(raw instanceof Map)) throw new SyncException(SyncException.Category.OTHER, "The backup of the measurements is damaged.");
                Map<?, ?> m = (Map<?, ?>) raw;
                double[] t = numbers(m.get("t"));
                double[] a = numbers(m.get("a"));
                double[] b = numbers(m.get("b"));
                double[] c = numbers(m.get("c"));
                int n = t.length;
                if (count + n > times.length) {
                    int size = Math.max(times.length * 2, count + n);
                    times = Arrays.copyOf(times, size);
                    for (int k = 0; k < 3; k++) values[k] = Arrays.copyOf(values[k], size);
                }
                for (int j = 0; j < n; j++) {
                    times[count + j] = t[j];
                    values[0][count + j] = (float) at(a, j);
                    values[1][count + j] = (float) at(b, j);
                    values[2][count + j] = (float) at(c, j);
                }
                count += n;
            }
            return new RawSamples(count, times, values);
        } catch (SyncException e) {
            throw e;
        } catch (Exception e) {
            throw translate(e);
        }
    }

    /** Best effort; failures are ignored (leftovers are only wasted space, never shown to the user). */
    void deleteSamples(String cloudId) {
        try {
            Tasks.await(root.child(cloudId).removeValue(), 15, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // nothing more to do
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> chunk(double[] t, float[][] v, int n) {
        Map<String, Object> m = new HashMap<>();
        List<Double> times = new ArrayList<>(n);
        for (int i = 0; i < n; i++) times.add(t[i]);
        m.put("t", times);
        String[] keys = {"a", "b", "c"};
        for (int k = 0; k < 3; k++) {
            boolean allZero = true;
            List<Double> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add((double) v[k][i]);
                if (v[k][i] != 0f) allZero = false;
            }
            if (k == 0 || !allZero) m.put(keys[k], list);
        }
        return m;
    }

    /** Arrays come back as a List, or as a Map keyed "0","1",... when the database stored them sparsely. */
    private static double[] numbers(Object o) {
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            double[] out = new double[l.size()];
            for (int i = 0; i < out.length; i++) out[i] = l.get(i) instanceof Number ? ((Number) l.get(i)).doubleValue() : 0;
            return out;
        }
        if (o instanceof Map) {
            TreeMap<Integer, Double> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                try {
                    sorted.put(Integer.parseInt(String.valueOf(e.getKey())),
                            e.getValue() instanceof Number ? ((Number) e.getValue()).doubleValue() : 0.0);
                } catch (NumberFormatException ignored) {
                    // not an array index
                }
            }
            int size = sorted.isEmpty() ? 0 : sorted.lastKey() + 1;
            double[] out = new double[size];
            for (Map.Entry<Integer, Double> e : sorted.entrySet()) out[e.getKey()] = e.getValue();
            return out;
        }
        return new double[0];
    }

    private static double at(double[] a, int i) {
        return i < a.length ? a[i] : 0;
    }

    static SyncException translate(Exception e) {
        Throwable t = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
        String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.US);
        if (msg.contains("permission denied")) {
            return new SyncException(SyncException.Category.PERMISSION,
                    "The realtime database refused access. Check its security rules.");
        }
        if (t instanceof TimeoutException || msg.contains("offline")) {
            return new SyncException(SyncException.Category.NETWORK, "No connection to the cloud.");
        }
        if (t instanceof InterruptedException) Thread.currentThread().interrupt();
        return new SyncException(SyncException.Category.OTHER,
                t.getMessage() == null ? "Unknown cloud error." : t.getMessage());
    }
}
