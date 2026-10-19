package com.pocketlab.app.sensor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class SensorTypeTest {

    @Test
    public void everySensorHasUnitAndPositiveAxes() {
        for (SensorType type : SensorType.values()) {
            assertTrue(type.getDisplayName() + " unit", !type.getUnit().isEmpty());
            assertTrue(type.getDisplayName() + " axes", type.getAxes() >= 1);
        }
    }

    @Test
    public void androidTypesAreUnique() {
        Set<Integer> seen = new HashSet<>();
        for (SensorType type : SensorType.values()) {
            assertTrue("duplicate type for " + type, seen.add(type.getAndroidType()));
        }
    }

    @Test
    public void fiveSensorsSupported() {
        assertEquals(5, SensorType.values().length);
    }

    @Test
    public void axisLabels() {
        assertEquals("Y", SensorType.ACCELEROMETER.getAxisLabel(1));
        assertEquals("", SensorType.LIGHT.getAxisLabel(0));
    }
}
