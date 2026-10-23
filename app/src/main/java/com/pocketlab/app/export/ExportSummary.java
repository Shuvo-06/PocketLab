package com.pocketlab.app.export;

import com.pocketlab.app.analysis.RunningStats;
import com.pocketlab.app.sensor.SensorType;

import java.io.IOException;

/**
 * One streaming pass over a recording: statistics per axis (exact, over every sample) plus an
 * evenly thinned copy of the data for drawing a graph.
 */
public final class ExportSummary {

    public final RunningStats[] stats;
    public final double[] graphTimes;
    public final float[][] graphValues;
    public final int graphCount;
    public final long totalSamples;

    private ExportSummary(RunningStats[] stats, double[] graphTimes, float[][] graphValues,
                          int graphCount, long totalSamples) {
        this.stats = stats;
        this.graphTimes = graphTimes;
        this.graphValues = graphValues;
        this.graphCount = graphCount;
        this.totalSamples = totalSamples;
    }

    /**
     * @param expectedTotal  sample count used to choose the thinning step
     * @param maxGraphPoints roughly how many points to keep for the graph (0 = keep none)
     */
    public static ExportSummary compute(SensorType type, SampleSource source, long expectedTotal,
                                        int maxGraphPoints) throws IOException {
        int axes = type.getAxes();
        RunningStats[] stats = new RunningStats[axes];
        for (int a = 0; a < axes; a++) stats[a] = new RunningStats();

        int capacity = Math.max(0, maxGraphPoints) + 2;
        double[] gt = new double[capacity];
        float[][] gv = new float[axes][capacity];
        int gc = 0;
        long step = maxGraphPoints <= 0 ? Long.MAX_VALUE
                : Math.max(1, (expectedTotal + maxGraphPoints - 1) / maxGraphPoints);

        final int chunk = 1000;
        double[] times = new double[chunk];
        float[][] values = new float[3][chunk];
        SampleSource.Cursor cursor = source.open();
        long index = 0;
        int n;
        while ((n = cursor.read(times, values, chunk)) > 0) {
            for (int i = 0; i < n; i++, index++) {
                for (int a = 0; a < axes; a++) stats[a].add(values[a][i]);
                if (maxGraphPoints > 0 && index % step == 0 && gc < capacity) {
                    gt[gc] = times[i];
                    for (int a = 0; a < axes; a++) gv[a][gc] = values[a][i];
                    gc++;
                }
            }
        }
        return new ExportSummary(stats, gt, gv, gc, index);
    }
}
