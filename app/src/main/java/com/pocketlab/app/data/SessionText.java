package com.pocketlab.app.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Small text helpers for history. */
public final class SessionText {

    private SessionText() {}

    /** For example "Simple pendulum – 21 Oct 2026, 14:32". */
    public static String defaultName(String label, long millis, Locale locale, TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat("d MMM yyyy, HH:mm", locale);
        f.setTimeZone(zone);
        return label + " – " + f.format(new Date(millis));
    }

    /** Escapes % _ and \ so user text is matched literally by SQL LIKE ... ESCAPE '\'. */
    public static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
