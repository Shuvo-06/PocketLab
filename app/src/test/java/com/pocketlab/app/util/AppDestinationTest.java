package com.pocketlab.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class AppDestinationTest {

    @Test
    public void everyKeyRoundTrips() {
        for (AppDestination d : AppDestination.values()) assertEquals(d, AppDestination.fromKey(d.key));
    }

    @Test
    public void unknownOrMissingKeysGiveNullNotACrash() {
        assertNull(AppDestination.fromKey(null));
        assertNull(AppDestination.fromKey(""));
        assertNull(AppDestination.fromKey("deleted-screen"));
        assertNull(AppDestination.fromKey("HISTORY"));      // keys are lower case
    }

    @Test
    public void keysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (AppDestination d : AppDestination.values()) assertTrue(keys.add(d.key));
    }
}
