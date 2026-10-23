package com.pocketlab.app.export;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SensorType;

import org.json.JSONObject;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;

/**
 * JSON written as a stream (so large recordings do not need much memory). Samples are rows:
 * {@code "columns": ["time","x","y","z"], "samples": [[0.0, 0.1, 0.2, 9.8], ...]}.
 */
public final class JsonExporter {

    private static final int CHUNK = 1000;

    private JsonExporter() {}

    private static String q(String s) {
        return s == null ? "null" : JSONObject.quote(s);
    }

    public static void write(ExportHeader h, List<Marker> markers, SampleSource source, Writer out)
            throws IOException {
        SensorType t = h.sensor;
        int axes = t.getAxes();
        ExportSummary sum = ExportSummary.compute(t, source, h.sampleCount, 0);

        out.write("{\n");
        out.write("  \"format\": \"pocketlab-session\",\n  \"version\": 1,\n");
        out.write("  \"name\": " + q(h.name) + ",\n");
        out.write("  \"recorded\": " + q(Instant.ofEpochMilli(h.createdAtMillis).toString()) + ",\n");
        out.write("  \"recordedMillis\": " + h.createdAtMillis + ",\n");
        out.write("  \"sensor\": " + q(t.name()) + ",\n");
        out.write("  \"unit\": " + q(t.getUnit()) + ",\n");
        out.write("  \"samplingRate\": " + q(h.rate.name()) + ",\n");
        out.write("  \"kind\": " + q(h.kind.name()) + ",\n");
        out.write("  \"experimentId\": " + q(h.experimentId) + ",\n");
        out.write("  \"experimentTitle\": " + q(h.experimentTitle) + ",\n");
        out.write("  \"durationSeconds\": " + ExportText.jsonNum(h.durationSeconds) + ",\n");
        out.write("  \"sampleCount\": " + sum.totalSamples + ",\n");
        out.write("  \"result\": " + q(h.resultText) + ",\n");

        out.write("  \"markers\": [");
        for (int i = 0; i < markers.size(); i++) {
            Marker m = markers.get(i);
            out.write((i == 0 ? "" : ", ") + "{\"time\": " + ExportText.jsonNum(m.getTimeSeconds())
                    + ", \"label\": " + q(m.getLabel()) + "}");
        }
        out.write("],\n");

        out.write("  \"summary\": [");
        for (int a = 0; a < axes; a++) {
            out.write((a == 0 ? "" : ", ") + "{\"axis\": " + q(ExportText.axisName(t, a))
                    + ", \"mean\": " + ExportText.jsonNum(sum.stats[a].getMean())
                    + ", \"stdDev\": " + ExportText.jsonNum(sum.stats[a].getStdDev())
                    + ", \"min\": " + ExportText.jsonNum(sum.stats[a].getMin())
                    + ", \"max\": " + ExportText.jsonNum(sum.stats[a].getMax()) + "}");
        }
        out.write("],\n");

        out.write("  \"columns\": [\"time\"");
        for (int a = 0; a < axes; a++) out.write(", " + q(ExportText.axisName(t, a)));
        out.write("],\n  \"samples\": [");

        double[] times = new double[CHUNK];
        float[][] values = new float[3][CHUNK];
        SampleSource.Cursor cursor = source.open();
        StringBuilder row = new StringBuilder();
        boolean first = true;
        int n;
        while ((n = cursor.read(times, values, CHUNK)) > 0) {
            for (int i = 0; i < n; i++) {
                row.setLength(0);
                row.append(first ? "\n    [" : ",\n    [").append(ExportText.jsonNum(times[i]));
                for (int a = 0; a < axes; a++) row.append(", ").append(ExportText.jsonNum(values[a][i]));
                row.append(']');
                out.write(row.toString());
                first = false;
            }
        }
        out.write(first ? "]\n}\n" : "\n  ]\n}\n");
        out.flush();
    }
}
