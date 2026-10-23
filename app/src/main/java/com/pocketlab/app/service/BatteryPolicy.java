package com.pocketlab.app.service;

/**
 * Decides what to do about the battery while recording. Pure Java.
 * Warns once when the level falls to {@link #WARN_PERCENT}, stops and saves at
 * {@link #STOP_PERCENT}, and never acts while the phone is charging.
 */
public final class BatteryPolicy {

    public enum Decision { NONE, WARN, STOP_AND_SAVE }

    public static final int WARN_PERCENT = 15;
    public static final int STOP_PERCENT = 5;
    /** The warning can fire again after the level has recovered above this. */
    private static final int REARM_PERCENT = 20;

    private boolean warned;

    /** For a normal battery level update. A negative or over-100 percent is ignored. */
    public Decision onLevel(int percent, boolean charging) {
        if (percent < 0 || percent > 100) return Decision.NONE;
        if (charging) {
            warned = false;
            return Decision.NONE;
        }
        if (percent <= STOP_PERCENT) return Decision.STOP_AND_SAVE;
        if (percent <= WARN_PERCENT && !warned) {
            warned = true;
            return Decision.WARN;
        }
        if (percent > REARM_PERCENT) warned = false;
        return Decision.NONE;
    }

    /** For the system's own "battery low" broadcast (level may be unknown). */
    public Decision onSystemLow(boolean charging) {
        if (charging || warned) return Decision.NONE;
        warned = true;
        return Decision.WARN;
    }

    /** Percent from the BatteryManager extras, or -1 if unknown. */
    public static int percentOf(int level, int scale) {
        if (level < 0 || scale <= 0) return -1;
        return Math.min(100, level * 100 / scale);
    }
}
