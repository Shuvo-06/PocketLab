package com.pocketlab.app.ui;

import android.content.Context;

import com.pocketlab.app.R;
import com.pocketlab.app.analysis.PeriodResult;
import com.pocketlab.app.analysis.SummaryStats;
import com.pocketlab.app.recording.RecordingSummary;
import com.pocketlab.app.sensor.SensorType;

import java.util.Locale;

/** Turns a {@link RecordingSummary} into display text. */
public final class SummaryText {

    private SummaryText() {}

    public static String format(Context ctx, SensorType type, RecordingSummary sum) {
        String fmt = "%." + type.getDecimals() + "f";
        StringBuilder sb = new StringBuilder(ctx.getString(R.string.analysis_header));
        for (RecordingSummary.Axis a : sum.getAxes()) {
            SummaryStats st = a.getStats();
            String sd = Double.isNaN(st.getStdDev()) ? "–" : String.format(Locale.US, fmt, st.getStdDev());
            sb.append('\n').append(ctx.getString(R.string.analysis_axis_line,
                    axisName(type, a.getIndex()),
                    String.format(Locale.US, fmt, st.getMean()), sd,
                    String.format(Locale.US, fmt, st.getMin()),
                    String.format(Locale.US, fmt, st.getMax()), type.getUnit()));
        }
        PeriodResult p = sum.getPeriod();
        if (p != null) {
            sb.append('\n').append(ctx.getString(R.string.analysis_period,
                    axisName(type, sum.getPeriodAxis()), p.getPeriodSeconds(),
                    p.getStdDevSeconds(), p.getPeriodCount(), p.getFrequencyHz()));
        }
        return sb.toString();
    }

    private static String axisName(SensorType type, int index) {
        return type.getAxes() > 1 ? type.getAxisLabel(index) : type.getDisplayName();
    }
}
