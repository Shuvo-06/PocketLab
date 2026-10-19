package com.pocketlab.app.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central access point for all smartphone sensors.
 *
 * <ul>
 *   <li>Detects which sensors exist (missing hardware is normal, never an error).</li>
 *   <li>Hands out {@link Subscription}s; each owns one SensorManager registration.</li>
 *   <li>Callbacks arrive on the main thread (SensorManager's default).</li>
 * </ul>
 * Call all methods from the main thread and always cancel subscriptions.
 */
public class SensorHub {

    /** Receives samples. */
    public interface Listener {
        void onSample(@NonNull SensorSample sample);
    }

    /** Handle for one active registration. */
    public final class Subscription implements SensorEventListener {
        private final SensorType type;
        private final Sensor sensor;
        private final Listener listener;
        private boolean active = true;

        private Subscription(SensorType type, Sensor sensor, Listener listener) {
            this.type = type;
            this.sensor = sensor;
            this.listener = listener;
        }

        public SensorType getType() { return type; }

        public void cancel() {
            if (!active) return;
            active = false;
            manager.unregisterListener(this, sensor);
            subscriptions.remove(this);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!active) return;
            // event.values is reused by the framework, so SensorSample copies it.
            listener.onSample(new SensorSample(type, event.timestamp, event.values, event.accuracy));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Accuracy also arrives with every sample.
        }
    }

    private final SensorManager manager;
    private final Map<SensorType, Sensor> sensors = new EnumMap<>(SensorType.class);
    private final Map<SensorType, SensorInfo> infos = new EnumMap<>(SensorType.class);
    private final Set<Subscription> subscriptions = new LinkedHashSet<>();

    public SensorHub(Context context) {
        manager = (SensorManager) context.getApplicationContext()
                .getSystemService(Context.SENSOR_SERVICE);
        if (manager == null) return;
        for (SensorType type : SensorType.values()) {
            Sensor s = manager.getDefaultSensor(type.getAndroidType());
            if (s != null) {
                sensors.put(type, s);
                infos.put(type, SensorInfo.from(s));
            }
        }
    }

    public boolean isAvailable(SensorType type) {
        return sensors.containsKey(type);
    }

    /** Hardware info, or null if the sensor is not present. */
    @Nullable
    public SensorInfo getInfo(SensorType type) {
        return infos.get(type);
    }

    /** Available sensors in enum order. */
    public List<SensorType> getAvailableTypes() {
        return new ArrayList<>(sensors.keySet());
    }

    public int getAvailableCount() {
        return sensors.size();
    }

    /**
     * Starts delivering samples. Returns null if the sensor is missing or Android
     * refused the registration, so callers must handle null.
     */
    @Nullable
    public Subscription subscribe(SensorType type, SamplingRate rate, Listener listener) {
        Sensor sensor = sensors.get(type);
        if (sensor == null) return null;
        Subscription sub = new Subscription(type, sensor, listener);
        boolean ok;
        try {
            ok = manager.registerListener(sub, sensor, rate.getPeriodUs());
        } catch (SecurityException e) {
            ok = false;
        }
        if (!ok) return null;
        subscriptions.add(sub);
        return sub;
    }

    /** Cancels every active subscription (safety net for lifecycle edges). */
    public void cancelAll() {
        for (Subscription s : new ArrayList<>(subscriptions)) {
            s.cancel();
        }
    }
}
