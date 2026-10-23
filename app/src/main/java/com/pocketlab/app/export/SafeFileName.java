package com.pocketlab.app.export;

/** Turns a session name into a safe file name. */
public final class SafeFileName {

    private SafeFileName() {}

    /** For example "Simple pendulum – 21 Oct, 14:32" and id 7 give "Simple_pendulum_21_Oct_14_32-7". */
    public static String of(String name, long id) {
        String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]+", "_");
        s = s.replaceAll("^[_.]+|[_.]+$", "");
        if (s.length() > 50) s = s.substring(0, 50).replaceAll("[_.]+$", "");
        if (s.isEmpty()) s = "recording";
        return s + "-" + id;
    }
}
