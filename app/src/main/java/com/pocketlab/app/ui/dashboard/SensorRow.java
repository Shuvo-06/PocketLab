package com.pocketlab.app.ui.dashboard;

import androidx.annotation.Nullable;

import com.pocketlab.app.sensor.RateEstimator;
import com.pocketlab.app.sensor.SensorInfo;
import com.pocketlab.app.sensor.SensorSample;
import com.pocketlab.app.sensor.SensorType;

/** UI state for one sensor card. Mutated from sensor callbacks, read by the adapter (main thread). */
class SensorRow {
    final SensorType type;
    @Nullable final SensorInfo info;
    @Nullable SensorSample latest;
    private final RateEstimator estimator = new RateEstimator(50);

    SensorRow(SensorType type, @Nullable SensorInfo info) {
        this.type = type;
        this.info = info;
    }

    boolean isAvailable() { return info != null; }

    void onSample(SensorSample sample) {
        latest = sample;
        estimator.add(sample.getTimestampNanos());
    }

    double getHz() { return estimator.getHz(); }

    void reset() {
        latest = null;
        estimator.reset();
    }
}
