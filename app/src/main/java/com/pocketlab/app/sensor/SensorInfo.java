package com.pocketlab.app.sensor;

import android.hardware.Sensor;

/** Static hardware facts about a sensor. */
public final class SensorInfo {

    private final String name;
    private final String vendor;
    private final float maxRange;
    private final float resolution;
    private final int minDelayUs;

    private SensorInfo(String name, String vendor, float maxRange, float resolution, int minDelayUs) {
        this.name = name;
        this.vendor = vendor;
        this.maxRange = maxRange;
        this.resolution = resolution;
        this.minDelayUs = minDelayUs;
    }

    static SensorInfo from(Sensor s) {
        return new SensorInfo(s.getName(), s.getVendor(), s.getMaximumRange(),
                s.getResolution(), s.getMinDelay());
    }

    public String getName() { return name; }
    public String getVendor() { return vendor; }
    public float getMaxRange() { return maxRange; }
    public float getResolution() { return resolution; }
    /** Smallest period in µs; 0 = on-change sensor, negative = one-shot. */
    public int getMinDelayUs() { return minDelayUs; }
}
