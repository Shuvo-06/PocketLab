package com.pocketlab.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * Receives battery broadcasts. ACTION_BATTERY_CHANGED and ACTION_BATTERY_LOW cannot be declared
 * in the manifest on modern Android, so {@link RecordingService} registers this at run time
 * and unregisters it when recording ends.
 */
public final class BatteryEventReceiver extends BroadcastReceiver {

    public interface Listener {
        /** @param percent -1 if unknown; @param systemLow true for the system's "battery low" broadcast */
        void onBattery(int percent, boolean charging, boolean systemLow);
    }

    private final Listener listener;

    public BatteryEventReceiver(Listener listener) {
        this.listener = listener;
    }

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(Intent.ACTION_BATTERY_LOW);
        return f;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
            listener.onBattery(percent(intent), charging(intent), false);
        } else if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            // This broadcast carries no level; read the sticky BATTERY_CHANGED one.
            Intent sticky = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            listener.onBattery(sticky == null ? -1 : percent(sticky), sticky != null && charging(sticky), true);
        }
    }

    private static int percent(Intent i) {
        return BatteryPolicy.percentOf(i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                i.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
    }

    private static boolean charging(Intent i) {
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
    }
}
