package com.pocketlab.app.export;

import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import java.util.Arrays;
import java.util.List;

/** Shared fixtures for export tests. */
final class ExportTestSupport {
    private ExportTestSupport() {}

    static SampleSource source(double[] times, float[][] values) {
        return () -> new SampleSource.Cursor() {
            int pos;

            @Override
            public int read(double[] t, float[][] v, int max) {
                int n = Math.min(max, times.length - pos);
                for (int i = 0; i < n; i++) {
                    t[i] = times[pos + i];
                    for (int a = 0; a < values.length; a++) v[a][i] = values[a][pos + i];
                }
                pos += n;
                return n;
            }
        };
    }

    /** 2500 accelerometer samples, 100 Hz, x = i, y = 2i, z = 9.5. */
    static SampleSource accelSource(int n) {
        double[] t = new double[n];
        float[][] v = new float[3][n];
        for (int i = 0; i < n; i++) {
            t[i] = i * 0.01;
            v[0][i] = i;
            v[1][i] = 2f * i;
            v[2][i] = 9.5f;
        }
        return source(t, v);
    }

    static ExportHeader accelHeader(String name, int n, String result) {
        return new ExportHeader(name, 1_792_593_120_000L, SensorType.ACCELEROMETER, SamplingRate.GAME,
                SessionKind.EXPERIMENT, "pendulum", "Simple pendulum", (n - 1) * 0.01, n, result);
    }

    static List<Marker> markers() {
        return Arrays.asList(new Marker(0.5, "M1"), new Marker(1.25, "M2"));
    }
}
