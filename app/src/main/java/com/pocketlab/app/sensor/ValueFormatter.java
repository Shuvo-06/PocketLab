package com.pocketlab.app.sensor;

import java.util.Locale;

/** Formats sensor readings for display: one line per axis, fixed decimals, signed. */
public final class ValueFormatter {

    private ValueFormatter() {}

    public static String format(SensorType type, float[] values) {
        String fmt = "%+10." + type.getDecimals() + "f";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < type.getAxes() && i < values.length; i++) {
            if (i > 0) sb.append('\n');
            if (type.getAxes() > 1) sb.append(type.getAxisLabel(i)).append("  ");
            sb.append(String.format(Locale.US, fmt, values[i]));
        }
        return sb.toString();
    }
}
