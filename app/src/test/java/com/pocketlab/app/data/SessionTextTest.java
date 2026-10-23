package com.pocketlab.app.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;
import java.util.TimeZone;

public class SessionTextTest {

    @Test
    public void defaultNameUsesLabelAndLocalDate() {
        // 2026-10-21 14:32:00 UTC
        long millis = 1_792_593_120_000L;
        assertEquals("Simple pendulum – 21 Oct 2026, 14:32",
                SessionText.defaultName("Simple pendulum", millis, Locale.US, TimeZone.getTimeZone("UTC")));
        assertEquals("Light sensor – 21 Oct 2026, 20:32",
                SessionText.defaultName("Light sensor", millis, Locale.US, TimeZone.getTimeZone("Asia/Dhaka")));
    }

    @Test
    public void escapeLikeMakesWildcardsLiteral() {
        assertEquals("plain", SessionText.escapeLike("plain"));
        assertEquals("100\\% pure\\_", SessionText.escapeLike("100% pure_"));
        assertEquals("a\\\\b", SessionText.escapeLike("a\\b"));
    }
}
