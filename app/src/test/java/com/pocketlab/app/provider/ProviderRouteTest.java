package com.pocketlab.app.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ProviderRouteTest {

    private static ProviderRoute parse(String... segments) {
        return ProviderRoute.parse(Arrays.asList(segments));
    }

    @Test
    public void knownRoutes() {
        assertEquals(ProviderRoute.Type.SESSIONS, parse("sessions").type);
        ProviderRoute one = parse("sessions", "12");
        assertEquals(ProviderRoute.Type.SESSION, one.type);
        assertEquals(12, one.sessionId);
        assertEquals(ProviderRoute.Type.SAMPLES, parse("sessions", "7", "samples").type);
        assertEquals(7, parse("sessions", "7", "samples").sessionId);
        assertEquals(ProviderRoute.Type.MARKERS, parse("sessions", "7", "markers").type);
    }

    @Test
    public void unknownRoutesAreRejected() {
        assertNull(ProviderRoute.parse(Collections.emptyList()));
        assertNull(ProviderRoute.parse(null));
        assertNull(parse("other"));
        assertNull(parse("sessions", "abc"));
        assertNull(parse("sessions", "0"));
        assertNull(parse("sessions", "-4"));
        assertNull(parse("sessions", "1", "secrets"));
        assertNull(parse("sessions", "1", "samples", "extra"));
        assertNull(parse("sessions", "99999999999999999999"));
    }

    @Test
    public void limitIsClamped() {
        assertEquals(1000, ProviderRoute.parseLimit(null, 1000, 10_000));
        assertEquals(50, ProviderRoute.parseLimit("50", 1000, 10_000));
        assertEquals(10_000, ProviderRoute.parseLimit("999999", 1000, 10_000));
        assertEquals(1000, ProviderRoute.parseLimit("0", 1000, 10_000));
        assertEquals(1000, ProviderRoute.parseLimit("-5", 1000, 10_000));
        assertEquals(1000, ProviderRoute.parseLimit("many", 1000, 10_000));
    }

    @Test
    public void afterParameter() {
        assertEquals(0, ProviderRoute.parseAfter(null));
        assertEquals(42, ProviderRoute.parseAfter("42"));
        assertEquals(0, ProviderRoute.parseAfter("-3"));
        assertEquals(0, ProviderRoute.parseAfter("x"));
    }
}
