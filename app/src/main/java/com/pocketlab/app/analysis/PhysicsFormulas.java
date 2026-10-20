package com.pocketlab.app.analysis;

/** Formulas used by the guided experiments. SI units unless stated. */
public final class PhysicsFormulas {

    /** Standard gravity, m/s². */
    public static final double STANDARD_GRAVITY = 9.80665;
    /** Standard sea-level pressure, hPa. */
    public static final double SEA_LEVEL_HPA = 1013.25;

    private PhysicsFormulas() {}

    /** Small-angle simple pendulum: g = 4 pi² L / T². */
    public static double gravityFromPendulum(double lengthMeters, double periodSeconds) {
        if (!(lengthMeters > 0) || !(periodSeconds > 0)) {
            throw new IllegalArgumentException("length and period must be > 0");
        }
        return 4 * Math.PI * Math.PI * lengthMeters / (periodSeconds * periodSeconds);
    }

    /** Small-angle simple pendulum: T = 2 pi sqrt(L / g). */
    public static double pendulumPeriod(double lengthMeters, double gravity) {
        if (!(lengthMeters > 0) || !(gravity > 0)) {
            throw new IllegalArgumentException("length and gravity must be > 0");
        }
        return 2 * Math.PI * Math.sqrt(lengthMeters / gravity);
    }

    /** Altitude in metres above the pressure-reference level (international barometric formula). */
    public static double altitudeFromPressure(double pressureHpa, double referenceHpa) {
        if (!(pressureHpa > 0) || !(referenceHpa > 0)) {
            throw new IllegalArgumentException("pressures must be > 0");
        }
        return 44330.0 * (1.0 - Math.pow(pressureHpa / referenceHpa, 0.190295));
    }

    /**
     * Height gained in metres when the pressure changes from {@code startHpa} to {@code endHpa}
     * (positive when the pressure drops). Assumes the weather stays constant.
     */
    public static double heightChangeFromPressure(double startHpa, double endHpa) {
        return altitudeFromPressure(endHpa, startHpa);
    }
}
