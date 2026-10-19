package com.pocketlab.app.graph;

import java.util.Locale;

/** Formats elapsed seconds as mm:ss.t. */
public final class TimeFormat {

    private TimeFormat() {}

    public static String format(double seconds) {
        long tenths = Math.round(Math.max(0, seconds) * 10);
        long minutes = tenths / 600;
        long secs = (tenths / 10) % 60;
        long tenth = tenths % 10;
        return String.format(Locale.US, "%02d:%02d.%d", minutes, secs, tenth);
    }
}
