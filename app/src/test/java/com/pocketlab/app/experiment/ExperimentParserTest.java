package com.pocketlab.app.experiment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pocketlab.app.sensor.SensorType;

import org.json.JSONException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ExperimentParserTest {

    /** Unit tests run with the module directory (app/) as working directory. */
    static String bundledJson() throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/assets/experiments.json")), StandardCharsets.UTF_8);
    }

    @Test
    public void bundledFileParsesIntoFourExperiments() throws Exception {
        List<ExperimentDefinition> defs = ExperimentParser.parse(bundledJson());
        assertEquals(4, defs.size());
        assertEquals("pendulum", defs.get(0).getId());
        assertEquals(SensorType.ACCELEROMETER, defs.get(0).getSensor());
        assertEquals(1, defs.get(0).getInputs().size());
        assertEquals(9.80665, defs.get(0).getReferences().get(0).value, 1e-9);
        assertEquals(3, defs.get(0).getSteps().size());
        assertTrue(defs.get(2).getReferences().get(0).isRange());
        assertEquals(6.0, defs.get(3).param("windowSeconds", 0), 0);
        assertEquals(42.0, defs.get(3).param("missing", 42), 0);
    }

    private static String one(String sensor, String method, String extra) {
        return "{\"experiments\":[{\"id\":\"x\",\"title\":\"t\",\"summary\":\"s\",\"sensor\":\"" + sensor
                + "\",\"minSeconds\":5,\"maxSeconds\":10,\"method\":\"" + method + "\","
                + "\"steps\":[{\"title\":\"a\",\"text\":\"b\"}]" + extra + "}]}";
    }

    @Test
    public void validMinimalExperimentParses() throws Exception {
        assertEquals(1, ExperimentParser.parse(one("LIGHT", "LIGHT_LEVEL", "")).size());
    }

    @Test(expected = JSONException.class)
    public void unknownSensorRejected() throws Exception {
        ExperimentParser.parse(one("RADAR", "LIGHT_LEVEL", ""));
    }

    @Test(expected = JSONException.class)
    public void methodSensorMismatchRejected() throws Exception {
        ExperimentParser.parse(one("LIGHT", "PENDULUM_GRAVITY", ""));
    }

    @Test(expected = JSONException.class)
    public void pendulumWithoutLengthInputRejected() throws Exception {
        ExperimentParser.parse(one("ACCELEROMETER", "PENDULUM_GRAVITY", ""));
    }

    @Test(expected = JSONException.class)
    public void missingRequiredFieldRejected() throws Exception {
        ExperimentParser.parse("{\"experiments\":[{\"id\":\"x\"}]}");
    }

    @Test(expected = JSONException.class)
    public void emptyStepsRejected() throws Exception {
        ExperimentParser.parse("{\"experiments\":[{\"id\":\"x\",\"title\":\"t\",\"summary\":\"s\",\"sensor\":\"LIGHT\","
                + "\"minSeconds\":5,\"maxSeconds\":10,\"method\":\"LIGHT_LEVEL\",\"steps\":[]}]}");
    }

    @Test(expected = JSONException.class)
    public void duplicateIdsRejected() throws Exception {
        String e = "{\"id\":\"x\",\"title\":\"t\",\"summary\":\"s\",\"sensor\":\"LIGHT\",\"minSeconds\":5,"
                + "\"maxSeconds\":10,\"method\":\"LIGHT_LEVEL\",\"steps\":[{\"title\":\"a\",\"text\":\"b\"}]}";
        ExperimentParser.parse("{\"experiments\":[" + e + "," + e + "]}");
    }

    @Test(expected = JSONException.class)
    public void notJsonRejected() throws Exception {
        ExperimentParser.parse("hello");
    }

    @Test
    public void inputSpecValidation() throws Exception {
        ExperimentDefinition.InputSpec len = ExperimentParser.parse(bundledJson()).get(0).getInputs().get(0);
        assertEquals(1.25, len.parse(" 1,25 "), 1e-12);
        assertEquals(0.5, len.parse("0.5"), 1e-12);
        for (String bad : new String[]{"", "abc", "0.1", "5", "NaN"}) {
            try {
                len.parse(bad);
                throw new AssertionError("accepted: " + bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().length() > 0);
            }
        }
        ExperimentDefinition.InputSpec optional = ExperimentParser.parse(bundledJson()).get(3).getInputs().get(0);
        assertNull(optional.parse("  "));
        assertEquals(-3.0, optional.parse("-3"), 1e-12);
    }
}
