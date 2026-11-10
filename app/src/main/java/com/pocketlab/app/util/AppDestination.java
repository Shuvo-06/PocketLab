package com.pocketlab.app.util;

/**
 * Screens a notification (or any other Intent) can open. The {@code key} travels in an Intent extra;
 * {@link #fromKey} tolerates missing or unknown values so a stale notification can never crash the app.
 */
public enum AppDestination {
    SENSORS("sensors"),
    RECORDER("recorder"),
    EXPERIMENTS("experiments"),
    HISTORY("history"),
    CLOUD("cloud"),
    PROFILE("profile");

    public static final String EXTRA = "com.pocketlab.app.extra.DESTINATION";

    public final String key;

    AppDestination(String key) {
        this.key = key;
    }

    /** @return the destination, or null if {@code key} is null or unknown */
    public static AppDestination fromKey(String key) {
        if (key == null) return null;
        for (AppDestination d : values()) if (d.key.equals(key)) return d;
        return null;
    }
}
