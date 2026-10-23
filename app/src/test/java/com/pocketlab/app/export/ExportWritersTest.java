package com.pocketlab.app.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExportWritersTest {

    // ---------------- CSV ----------------

    @Test
    public void csvHasCommentHeaderColumnsAndOneRowPerSample() throws IOException {
        StringWriter w = new StringWriter();
        CsvExporter.write(ExportTestSupport.accelHeader("Run 1", 2500, "Measured g: 9.8 m/s²\n\nGood."),
                ExportTestSupport.markers(), ExportTestSupport.accelSource(2500), w);
        String[] lines = w.toString().split("\n");
        List<String> comments = new ArrayList<>(), data = new ArrayList<>();
        for (String l : lines) (l.startsWith("#") ? comments : data).add(l);

        assertTrue(comments.contains("# name: Run 1"));
        assertTrue(comments.contains("# unit: m/s²"));
        assertTrue(comments.contains("# samples: 2500"));
        assertTrue(comments.contains("# marker M2 at 1.25 s"));
        assertTrue(comments.contains("# result: Measured g: 9.8 m/s²"));
        assertTrue(comments.contains("# result: Good."));
        assertEquals("time_s,x,y,z", data.get(0));
        assertEquals(2501, data.size());                 // header + 2500 rows
        assertEquals("0.0,0.0,0.0,9.5", data.get(1));
        assertTrue(data.get(2500).startsWith("24.99"));            // 2499 * 0.01 prints as 24.990000000000002
        assertTrue(data.get(2500).endsWith(",2499.0,4998.0,9.5"));
        boolean meanLine = false;
        for (String c : comments) if (c.startsWith("# summary x: mean=1249.5 ")) meanLine = true;
        assertTrue(meanLine);
    }

    @Test
    public void csvSingleAxisUsesValueColumnAndNewlinesInNamesAreFlattened() throws IOException {
        ExportHeader h = new ExportHeader("two\nlines", 0, SensorType.LIGHT, SamplingRate.UI,
                SessionKind.FREE, null, null, 1, 2, null);
        StringWriter w = new StringWriter();
        CsvExporter.write(h, Collections.emptyList(), ExportTestSupport.source(
                new double[]{0, 1}, new float[][]{{100f, 200.5f}}), w);
        String s = w.toString();
        assertTrue(s.contains("# name: two lines\n"));
        assertTrue(s.contains("\ntime_s,value\n0.0,100.0\n1.0,200.5\n"));
    }

    // ---------------- JSON ----------------

    @Test
    public void jsonIsValidAndComplete() throws Exception {
        StringWriter w = new StringWriter();
        JsonExporter.write(ExportTestSupport.accelHeader("He said \"hi\" \\ ✓", 2500, "Result line"),
                ExportTestSupport.markers(), ExportTestSupport.accelSource(2500), w);
        JSONObject o = new JSONObject(w.toString());
        assertEquals("pocketlab-session", o.getString("format"));
        assertEquals("He said \"hi\" \\ ✓", o.getString("name"));
        assertEquals("ACCELEROMETER", o.getString("sensor"));
        assertEquals("pendulum", o.getString("experimentId"));
        assertEquals("Result line", o.getString("result"));
        assertEquals(2500, o.getInt("sampleCount"));
        assertEquals(2, o.getJSONArray("markers").length());
        assertEquals("M2", o.getJSONArray("markers").getJSONObject(1).getString("label"));
        assertEquals(4, o.getJSONArray("columns").length());
        JSONArray s = o.getJSONArray("samples");
        assertEquals(2500, s.length());
        assertEquals(24.99, s.getJSONArray(2499).getDouble(0), 1e-9);
        assertEquals(4998.0, s.getJSONArray(2499).getDouble(2), 1e-9);
        assertEquals(1249.5, o.getJSONArray("summary").getJSONObject(0).getDouble("mean"), 1e-9);
    }

    @Test
    public void jsonUsesNullForUndefinedValuesAndHandlesEmptyData() throws Exception {
        ExportHeader h = new ExportHeader("one", 0, SensorType.LIGHT, SamplingRate.UI,
                SessionKind.FREE, null, null, 0, 1, null);
        StringWriter w = new StringWriter();
        JsonExporter.write(h, Collections.emptyList(),
                ExportTestSupport.source(new double[]{0}, new float[][]{{5f}}), w);
        JSONObject o = new JSONObject(w.toString());
        assertTrue(o.getJSONArray("summary").getJSONObject(0).isNull("stdDev"));   // one sample
        assertTrue(o.isNull("experimentId"));

        StringWriter empty = new StringWriter();
        JsonExporter.write(h, Collections.emptyList(),
                ExportTestSupport.source(new double[0], new float[][]{new float[0]}), empty);
        assertEquals(0, new JSONObject(empty.toString()).getJSONArray("samples").length());
    }

    // ---------------- summary ----------------

    @Test
    public void summaryIsExactAndGraphPointsAreThinned() throws IOException {
        ExportSummary s = ExportSummary.compute(SensorType.ACCELEROMETER,
                ExportTestSupport.accelSource(2500), 2500, 500);
        assertEquals(2500, s.totalSamples);
        assertEquals(1249.5, s.stats[0].getMean(), 1e-9);
        assertEquals(9.5, s.stats[2].getMean(), 1e-6);
        assertTrue(s.graphCount >= 400 && s.graphCount <= 502);
        assertEquals(0.0, s.graphTimes[0], 0);

        ExportSummary none = ExportSummary.compute(SensorType.ACCELEROMETER,
                ExportTestSupport.accelSource(10), 10, 0);
        assertEquals(0, none.graphCount);
        assertEquals(10, none.totalSamples);
    }

    @Test
    public void safeFileNames() {
        assertEquals("Simple_pendulum_21_Oct_2026_14_32-7",
                SafeFileName.of("Simple pendulum – 21 Oct 2026, 14:32", 7));
        assertEquals("recording-3", SafeFileName.of("///", 3));
        assertEquals("recording-4", SafeFileName.of(null, 4));
        assertTrue(SafeFileName.of(new String(new char[200]).replace('\0', 'a'), 1).length() <= 53);
        assertTrue(!SafeFileName.of("../../etc/passwd", 1).contains("/"));
    }
}
