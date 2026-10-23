package com.pocketlab.app.service;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BatteryPolicyTest {

    private static final BatteryPolicy.Decision NONE = BatteryPolicy.Decision.NONE;
    private static final BatteryPolicy.Decision WARN = BatteryPolicy.Decision.WARN;
    private static final BatteryPolicy.Decision STOP = BatteryPolicy.Decision.STOP_AND_SAVE;

    @Test
    public void healthyBatteryDoesNothing() {
        BatteryPolicy p = new BatteryPolicy();
        assertEquals(NONE, p.onLevel(100, false));
        assertEquals(NONE, p.onLevel(50, false));
        assertEquals(NONE, p.onLevel(16, false));
    }

    @Test
    public void warnsOnceThenStopsAtCriticalLevel() {
        BatteryPolicy p = new BatteryPolicy();
        assertEquals(WARN, p.onLevel(15, false));
        assertEquals(NONE, p.onLevel(14, false));      // already warned
        assertEquals(NONE, p.onLevel(10, false));
        assertEquals(STOP, p.onLevel(5, false));
        assertEquals(STOP, p.onLevel(3, false));
    }

    @Test
    public void goingStraightToCriticalStopsWithoutWarning() {
        assertEquals(STOP, new BatteryPolicy().onLevel(4, false));
    }

    @Test
    public void chargingNeverActsAndRearmsTheWarning() {
        BatteryPolicy p = new BatteryPolicy();
        assertEquals(WARN, p.onLevel(12, false));
        assertEquals(NONE, p.onLevel(3, true));        // plugged in
        assertEquals(WARN, p.onLevel(12, false));      // unplugged again: warn again
    }

    @Test
    public void warningRearmsAfterRecovery() {
        BatteryPolicy p = new BatteryPolicy();
        assertEquals(WARN, p.onLevel(14, false));
        assertEquals(NONE, p.onLevel(18, false));      // still inside the hysteresis band
        assertEquals(NONE, p.onLevel(14, false));
        assertEquals(NONE, p.onLevel(25, false));      // recovered
        assertEquals(WARN, p.onLevel(14, false));
    }

    @Test
    public void systemLowBroadcastWarnsOnceUnlessCharging() {
        BatteryPolicy p = new BatteryPolicy();
        assertEquals(WARN, p.onSystemLow(false));
        assertEquals(NONE, p.onSystemLow(false));
        assertEquals(NONE, new BatteryPolicy().onSystemLow(true));
        BatteryPolicy q = new BatteryPolicy();
        q.onSystemLow(false);
        assertEquals(NONE, q.onLevel(14, false));      // the broadcast already warned
    }

    @Test
    public void invalidLevelsAreIgnored() {
        BatteryPolicy p = new BatteryPolicy();
        assertEquals(NONE, p.onLevel(-1, false));
        assertEquals(NONE, p.onLevel(101, false));
    }

    @Test
    public void percentOfHandlesUnknownAndRounding() {
        assertEquals(50, BatteryPolicy.percentOf(50, 100));
        assertEquals(33, BatteryPolicy.percentOf(1, 3));
        assertEquals(100, BatteryPolicy.percentOf(120, 100));
        assertEquals(-1, BatteryPolicy.percentOf(-1, 100));
        assertEquals(-1, BatteryPolicy.percentOf(50, 0));
    }
}
