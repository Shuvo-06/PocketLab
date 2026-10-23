package com.pocketlab.app.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.data.SessionKind;
import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

public class XmlSessionReaderTest {

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static final String ROOT = "<pocketlab-session version=\"1\" name=\"n\" sensor=\"LIGHT\" rate=\"UI\" samples=\"2\">";

    private static String doc(String body) {
        return "<?xml version=\"1.0\"?>" + ROOT + body + "</pocketlab-session>";
    }

    private static final String TWO = "<data><sample t=\"0.0\" v0=\"1.5\"/><sample t=\"0.5\" v0=\"2.5\"/></data>";

    private static String failureOf(String xml) {
        try {
            XmlSessionReader.read(stream(xml));
            throw new AssertionError("accepted: " + xml);
        } catch (ImportException e) {
            assertTrue(e.getMessage().length() > 0);
            return e.getMessage();
        } catch (IOException e) {
            throw new AssertionError("wrong exception type " + e);
        }
    }

    // ---------------- round trip ----------------

    @Test
    public void exportedFileImportsBackIdentically() throws Exception {
        String name = "Odd <name> & \"quotes\" 'x' ✓";
        ExportHeader h = new ExportHeader(name, 1_792_593_120_000L, SensorType.ACCELEROMETER,
                SamplingRate.GAME, SessionKind.EXPERIMENT, "pendulum", "Simple pendulum", 24.99, 2500,
                "Measured g: 9.8 m/s²\n\nGood <result> & more");
        StringWriter w = new StringWriter();
        XmlExporter.write(h, Arrays.asList(new Marker(0.5, "M<1>"), new Marker(1.25, "M2")),
                ExportTestSupport.accelSource(2500), w);

        ImportedSession s = XmlSessionReader.read(stream(w.toString()));
        assertEquals(name, s.name);
        assertEquals(1_792_593_120_000L, s.createdAtMillis);
        assertEquals(SensorType.ACCELEROMETER, s.sensor);
        assertEquals(SamplingRate.GAME, s.rate);
        assertEquals("pendulum", s.experimentId);
        assertEquals("Simple pendulum", s.experimentTitle);
        assertEquals(24.99, s.durationSeconds, 1e-12);
        assertEquals("Measured g: 9.8 m/s²\n\nGood <result> & more", s.resultText);
        assertEquals(2, s.markers.size());
        assertEquals("M<1>", s.markers.get(0).getLabel());
        assertEquals(1.25, s.markers.get(1).getTimeSeconds(), 0);
        assertEquals(2500, s.count);
        for (int i = 0; i < 2500; i += 499) {
            assertEquals(i * 0.01, s.times[i], 1e-12);
            assertEquals((float) i, s.values[0][i], 0);
            assertEquals(2f * i, s.values[1][i], 0);
            assertEquals(9.5f, s.values[2][i], 0);
        }
        assertEquals(2499.0f, s.values[0][2499], 0);
    }

    @Test
    public void minimalFileWithDefaults() throws Exception {
        ImportedSession s = XmlSessionReader.read(stream(
                "<pocketlab-session version=\"1\" sensor=\"LIGHT\" rate=\"UI\"><data>"
                        + "<sample t=\"0\" v0=\"1\"/><sample t=\"2\" v0=\"3\"/></data></pocketlab-session>"));
        assertEquals("Imported recording", s.name);
        assertEquals(2.0, s.durationSeconds, 0);       // derived from the times
        assertNull(s.resultText);
        assertNull(s.experimentId);
        assertEquals(0, s.markers.size());
    }

    @Test
    public void unknownElementsAreIgnored() throws Exception {
        ImportedSession s = XmlSessionReader.read(stream(doc("<extra foo=\"bar\"><deep/></extra>" + TWO)));
        assertEquals(2, s.count);
        assertNotNull(s);
    }

    // ---------------- rejection ----------------

    @Test
    public void notXmlAndWrongRoot() {
        failureOf("this is not xml");
        assertTrue(failureOf("<other/>").contains("not a PocketLab"));
        failureOf("");
        failureOf(doc(TWO).replace("</pocketlab-session>", ""));   // truncated
    }

    @Test
    public void versionSensorAndRateAreChecked() {
        assertTrue(failureOf(doc(TWO).replace("version=\"1\"", "version=\"2\"")).contains("version"));
        assertTrue(failureOf(doc(TWO).replace("LIGHT", "RADAR")).contains("RADAR"));
        assertTrue(failureOf(doc(TWO).replace("\"UI\"", "\"WARP\"")).contains("WARP"));
    }

    @Test
    public void doctypeAndEntityAttacksAreRejected() {
        failureOf("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"boom\">]>" + doc(TWO).substring(doc(TWO).indexOf("<pocketlab")));
        failureOf("<?xml version=\"1.0\"?><!DOCTYPE x SYSTEM \"file:///etc/passwd\">" + doc(TWO).substring(doc(TWO).indexOf("<pocketlab")));
        failureOf("<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;&lol;&lol;\">]>"
                + "<pocketlab-session version=\"1\" name=\"&lol2;\" sensor=\"LIGHT\" rate=\"UI\"><data><sample t=\"0\" v0=\"1\"/></data></pocketlab-session>");
    }

