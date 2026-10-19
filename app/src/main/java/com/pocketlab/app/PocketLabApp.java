package com.pocketlab.app;

import android.app.Application;

import com.pocketlab.app.sensor.SensorHub;

/** Application entry point and home of shared singletons (SensorHub now; database later). */
public class PocketLabApp extends Application {

    private SensorHub sensorHub;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorHub = new SensorHub(this);
    }

    public SensorHub getSensorHub() {
        return sensorHub;
    }
}
