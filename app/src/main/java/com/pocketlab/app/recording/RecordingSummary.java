package com.pocketlab.app.recording;

import com.pocketlab.app.analysis.PeriodEstimator;
import com.pocketlab.app.analysis.PeriodResult;
import com.pocketlab.app.analysis.Series;
import com.pocketlab.app.analysis.SummaryStats;
import com.pocketlab.app.graph.SeriesBuffer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Statistics for a finished recording, computed from the samples still held in the graph
 * buffer (the newest ones if the recording was longer than the buffer).
 * Includes a period estimate for the axis that varies the most, when it oscillates.
 */
public final class RecordingSummary {

    /** Stats for one axis. */
    public static final class Axis {
        private final int index;
        private final SummaryStats stats;

        Axis(int index, SummaryStats stats) {
            this.index = index;
            this.stats = stats;
        }

        public int getIndex() { return index; }
        public SummaryStats getStats() { return stats; }
    }

    private final List<Axis> axes;
    private final PeriodResult period;
    private final int periodAxis;
    private final int samplesUsed;
    private final boolean truncated;

    private RecordingSummary(List<Axis> axes, PeriodResult period, int periodAxis,
                             int samplesUsed, boolean truncated) {
        this.axes = Collections.unmodifiableList(axes);
        this.period = period;
        this.periodAxis = periodAxis;
        this.samplesUsed = samplesUsed;
        this.truncated = truncated;
    }

    public static RecordingSummary of(RecordingSession session) {
        List<SeriesBuffer> buffers = session.getSeries();
        int n = buffers.get(0).size();
        if (n == 0) {
            return new RecordingSummary(new ArrayList<>(), null, -1, 0, false);
        }
        List<Axis> axes = new ArrayList<>();
        Series bestSeries = null;
        double bestStd = 0;
        int bestAxis = -1;
        for (int k = 0; k < buffers.size(); k++) {
            SeriesBuffer b = buffers.get(k);
            double[] t = new double[n];
            double[] v = new double[n];
            for (int i = 0; i < n; i++) {
                t[i] = b.getTime(i);
                v[i] = b.getValue(i);
            }
            SummaryStats st = SummaryStats.of(v);
            axes.add(new Axis(k, st));
            if (!Double.isNaN(st.getStdDev()) && st.getStdDev() > bestStd) {
                bestStd = st.getStdDev();
                bestSeries = new Series(t, v);
                bestAxis = k;
            }
        }
        PeriodResult p = null;
        if (bestSeries != null && n >= 10) {
            // Noise band = 25 % of the spread, so jitter does not create false cycles.
            p = PeriodEstimator.fromZeroCrossings(bestSeries, 0.25 * bestStd);
            if (p != null && p.getPeriodCount() < 2) p = null;
        }
        return new RecordingSummary(axes, p, p == null ? -1 : bestAxis, n, session.isGraphTruncated());
    }

    public List<Axis> getAxes() { return axes; }
    /** Null if the signal does not oscillate clearly enough. */
    public PeriodResult getPeriod() { return period; }
    public int getPeriodAxis() { return periodAxis; }
    public int getSamplesUsed() { return samplesUsed; }
    /** True if the recording was longer than the graph buffer, so only the newest samples were used. */
    public boolean isTruncated() { return truncated; }
}
