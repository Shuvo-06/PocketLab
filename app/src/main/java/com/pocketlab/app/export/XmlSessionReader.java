package com.pocketlab.app.export;

import com.pocketlab.app.graph.Marker;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorType;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

/**
 * Reads a PocketLab XML session with a streaming (SAX) parser and validates everything:
 * root element and version, sensor and rate names, every number finite, times not going
 * backwards, declared and actual sample counts equal, and hard limits on file size and
 * sample count. A DOCTYPE is rejected outright, which blocks XML entity attacks.
 * Every problem is reported as an {@link ImportException} with a readable message.
 */
public final class XmlSessionReader {

    public static final int MAX_SAMPLES = 500_000;
    public static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int MAX_NAME = 80;
    private static final int MAX_RESULT = 20_000;
    private static final int MAX_MARKERS = 1000;

    private XmlSessionReader() {}

    public static ImportedSession read(InputStream in) throws IOException {
        return read(in, MAX_SAMPLES, MAX_BYTES);
    }

    static ImportedSession read(InputStream in, int maxSamples, long maxBytes) throws IOException {
        Handler h = new Handler(maxSamples);
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(false);
            // Best effort; not every platform supports these, the lexical handler below always applies.
            try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) { }
            try { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); } catch (Exception ignored) { }
            XMLReader reader = f.newSAXParser().getXMLReader();
            reader.setContentHandler(h);
            reader.setErrorHandler(h);
            try { reader.setProperty("http://xml.org/sax/properties/lexical-handler", h); } catch (SAXException ignored) { }
            reader.parse(new InputSource(new LimitedInputStream(in, maxBytes)));
        } catch (SAXException e) {
            throw unwrap(e);
        } catch (ParserConfigurationException e) {
            throw new ImportException("This device cannot read XML files.");
        }
        return h.result();
    }

    private static ImportException unwrap(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ImportException) return (ImportException) t;
        }
        return new ImportException("This is not a valid XML file.");
    }

    private static SAXException fail(String message) {
        return new SAXException(new ImportException(message));
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long max;
        private long total;

        LimitedInputStream(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        private void count(long n) throws ImportException {
            if (n > 0) total += n;
            if (total > max) throw new ImportException("The file is larger than " + (max / (1024 * 1024)) + " MB.");
        }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            count(n);
            return n;
        }
    }

    private static final class Handler extends DefaultHandler implements LexicalHandler {
        private final int maxSamples;
        private int depth;
        private boolean rootSeen;

        private String name = "";
        private long createdMillis = System.currentTimeMillis();
        private SensorType sensor;
        private SamplingRate rate;
        private String experimentId, experimentTitle;
        private double duration = Double.NaN;
        private long declaredSamples = -1;
        private final StringBuilder resultText = new StringBuilder();
        private boolean inResult;
        private final List<Marker> markers = new ArrayList<>();

        private int count;
        private double[] times = new double[1024];
        private final float[][] values = new float[3][1024];
        private double lastTime = Double.NEGATIVE_INFINITY;
        private double firstTime;

        Handler(int maxSamples) {
            this.maxSamples = maxSamples;
        }

        // ---- reject DTDs (entity declarations) ----
        @Override public void startDTD(String n, String publicId, String systemId) throws SAXException {
            throw fail("Files with a DOCTYPE are not accepted.");
        }
        @Override public void endDTD() { }
        @Override public void startEntity(String n) { }
        @Override public void endEntity(String n) { }
        @Override public void startCDATA() { }
        @Override public void endCDATA() { }
        @Override public void comment(char[] ch, int start, int length) { }

        @Override
        public void startElement(String uri, String local, String q, Attributes a) throws SAXException {
            if (depth == 0) {
                if (rootSeen || !q.equals("pocketlab-session")) throw fail("This is not a PocketLab session file.");
                rootSeen = true;
                readRoot(a);
            } else if (q.equals("sample")) {
                readSample(a);
            } else if (q.equals("marker")) {
                readMarker(a);
            } else if (q.equals("experiment")) {
                experimentId = clip(a.getValue("id"), 100);
                experimentTitle = clip(a.getValue("title"), 100);
            } else if (q.equals("result")) {
                inResult = true;
                resultText.setLength(0);
            }
            depth++;
        }

        @Override
        public void endElement(String uri, String local, String q) {
            depth--;
            if (q.equals("result")) inResult = false;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (inResult && resultText.length() < MAX_RESULT) {
                resultText.append(ch, start, Math.min(length, MAX_RESULT - resultText.length()));
            }
        }

        private void readRoot(Attributes a) throws SAXException {
            if (!"1".equals(a.getValue("version"))) {
                throw fail("Unsupported file version. This app reads version 1.");
            }
            String n = clip(a.getValue("name"), MAX_NAME);
            name = n == null || n.trim().isEmpty() ? "Imported recording" : n.trim();
            try {
                sensor = SensorType.valueOf(String.valueOf(a.getValue("sensor")));
            } catch (IllegalArgumentException e) {
                throw fail("Unknown sensor \"" + a.getValue("sensor") + "\".");
            }
            try {
                rate = SamplingRate.valueOf(String.valueOf(a.getValue("rate")));
            } catch (IllegalArgumentException e) {
                throw fail("Unknown sampling rate \"" + a.getValue("rate") + "\".");
            }
            String c = a.getValue("createdMillis");
            if (c != null) {
                try {
                    createdMillis = Long.parseLong(c.trim());
                } catch (NumberFormatException e) {
                    throw fail("The recording date is not valid.");
                }
            }
            String d = a.getValue("duration");
            if (d != null) duration = number(d, "duration");
            String s = a.getValue("samples");
            if (s != null) {
                try {
                    declaredSamples = Long.parseLong(s.trim());
                } catch (NumberFormatException e) {
                    throw fail("The sample count is not valid.");
                }
                if (declaredSamples < 0 || declaredSamples > maxSamples) {
                    throw fail("Too many samples (the limit is " + maxSamples + ").");
                }
            }
        }

        private void readSample(Attributes a) throws SAXException {
            if (sensor == null) throw fail("This is not a PocketLab session file.");
            if (count >= maxSamples) throw fail("Too many samples (the limit is " + maxSamples + ").");
            double t = number(a.getValue("t"), "time of sample " + (count + 1));
            if (t < lastTime) throw fail("Sample " + (count + 1) + " goes back in time.");
            if (count == 0) firstTime = t;
            if (count == times.length) grow();
            times[count] = t;
            for (int k = 0; k < sensor.getAxes(); k++) {
                values[k][count] = (float) number(a.getValue("v" + k), "value v" + k + " of sample " + (count + 1));
            }
            lastTime = t;
            count++;
        }

        private void readMarker(Attributes a) throws SAXException {
            if (markers.size() >= MAX_MARKERS) throw fail("Too many markers.");
            double t = number(a.getValue("time"), "marker time");
            String label = clip(a.getValue("label"), 40);
            markers.add(new Marker(t, label == null || label.isEmpty() ? "M" + (markers.size() + 1) : label));
        }

        private double number(String s, String what) throws SAXException {
            if (s == null) throw fail("Missing " + what + ".");
            try {
                double v = Double.parseDouble(s.trim());
                if (Double.isNaN(v) || Double.isInfinite(v)) throw new NumberFormatException();
                return v;
            } catch (NumberFormatException e) {
                throw fail("Invalid number for " + what + ".");
            }
        }

        private static String clip(String s, int max) {
            if (s == null) return null;
            s = s.trim();
            return s.length() > max ? s.substring(0, max) : s;
        }

        private void grow() {
            int n = times.length * 2;
            times = Arrays.copyOf(times, n);
            for (int k = 0; k < 3; k++) values[k] = Arrays.copyOf(values[k], n);
        }

        ImportedSession result() throws ImportException {
            if (!rootSeen) throw new ImportException("This is not a PocketLab session file.");
            if (count == 0) throw new ImportException("The file contains no samples.");
            if (declaredSamples >= 0 && declaredSamples != count) {
                throw new ImportException("The file says it has " + declaredSamples + " samples but contains "
                        + count + ". It may be cut off.");
            }
            double dur = Double.isNaN(duration) || duration < 0 ? lastTime - firstTime : duration;
            String result = resultText.toString().trim();
            return new ImportedSession(name, createdMillis, sensor, rate, experimentId, experimentTitle,
                    dur, result.isEmpty() ? null : result, markers, count, times, values);
        }
    }
}
