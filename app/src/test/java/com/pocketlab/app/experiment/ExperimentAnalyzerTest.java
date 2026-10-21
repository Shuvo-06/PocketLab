package com.pocketlab.app.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.analysis.PhysicsFormulas;
import com.pocketlab.app.analysis.Series;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.sensor.SamplingRate;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ExperimentAnalyzerTest {

    private static ExperimentDefinition def(String id) throws Exception {
        for (ExperimentDefinition d : ExperimentParser.parse(ExperimentParserTest.bundledJson())) {
            if (d.getId().equals(id)) return d;
        }
        throw new AssertionError(id);
    }

    private interface Fn { double at(double t); }

    private static Series gen(double rate, double seconds, Fn f) {
        int n = (int) Math.round(rate * seconds) + 1;
        double[] t = new double[n], v = new double[n];
        for (int i = 0; i < n; i++) { t[i] = i / rate; v[i] = f.at(t[i]); }
        return new Series(t, v);
    }

    private static Map<String, Double> in(String k, double v) {
        Map<String, Double> m = new HashMap<>();
        m.put(k, v);
        return m;
    }

    private static String line(ExperimentResult r, String label) {
        for (ExperimentResult.Line l : r.getLines()) if (l.label.equals(label)) return l.value;
        throw new AssertionError("no line " + label);
    }

    // ---------------- pendulum ----------------

    @Test
    public void pendulumRecoversGravity() throws Exception {
        double L = 1.0, T = PhysicsFormulas.pendulumPeriod(L, PhysicsFormulas.STANDARD_GRAVITY);
        Random rnd = new Random(7);
        Series x = gen(50, 25, t -> 1.2 * Math.sin(2 * Math.PI * t / T) + (rnd.nextDouble() - 0.5) * 0.2);
        Series y = gen(50, 25, t -> 9.8 + 0.3 * Math.sin(4 * Math.PI * t / T)); // string axis: half period
        Series z = gen(50, 25, t -> 0.1);
        ExperimentResult r = ExperimentAnalyzer.analyze(def("pendulum"), in("length", L), Arrays.asList(x, y, z));
        assertTrue(r.getMessage(), r.isOk());
        assertTrue(line(r, "Measured g").startsWith("9.8"));
        assertTrue(Double.parseDouble(line(r, "Percentage error").split(" ")[0]) < 2.0);
        assertTrue(r.getMessage().startsWith("Excellent"));
    }

    @Test
    public void wrongLengthGivesLargeErrorAndAdvice() throws Exception {
        double T = PhysicsFormulas.pendulumPeriod(1.0, PhysicsFormulas.STANDARD_GRAVITY);
        Series x = gen(50, 25, t -> 1.2 * Math.sin(2 * Math.PI * t / T));
        Series flat = gen(50, 25, t -> 0);
        ExperimentResult r = ExperimentAnalyzer.analyze(def("pendulum"), in("length", 1.5), Arrays.asList(x, flat, flat));
        assertTrue(r.isOk());
        assertTrue(r.getMessage().startsWith("Far from"));
    }

    @Test
    public void pendulumFailures() throws Exception {
        double T = 2.0;
        Series x = gen(50, 25, t -> Math.sin(2 * Math.PI * t / T));
        Series flat = gen(50, 25, t -> 0);
        List<Series> axes = Arrays.asList(x, flat, flat);
        assertFalse(ExperimentAnalyzer.analyze(def("pendulum"), new HashMap<>(), axes).isOk());           // no length
        Series shortX = gen(50, 6, t -> Math.sin(2 * Math.PI * t / T));
        assertFalse(ExperimentAnalyzer.analyze(def("pendulum"), in("length", 1), Arrays.asList(shortX, flat, flat)).isOk());
        Series still = gen(50, 25, t -> 0.001);
        ExperimentResult r = ExperimentAnalyzer.analyze(def("pendulum"), in("length", 1), Arrays.asList(still, flat, flat));
        assertFalse(r.isOk());
        assertTrue(r.getMessage().contains("hardly moved"));
        assertFalse(ExperimentAnalyzer.analyze(def("pendulum"), in("length", 1), new ArrayList<>()).isOk());
    }

    // ---------------- magnetic ----------------

    @Test
    public void magnetDetected() throws Exception {
        Fn strength = t -> t < 8 ? 45 : (t < 14 ? 200 : 45);
        Series x = gen(50, 20, t -> strength.at(t) * 0.6);
        Series y = gen(50, 20, t -> strength.at(t) * 0.0);
        Series z = gen(50, 20, t -> strength.at(t) * 0.8);
        ExperimentResult r = ExperimentAnalyzer.analyze(def("magnetic"), new HashMap<>(), Arrays.asList(x, y, z));
        assertTrue(r.getMessage(), r.isOk());
        assertEquals("45.0 µT", line(r, "Baseline field"));
        assertEquals("200.0 µT", line(r, "Peak field"));
        assertTrue(r.getMessage().contains("inside the typical"));
        assertTrue(r.getMessage().contains("clearly detected"));
    }

    @Test
    public void noMagnetAndOddBaseline() throws Exception {
        Series x = gen(50, 20, t -> 100);
        Series z = gen(50, 20, t -> 0);
        ExperimentResult r = ExperimentAnalyzer.analyze(def("magnetic"), new HashMap<>(), Arrays.asList(x, z, z));
        assertTrue(r.getMessage().contains("outside the typical"));
        assertTrue(r.getMessage().contains("No clear change"));
    }

    // ---------------- light ----------------

    @Test
    public void lightClassified() throws Exception {
        ExperimentResult r = ExperimentAnalyzer.analyze(def("light"), new HashMap<>(), Arrays.asList(gen(5, 10, t -> 350)));
        assertTrue(r.isOk());
        assertEquals("350.0 lx", line(r, "Mean illuminance"));
        assertTrue(r.getMessage(), r.getMessage().contains("Office lighting"));
    }

    @Test
    public void unsteadyLightIsFlagged() throws Exception {
        ExperimentResult r = ExperimentAnalyzer.analyze(def("light"), new HashMap<>(),
                Arrays.asList(gen(5, 10, t -> ((int) (t * 5)) % 2 == 0 ? 100 : 400)));
        assertTrue(r.getMessage().contains("varied a lot"));
    }

    @Test
    public void tooShortLightFails() throws Exception {
        assertFalse(ExperimentAnalyzer.analyze(def("light"), new HashMap<>(), Arrays.asList(gen(5, 2, t -> 100))).isOk());
    }

    // ---------------- height ----------------

    @Test
    public void heightFromPressureDrop() throws Exception {
        Series p = gen(5, 30, t -> t < 10 ? 1013.25 : (t < 20 ? 1013.25 - (t - 10) * 0.1 : 1012.25));
        ExperimentResult r = ExperimentAnalyzer.analyze(def("height"), in("knownHeight", 8.0), Arrays.asList(p));
        assertTrue(r.getMessage(), r.isOk());
        double dh = Double.parseDouble(line(r, "Estimated height change").split(" ")[0]);
        assertEquals(8.3, dh, 0.2);
        assertTrue(Double.parseDouble(line(r, "Percentage error").split(" ")[0]) < 10);
        assertTrue(r.getMessage().contains("off by"));
    }

    @Test
    public void heightWithoutKnownValueAndNoise() throws Exception {
        Series p = gen(5, 30, t -> 1000.0);
        ExperimentResult r = ExperimentAnalyzer.analyze(def("height"), new HashMap<>(), Arrays.asList(p));
        assertTrue(r.isOk());
        assertTrue(r.getMessage().contains("noise level"));
        assertEquals(4, r.getLines().size());
    }

    // ---------------- from a real RecordingSession ----------------

    @Test
    public void seriesFromSessionMatchesSamples() throws Exception {
        RecordingSession s = new RecordingSession(SensorType.ACCELEROMETER, SamplingRate.GAME, 1000);
        s.start();
        for (int i = 0; i < 100; i++) {
            s.onSample(new SensorSample(SensorType.ACCELEROMETER, 1_000_000_000L + i * 20_000_000L,
                    new float[]{i, 2f, 3f}, 3));
        }
        List<Series> axes = ExperimentAnalyzer.seriesFrom(s);
        assertEquals(3, axes.size());
        assertEquals(100, axes.get(0).size());
        assertEquals(99.0, axes.get(0).value(99), 0);
        assertEquals(1.98, axes.get(0).duration(), 1e-9);
    }
}
