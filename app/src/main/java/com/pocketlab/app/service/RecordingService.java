package com.pocketlab.app.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.cloud.LiveStatus;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;
import com.pocketlab.app.util.AppDestination;

/**
 * Foreground service that keeps a recording alive when the app is in the background or the screen is
 * off. It shows an ongoing notification (tap = back to the app, Stop = save and end), holds a
 * time-limited partial wake lock while recording, reacts to low battery, publishes live status to the
 * Realtime Database for the user's other devices, and stops itself when the recording ends.
 * The recording itself belongs to {@link RecordingController}.
 */
public class RecordingService extends Service {

    static final String ACTION_STOP = "com.pocketlab.app.action.STOP_RECORDING";

    private static final long TICK_MS = 1000;
    private static final int LIVE_EVERY_N_TICKS = 5;
    /** Safety limit: a forgotten recording stops (and is saved) after this long. */
    private static final long MAX_RECORDING_MS = 6L * 60 * 60 * 1000;

    private RecordingController controller;
    private NotificationManager notifications;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BatteryPolicy batteryPolicy = new BatteryPolicy();
    private final BatteryEventReceiver batteryReceiver = new BatteryEventReceiver(this::onBattery);
    private boolean started;
    private boolean receiverRegistered;
    private boolean shuttingDown;
    private boolean livePublished;
    private long startedAt;
    private long startedWallClock;
    private int ticks;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (shuttingDown) return;
            if (!controller.isActive()) {            // stopped from the app
                shutDown();
                return;
            }
            if (SystemClock.elapsedRealtime() - startedAt > MAX_RECORDING_MS) {
                controller.stop();
                Notifier.alert(RecordingService.this, getString(R.string.alert_time_limit), AppDestination.HISTORY);
                shutDown();
                return;
            }
            updateWakeLock();
            notifications.notify(Notifier.ID_RECORDING, buildNotification());
            if (ticks++ % LIVE_EVERY_N_TICKS == 0) publishLive();
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        controller = ((PocketLabApp) getApplication()).getRecordingController();
        notifications = getSystemService(NotificationManager.class);
        Notifier.createChannels(this);
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketLab:recording");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            controller.stop();                       // saves the recording
            shutDown();
            return START_NOT_STICKY;
        }
        // Started with startForegroundService(): we must call startForeground() promptly.
        startForeground(Notifier.ID_RECORDING, buildNotification());
        if (!controller.isActive()) {
            shutDown();
            return START_NOT_STICKY;
        }
        controller.setForegroundActive(true);
        if (!started) {
            started = true;
            startedAt = SystemClock.elapsedRealtime();
            startedWallClock = System.currentTimeMillis();
            registerReceiver(batteryReceiver, BatteryEventReceiver.filter());
            receiverRegistered = true;
            handler.post(tick);
        }
        return START_NOT_STICKY;   // if the system kills us the recording is gone anyway
    }

    // ------------------------------------------------------------------ battery

    private void onBattery(int percent, boolean charging, boolean systemLow) {
        if (shuttingDown || !controller.isActive()) return;
        BatteryPolicy.Decision d = systemLow ? batteryPolicy.onSystemLow(charging)
                : batteryPolicy.onLevel(percent, charging);
        if (d == BatteryPolicy.Decision.STOP_AND_SAVE) {
            controller.stop();
            Notifier.alert(this, getString(R.string.alert_battery_stopped, percent), AppDestination.HISTORY);
            shutDown();
        } else if (d == BatteryPolicy.Decision.WARN) {
            Notifier.alert(this, percent >= 0 ? getString(R.string.alert_battery_low, percent)
                    : getString(R.string.alert_battery_low_unknown), AppDestination.RECORDER);
        }
    }

    // ------------------------------------------------------------------ live status (Realtime Database)

    private void publishLive() {
        RecordingSession s = controller.getSession();
        if (s == null) return;
        livePublished = true;
        LiveStatus.publish(this, s.getType().getDisplayName(), startedWallClock, s.getTotalSamples());
    }

    // ------------------------------------------------------------------ lifecycle helpers

    private void shutDown() {
        if (shuttingDown) return;
        shuttingDown = true;
        handler.removeCallbacks(tick);
        if (wakeLock.isHeld()) wakeLock.release();
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver);
            receiverRegistered = false;
        }
        if (livePublished) LiveStatus.clear(this);
        controller.setForegroundActive(false);
        stopForeground(true);
        stopSelf();
    }

    /** Hold the CPU awake only while samples are actually being collected. */
    private void updateWakeLock() {
        boolean recording = controller.getState() == RecordingState.RECORDING;
        if (recording && !wakeLock.isHeld()) {
            wakeLock.acquire(MAX_RECORDING_MS + 60_000L);
        } else if (!recording && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onDestroy() {
        shutDown();   // no-op if already done; releases everything if the system stops us
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------ notification

    private Notification buildNotification() {
        RecordingSession s = controller.getSession();
        String title, text;
        if (s == null) {
            title = getString(R.string.app_name);
            text = getString(R.string.state_ready);
        } else {
            title = getString(R.string.notif_recording_title, s.getType().getDisplayName());
            String state = getString(s.getState() == RecordingState.PAUSED
                    ? R.string.state_paused : R.string.state_recording);
            text = getString(R.string.notif_recording_text, state,
                    TimeFormat.format(s.getElapsedSeconds()), s.getTotalSamples());
        }
        Intent stop = new Intent(this, RecordingService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, Notifier.CHANNEL_RECORDING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(Notifier.openApp(this, AppDestination.RECORDER))
                .addAction(0, getString(R.string.notif_stop), stopPi)
                .build();
    }
}
