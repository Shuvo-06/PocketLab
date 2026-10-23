package com.pocketlab.app.provider;

import java.util.List;

/** Decodes a provider URI path. Pure Java so it can be unit-tested. */
public final class ProviderRoute {

    public enum Type { SESSIONS, SESSION, SAMPLES, MARKERS }

    public final Type type;
    public final long sessionId;   // 0 for SESSIONS

    private ProviderRoute(Type type, long sessionId) {
        this.type = type;
        this.sessionId = sessionId;
    }

    /**
     * /sessions, /sessions/{id}, /sessions/{id}/samples, /sessions/{id}/markers.
     * @return null for anything else (including non-positive or non-numeric ids)
     */
    public static ProviderRoute parse(List<String> segments) {
        if (segments == null || segments.isEmpty() || !segments.get(0).equals("sessions")) return null;
        if (segments.size() == 1) return new ProviderRoute(Type.SESSIONS, 0);
        long id;
        try {
            id = Long.parseLong(segments.get(1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (id <= 0) return null;
        if (segments.size() == 2) return new ProviderRoute(Type.SESSION, id);
        if (segments.size() == 3) {
            if (segments.get(2).equals("samples")) return new ProviderRoute(Type.SAMPLES, id);
            if (segments.get(2).equals("markers")) return new ProviderRoute(Type.MARKERS, id);
        }
        return null;
    }

    /** A "limit" query parameter, clamped to 1..max; missing or invalid gives {@code fallback}. */
    public static int parseLimit(String value, int fallback, int max) {
        if (value == null) return fallback;
        try {
            int v = Integer.parseInt(value.trim());
            return v <= 0 ? fallback : Math.min(v, max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** An "after" (row id) query parameter; missing, invalid or negative gives 0. */
    public static long parseAfter(String value) {
        if (value == null) return 0;
        try {
            return Math.max(0, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
