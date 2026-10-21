package com.pocketlab.app.experiment;

import com.pocketlab.app.analysis.ErrorMetrics;
import com.pocketlab.app.analysis.LowPassFilter;
import com.pocketlab.app.analysis.PeriodEstimator;
import com.pocketlab.app.analysis.PeriodResult;
import com.pocketlab.app.analysis.PhysicsFormulas;
import com.pocketlab.app.analysis.Series;
import com.pocketlab.app.analysis.Stats;
import com.pocketlab.app.graph.SeriesBuffer;
import com.pocketlab.app.recording.RecordingSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs the analysis for an experiment on its recording. Pure Java: it only combines the
 * {@code analysis} building blocks. Results are estimates, never laboratory-grade values.
 */
public final class ExperimentAnalyzer {

    private ExperimentAnalyzer() {}

    /** One {@link Series} per sensor axis, from the recording's buffers. */
    public static List<Series> seriesFrom(RecordingSession session) {
        List<Series> out = new ArrayList<>();
        for (SeriesBuffer b : session.getSeries()) {
            int n = b.size();
            double[] t = new double[n], v = new double[n];
            for (int i = 0; i < n; i++) {
                t[i] = b.getTime(i);
                v[i] = b.getValue(i);
            }
            out.add(new Series(t, v));
        }
        return out;
    }

    public static ExperimentResult analyze(ExperimentDefinition def, Map<String, Double> inputs,
                                           List<Series> axes) {
        if (axes.isEmpty() || axes.get(0).size() < 2) {
            return ExperimentResult.failure("No data was recorded. Press Start and try again.");
        }
        double seconds = axes.get(0).duration();
        if (seconds < def.getMinSeconds()) {
            return ExperimentResult.failure(String.format(Locale.US,
                    "Recording too short: %.0f s recorded, at least %.0f s needed. Run it again for longer.",
                    seconds, def.getMinSeconds()));
        }
        switch (def.getMethod()) {
            case PENDULUM_GRAVITY: return pendulum(def, inputs, axes);
            case MAGNETIC_FIELD: return magnetic(def, axes);
            case LIGHT_LEVEL: return light(def, axes.get(0));
            case RELATIVE_HEIGHT: return height(def, inputs, axes.get(0));
            default: return ExperimentResult.failure("Unknown analysis method.");
        }
    }

    // ------------------------------------------------------------------ pendulum

    private static ExperimentResult pendulum(ExperimentDefinition def, Map<String, Double> inputs,
                                             List<Series> axes) {
        Double length = inputs.get("length");
        if (length == null) return ExperimentResult.failure("The pendulum length is missing.");

        Series raw = axes.get((int) def.param("axis", 0));
        double t0 = raw.time(0) + def.param("trimStartSeconds", 2);
        double t1 = raw.time(raw.size() - 1) - def.param("trimEndSeconds", 1);
        Series s = raw.slice(t0, t1);
        if (s.size() < 10) return ExperimentResult.failure("Not enough data after trimming. Record for longer.");

        // Zero-phase low-pass removes sensor noise without shifting the zero crossings.
        Series f = LowPassFilter.filterZeroPhase(s, def.param("cutoffHz", 3));
        double std = Stats.stdDev(f.valuesCopy());
        if (!(std > 0.05)) {
            return ExperimentResult.failure("The phone hardly moved. Release the pendulum after pressing Start.");
        }
        PeriodResult p = PeriodEstimator.fromZeroCrossings(f, 0.25 * std);
        if (p == null || p.getPeriodCount() < 3) {
            return ExperimentResult.failure("Fewer than 3 full swings were detected. "
                    + "Swing for longer, and check that the X axis points along the swing.");
        }

        double period = p.getPeriodSeconds();
        double g = PhysicsFormulas.gravityFromPendulum(length, period);
        // g ~ 1/T^2, so the relative uncertainty of g is twice that of T (standard error of the mean).
        double sem = p.getStdDevSeconds() / Math.sqrt(p.getPeriodCount());
        double dg = g * 2 * sem / period;
        double ref = def.getReferences().get(0).value;
        double err = ErrorMetrics.percentageError(g, ref);

        List<ExperimentResult.Line> lines = new ArrayList<>();
        lines.add(line("Length L", length, 3, "m"));
        lines.add(line("Period T", period, 3, "s"));
        lines.add(new ExperimentResult.Line("Swings measured", String.valueOf(p.getPeriodCount())));
        lines.add(new ExperimentResult.Line("Measured g", String.format(Locale.US, "%.2f ± %.2f m/s²", g, dg)));
        lines.add(line("Reference g", ref, 2, "m/s²"));
        lines.add(line("Percentage error", err, 1, "%"));

        String verdict;
        if (err < 3) verdict = "Excellent: within 3 % of the reference.";
        else if (err < 10) verdict = "Good: within 10 %. Remaining error usually comes from measuring L and from swing amplitude.";
        else verdict = "Far from the reference. Check that L runs from the pivot to the phone's centre, "
                + "that swings are small (under about 15°), and that the X axis points along the swing.";
        return ExperimentResult.success(lines, verdict);
    }

    // ------------------------------------------------------------------ magnetic field

