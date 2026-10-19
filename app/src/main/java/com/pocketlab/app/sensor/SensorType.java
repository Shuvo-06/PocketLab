package com.pocketlab.app.sensor;

import android.hardware.Sensor;

/**
 * The sensors PocketLab supports. Plain metadata; {@link SensorHub} maps each
 * entry to a real SensorManager sensor.
 */
public enum SensorType {

    ACCELEROMETER("Accelerometer", "m/s²", 3, 3, Sensor.TYPE_ACCELEROMETER,
            "Acceleration including gravity"),
    GYROSCOPE("Gyroscope", "rad/s", 3, 4, Sensor.TYPE_GYROSCOPE,
            "Angular velocity around each axis"),
    MAGNETOMETER("Magnetometer", "µT", 3, 2, Sensor.TYPE_MAGNETIC_FIELD,
            "Magnetic field strength"),
    LIGHT("Light sensor", "lx", 1, 1, Sensor.TYPE_LIGHT,
            "Ambient illuminance"),
    BAROMETER("Barometer", "hPa", 1, 2, Sensor.TYPE_PRESSURE,
            "Atmospheric pressure");

    private static final String[] AXIS_LABELS = {"X", "Y", "Z"};

    private final String displayName;
    private final String unit;
    private final int axes;
    private final int decimals;
    private final int androidType;
    private final String description;

    SensorType(String displayName, String unit, int axes, int decimals,
               int androidType, String description) {
        this.displayName = displayName;
        this.unit = unit;
        this.axes = axes;
        this.decimals = decimals;
        this.androidType = androidType;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getUnit() { return unit; }
    public int getAxes() { return axes; }
    /** Sensible number of decimals when displaying a value. */
    public int getDecimals() { return decimals; }
    /** The {@code Sensor.TYPE_*} constant. */
    public int getAndroidType() { return androidType; }
    public String getDescription() { return description; }

    /** "X", "Y", "Z" for multi-axis sensors, empty for single-value sensors. */
    public String getAxisLabel(int index) {
        return axes > 1 ? AXIS_LABELS[index] : "";
    }
}
