package com.pocketlab.app.export;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SensorType;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.List;

/**
 * PocketLab's own XML format. It can be imported again with {@link XmlSessionReader}.
 * Samples are {@code <sample t="..." v0="..." v1="..." v2="..."/>} (one value per sensor axis).
 */
public final class XmlExporter {

    private static final int CHUNK = 1000;

    private XmlExporter() {}

    public static void write(ExportHeader h, List<Marker> markers, SampleSource source, Writer out)
            throws IOException {
        SensorType t = h.sensor;
        int axes = t.getAxes();

        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.write("<pocketlab-session version=\"1\" name=\"" + ExportText.xml(h.name)
                + "\" created=\"" + Instant.ofEpochMilli(h.createdAtMillis)
                + "\" createdMillis=\"" + h.createdAtMillis
                + "\" sensor=\"" + t.name() + "\" unit=\"" + ExportText.xml(t.getUnit())
                + "\" rate=\"" + h.rate.name() + "\" kind=\"" + h.kind.name()
                + "\" duration=\"" + ExportText.num(h.durationSeconds)
                + "\" samples=\"" + h.sampleCount + "\">\n");
        if (h.experimentId != null) {
            out.write("  <experiment id=\"" + ExportText.xml(h.experimentId) + "\" title=\""
                    + ExportText.xml(h.experimentTitle) + "\"/>\n");
        }
        if (h.resultText != null && !h.resultText.isEmpty()) {
            out.write("  <result>" + ExportText.xml(h.resultText) + "</result>\n");
        }
        out.write("  <markers>\n");
        for (Marker m : markers) {
            out.write("    <marker time=\"" + ExportText.num(m.getTimeSeconds()) + "\" label=\""
                    + ExportText.xml(m.getLabel()) + "\"/>\n");
        }
        out.write("  </markers>\n  <data>\n");

        double[] times = new double[CHUNK];
        float[][] values = new float[3][CHUNK];
        SampleSource.Cursor cursor = source.open();
        StringBuilder row = new StringBuilder();
        int n;
        while ((n = cursor.read(times, values, CHUNK)) > 0) {
            for (int i = 0; i < n; i++) {
                row.setLength(0);
                row.append("    <sample t=\"").append(ExportText.num(times[i])).append('"');
                for (int a = 0; a < axes; a++) {
                    row.append(" v").append(a).append("=\"").append(ExportText.num(values[a][i])).append('"');
                }
                row.append("/>\n");
                out.write(row.toString());
            }
        }
        out.write("  </data>\n</pocketlab-session>\n");
        out.flush();
    }
}