    private static ExperimentResult magnetic(ExperimentDefinition def, List<Series> axes) {
        if (axes.size() < 3) return ExperimentResult.failure("Three magnetometer axes are required.");
        Series x = axes.get(0), y = axes.get(1), z = axes.get(2);
        int n = Math.min(x.size(), Math.min(y.size(), z.size()));
        double[] t = new double[n], mag = new double[n];
        for (int i = 0; i < n; i++) {
            t[i] = x.time(i);
            mag[i] = Math.sqrt(x.value(i) * x.value(i) + y.value(i) * y.value(i) + z.value(i) * z.value(i));
        }
        Series m = new Series(t, mag);
        Series baselineSeries = m.slice(t[0], t[0] + def.param("baselineSeconds", 5));
        if (baselineSeries.size() == 0) return ExperimentResult.failure("No baseline data was recorded.");
        double baseline = Stats.mean(baselineSeries.valuesCopy());
        double peak = Stats.max(mag), low = Stats.min(mag);

        List<ExperimentResult.Line> lines = new ArrayList<>();
        lines.add(line("Baseline field", baseline, 1, "µT"));
        lines.add(line("Peak field", peak, 1, "µT"));
        lines.add(line("Minimum field", low, 1, "µT"));
        lines.add(line("Largest change", peak - baseline, 1, "µT"));

        StringBuilder v = new StringBuilder();
        ExperimentDefinition.Reference ref = def.getReferences().isEmpty() ? null : def.getReferences().get(0);
        if (ref != null && ref.isRange()) {
            if (baseline >= ref.min && baseline <= ref.max) {
                v.append(String.format(Locale.US, "The baseline is inside the typical %.0f–%.0f µT range for Earth's field. ", ref.min, ref.max));
            } else {
                v.append(String.format(Locale.US, "The baseline is outside the typical %.0f–%.0f µT range: metal or electronics may be nearby, "
                        + "or the sensor needs calibrating (wave the phone in a figure 8). ", ref.min, ref.max));
            }
        }
        if (peak > 2 * baseline) {
            v.append(String.format(Locale.US, "A magnet was clearly detected (peak %.1f times the baseline).", peak / baseline));
        } else if (peak - baseline < 5) {
            v.append("No clear change was seen. Move the magnet slowly around the phone's top edge, where the sensor usually is.");
        } else {
            v.append("A moderate change was detected.");
        }
        return ExperimentResult.success(lines, v.toString().trim());
    }

    // ------------------------------------------------------------------ light

    private static ExperimentResult light(ExperimentDefinition def, Series s) {
        double[] v = s.valuesCopy();
        double mean = Stats.mean(v);
        double sd = Stats.stdDev(v);

        List<ExperimentResult.Line> lines = new ArrayList<>();
        lines.add(line("Mean illuminance", mean, 1, "lx"));
        lines.add(line("Minimum", Stats.min(v), 1, "lx"));
        lines.add(line("Maximum", Stats.max(v), 1, "lx"));
        lines.add(line("Variation (σ)", Double.isNaN(sd) ? 0 : sd, 1, "lx"));

        ExperimentDefinition.Reference match = null;
        for (ExperimentDefinition.Reference r : def.getReferences()) {
            if (r.contains(mean)) {
                match = r;
                break;
            }
        }
        String verdict = match == null
                ? "The mean is outside the listed reference levels."
                : String.format(Locale.US, "Closest reference: %s (%.0f–%.0f lx).", match.label, match.min, match.max);
        if (!Double.isNaN(sd) && mean > 0 && sd / mean > 0.2) {
            verdict += " The light varied a lot during the recording, so the mean is less reliable.";
        }
        return ExperimentResult.success(lines, verdict);
    }

    // ------------------------------------------------------------------ relative height

    private static ExperimentResult height(ExperimentDefinition def, Map<String, Double> inputs, Series s) {
        double w = def.param("windowSeconds", 6);
        double tStart = s.time(0), tEnd = s.time(s.size() - 1);
        if (tEnd - tStart < 2 * w) return ExperimentResult.failure("Recording too short for two separate hold periods.");
        double[] first = s.slice(tStart, tStart + w).valuesCopy();
        double[] last = s.slice(tEnd - w, tEnd).valuesCopy();
        if (first.length < 2 || last.length < 2) return ExperimentResult.failure("Too few readings at the start or end.");

        double p1 = Stats.mean(first), p2 = Stats.mean(last);
        double dh = PhysicsFormulas.heightChangeFromPressure(p1, p2);

        List<ExperimentResult.Line> lines = new ArrayList<>();
        lines.add(new ExperimentResult.Line("Start pressure", String.format(Locale.US, "%.2f ± %.2f hPa", p1, Stats.stdDev(first))));
        lines.add(new ExperimentResult.Line("End pressure", String.format(Locale.US, "%.2f ± %.2f hPa", p2, Stats.stdDev(last))));
        lines.add(line("Pressure change", p2 - p1, 2, "hPa"));
        lines.add(line("Estimated height change", dh, 1, "m"));

        StringBuilder v = new StringBuilder();
        Double known = inputs.get("knownHeight");
        if (known != null) {
            lines.add(line("Known height change", known, 1, "m"));
            double err = ErrorMetrics.percentageError(dh, known);
            if (!Double.isNaN(err)) lines.add(line("Percentage error", err, 1, "%"));
            v.append(String.format(Locale.US, "Compared with your known value, the estimate is off by %.1f m. ", ErrorMetrics.absoluteError(dh, known)));
        }
        if (Math.abs(p2 - p1) < 0.3) {
            v.append("The pressure change is close to the sensor's noise level (about 0.1 hPa, roughly 1 m). Use a larger height difference.");
        } else {
            v.append("Weather changes during the recording also shift the pressure, so keep the two holds close together in time.");
        }
        return ExperimentResult.success(lines, v.toString().trim());
    }

    private static ExperimentResult.Line line(String label, double value, int decimals, String unit) {
        return new ExperimentResult.Line(label, String.format(Locale.US, "%." + decimals + "f %s", value, unit));
    }
}
