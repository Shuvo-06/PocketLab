package com.pocketlab.app.sensor;

/**
 * User-selectable sampling rates. The period is a <em>hint</em> to Android: the
 * system may deliver faster or slower, so always measure the real rate from
 * sensor timestamps (see {@link RateEstimator}).
 */
public enum SamplingRate {

    NORMAL("Normal", 200_000),   // ~5 Hz
    UI("UI", 66_667),            // ~15 Hz
    GAME("Game", 20_000),        // ~50 Hz
    FASTEST("Fastest", 0);       // device maximum

    private final String label;
    private final int periodUs;

    SamplingRate(String label, int periodUs) {
        this.label = label;
        this.periodUs = periodUs;
    }

    public String getLabel() { return label; }

    /** Requested period in microseconds (0 = as fast as possible). */
    public int getPeriodUs() { return periodUs; }

    /** Requested rate in Hz, or 0 when the device maximum is requested. */
    public double getNominalHz() {
        return periodUs == 0 ? 0 : 1_000_000.0 / periodUs;
    }
}
