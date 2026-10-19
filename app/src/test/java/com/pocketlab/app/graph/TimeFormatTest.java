package com.pocketlab.app.graph;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeFormatTest {

    @Test
    public void formatsMinutesSecondsTenths() {
        assertEquals("00:00.0", TimeFormat.format(0));
        assertEquals("00:09.5", TimeFormat.format(9.5));
        assertEquals("01:05.3", TimeFormat.format(65.34));
    }

    @Test
    public void negativeClampsToZero() {
        assertEquals("00:00.0", TimeFormat.format(-4));
    }
}
