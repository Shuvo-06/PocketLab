package com.pocketlab.app.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValueFormatterTest {

    @Test
    public void threeAxisSensorGivesThreeLabelledLines() {
        String s = ValueFormatter.format(SensorType.ACCELEROMETER, new float[]{1f, -2f, 3f});
        String[] lines = s.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("X"));
        assertTrue(lines[0].endsWith("+1.000"));
        assertTrue(lines[1].endsWith("-2.000"));
        assertTrue(lines[2].startsWith("Z"));
    }

    @Test
    public void singleValueSensorHasNoAxisLabel() {
        assertEquals("+123.5", ValueFormatter.format(SensorType.LIGHT, new float[]{123.46f}).trim());
    }
}
