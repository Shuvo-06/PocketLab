package com.pocketlab.app.export;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SensorType;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;

/**
 * CSV: comment lines (starting with #) describing the recording and its summary, then a header
 * row and one row per sample. Spreadsheets show the comment lines as ordinary rows; pandas can
 * skip them with {@code comment='#'}. UTF-8.
 */
public final class CsvExporter {

    private static final int CHUNK = 1000;

    private CsvExporter() {}

    public static void write(ExportHeader h, List<Marker> markers, SampleSource source, Writer out)
            throws IOException {
        SensorType t = h.sensor;
        int axes = t.getAxes();
        ExportSummary sum = ExportSummary.compute(t, source, h.sampleCount, 0);

        out.write("# PocketLab export (CSV, format version 1)\n");
        out.write("# name: " + ExportText.oneLine(h.name) + "\n");
        out.write("# recorded: " + Instant.ofEpochMilli(h.createdAtMillis) + "\n");
        out.write("# sensor: " + t.getDisplayName() + "\n");
        out.write("# unit: " + t.getUnit() + "\n");
        out.write("# requested_rate: " + h.rate.name() + "\n");
        out.write("# duration_s: " + ExportText.num(h.durationSeconds) + "\n");
        out.write("# samples: " + sum.totalSamples + "\n");
        for (Marker m : markers) {
            out.write("# marker " + ExportText.oneLine(m.getLabel()) + " at " + ExportText.num(m.getTimeSeconds()) + " s\n");
        }
        for (int a = 0; a < axes; a++) {
            out.write("# summary " + ExportText.axisName(t, a)
                    + ": mean=" + ExportText.num(sum.stats[a].getMean())
                    + " std_dev=" + ExportText.num(sum.stats[a].getStdDev())
                    + " min=" + ExportText.num(sum.stats[a].getMin())
                    + " max=" + ExportText.num(sum.stats[a].getMax()) + "\n");
        }
        if (h.resultText != null) {
            for (String line : h.resultText.split("\n")) {
                if (!line.trim().isEmpty()) out.write("# result: " + ExportText.oneLine(line) + "\n");
            }
        }

        StringBuilder head = new StringBuilder("time_s");
        for (int a = 0; a < axes; a++) head.append(',').append(ExportText.axisName(t, a));
        out.write(head.append('\n').toString());

        double[] times = new double[CHUNK];
        float[][] values = new float[3][CHUNK];
        SampleSource.Cursor cursor = source.open();
        StringBuilder row = new StringBuilder();
        int n;
        while ((n = cursor.read(times, values, CHUNK)) > 0) {
            for (int i = 0; i < n; i++) {
                row.setLength(0);
                row.append(ExportText.num(times[i]));
                for (int a = 0; a < axes; a++) row.append(',').append(ExportText.num(values[a][i]));
                row.append('\n');
                out.write(row.toString());
            }
        }
        out.flush();
    }
}