    @Test
    public void badNumbersAreRejected() {
        assertTrue(failureOf(doc("<data><sample t=\"0\" v0=\"NaN\"/><sample t=\"1\" v0=\"1\"/></data>")).contains("Invalid number"));
        assertTrue(failureOf(doc("<data><sample t=\"0\" v0=\"1\"/><sample t=\"abc\" v0=\"1\"/></data>")).contains("Invalid number"));
        assertTrue(failureOf(doc("<data><sample t=\"0\"/><sample t=\"1\" v0=\"1\"/></data>")).contains("Missing"));
        assertTrue(failureOf(doc("<data><sample t=\"0\" v0=\"1e999\"/><sample t=\"1\" v0=\"1\"/></data>")).contains("Invalid number"));
    }

    @Test
    public void timeMustNotGoBackwards() {
        assertTrue(failureOf(doc("<data><sample t=\"2\" v0=\"1\"/><sample t=\"1\" v0=\"1\"/></data>")).contains("back in time"));
    }

    @Test
    public void declaredCountMustMatchAndFileMustHaveSamples() {
        assertTrue(failureOf(doc(TWO).replace("samples=\"2\"", "samples=\"5\"")).contains("cut off"));
        assertTrue(failureOf(doc("<data/>").replace("samples=\"2\"", "samples=\"0\"")).contains("no samples"));
    }

    @Test
    public void limitsOnSamplesAndBytes() throws Exception {
        StringBuilder sb = new StringBuilder("<data>");
        for (int i = 0; i < 20; i++) sb.append("<sample t=\"").append(i).append("\" v0=\"1\"/>");
        String xml = "<pocketlab-session version=\"1\" sensor=\"LIGHT\" rate=\"UI\">" + sb + "</data></pocketlab-session>";
        try {
            XmlSessionReader.read(stream(xml), 10, XmlSessionReader.MAX_BYTES);
            throw new AssertionError("accepted too many samples");
        } catch (ImportException e) {
            assertTrue(e.getMessage().contains("Too many samples"));
        }
        try {
            XmlSessionReader.read(stream(xml), 100, 100);
            throw new AssertionError("accepted too large file");
        } catch (ImportException e) {
            assertTrue(e.getMessage().contains("larger than"));
        }
    }

    // ---------------- importer ----------------

    static class FakeStore implements com.pocketlab.app.data.SessionStore {
        final java.util.List<String> calls = new java.util.ArrayList<>();
        final java.util.List<Integer> chunks = new java.util.ArrayList<>();
        boolean failOnSamples;
        String result;
        long count;

        @Override public long insertSession(com.pocketlab.app.data.SessionHeader h) {
            calls.add("insert:" + h.kind + ":" + h.name);
            return 9;
        }
        @Override public void insertSamples(long id, double[] t, float[] a, float[] b, float[] c, int n) {
            if (failOnSamples) throw new IllegalStateException("disk full");
            calls.add("samples");
            chunks.add(n);
            assertEquals(n, t.length);
        }
        @Override public void finishSession(long id, double d, long n, String text, java.util.List<Marker> m) {
            calls.add("finish");
            result = text;
            count = n;
        }
        @Override public void deleteSession(long id) { calls.add("delete"); }
    }

    @Test
    public void importerSavesInChunksAsImportedKind() throws Exception {
        StringWriter w = new StringWriter();
        XmlExporter.write(ExportTestSupport.accelHeader("Big", 1200, "R"), Collections.emptyList(),
                ExportTestSupport.accelSource(1200), w);
        ImportedSession s = XmlSessionReader.read(stream(w.toString()));
        FakeStore store = new FakeStore();
        assertEquals(9, SessionImporter.save(s, store));
        assertEquals("insert:IMPORTED:Big", store.calls.get(0));
        assertEquals("finish", store.calls.get(store.calls.size() - 1));
        assertEquals(Arrays.asList(500, 500, 200), store.chunks);
        assertEquals(1200, store.count);
        assertEquals("R", store.result);
    }

    @Test
    public void importerCleansUpWhenSavingFails() throws Exception {
        StringWriter w = new StringWriter();
        XmlExporter.write(ExportTestSupport.accelHeader("Big", 600, null), Collections.emptyList(),
                ExportTestSupport.accelSource(600), w);
        ImportedSession s = XmlSessionReader.read(stream(w.toString()));
        FakeStore store = new FakeStore();
        store.failOnSamples = true;
        try {
            SessionImporter.save(s, store);
            throw new AssertionError("expected failure");
        } catch (IllegalStateException expected) {
            assertEquals("delete", store.calls.get(store.calls.size() - 1));
        }
    }
}
