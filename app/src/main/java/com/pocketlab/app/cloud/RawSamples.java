package com.pocketlab.app.cloud;

/** The raw measurements of one recording, as downloaded from the cloud. {@code values} is [3][count]. */
public final class RawSamples {
    public final int count;
    public final double[] times;
    public final float[][] values;

    public RawSamples(int count, double[] times, float[][] values) {
        this.count = count;
        this.times = times;
        this.values = values;
    }
}
