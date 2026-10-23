package com.pocketlab.app.export;

import com.pocketlab.app.sensor.SensorType;

import java.util.Locale;

/** Text helpers shared by the exporters. */
final class ExportText {

    private ExportText() {}

    /** Shortest exact text for a number; empty if it is not finite. */
    static String num(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? "" : Double.toString(v);
    }

    static String num(float v) {
        return Float.isNaN(v) || Float.isInfinite(v) ? "" : Float.toString(v);
    }

    /** JSON number or null. */
    static String jsonNum(double v) {
        return Double.isNaN(v) || Double.isInfinite(v) ? "null" : Double.toString(v);
    }

    static String jsonNum(float v) {
        return Float.isNaN(v) || Float.isInfinite(v) ? "null" : Float.toString(v);
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replace('\r', ' ').replace('\n', ' ');
    }

    /** Escapes text for an XML attribute or text node and drops characters XML 1.0 forbids. */
    static String xml(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                case '\t': case '\n': case '\r': sb.append(c); break;
                default:
                    if (c < 0x20 || c == 0xFFFE || c == 0xFFFF) sb.append(' ');
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    /** "x", "y", "z" for three-axis sensors, "value" otherwise. */
    static String axisName(SensorType t, int i) {
        return t.getAxes() > 1 ? t.getAxisLabel(i).toLowerCase(Locale.US) : "value";
    }
}
