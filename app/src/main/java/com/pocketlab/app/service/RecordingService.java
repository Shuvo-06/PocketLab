package com.pocketlab.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
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

import com.pocketlab.app.MainActivity;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.graph.TimeFormat;
import com.pocketlab.app.recording.RecordingSession;
import com.pocketlab.app.recording.RecordingState;

/**
 * Foreground service that keeps a recording alive when the app is in the background or the
 * screen is off. It shows an ongoing notification with a Stop action, holds a time-limited
 * partial wake lock while recording, reacts to low battery, and stops itself when the recording
 * ends. The recording itself belongs to {@link RecordingController}.
 */
public class RecordingService extends Service {

    static final String ACTION_STOP = "com.pocketlab.app.action.STOP_RECORDING";

    private static final String CHANNEL_RECORDING = "recording";
    private static final String CHANNEL_ALERTS = "alerts";
    private static final int NOTIF_RECORDING = 1;
    private static final int NOTIF_ALERT = 2;
    private static final long TICK_MS = 1000;
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
    private long startedAt;

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
                alert(getString(R.string.alert_time_limit));
                shutDown();
                return;
            }
            updateWakeLock();
            notifications.notify(NOTIF_RECORDING, buildNotification());
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        controller = ((PocketLabApp) getApplication()).getRecordingController();
        notifications = getSystemService(NotificationManager.class);
        createChannels();
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
        startForeground(NOTIF_RECORDING, buildNotification());
        if (!controller.isActive()) {
            shutDown();
            return START_NOT_STICKY;
        }
        controller.setForegroundActive(true);
        if (!started) {
            started = true;
            startedAt = SystemClock.elapsedRealtime();
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
            alert(getString(R.string.alert_battery_stopped, percent));
            shutDown();
        } else if (d == BatteryPolicy.Decision.WARN) {
            alert(percent >= 0 ? getString(R.string.alert_battery_low, percent)
                    : getString(R.string.alert_battery_low_unknown));
        }
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

    // ------------------------------------------------------------------ notifications

    private void createChannels() {
        NotificationChannel rec = new NotificationChannel(CHANNEL_RECORDING,
                getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW);
        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS,
                getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH);
        notifications.createNotificationChannel(rec);
        notifications.createNotificationChannel(alerts);
    }

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
        return new NotificationCompat.Builder(this, CHANNEL_RECORDING)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openAppIntent())
                .addAction(0, getString(R.string.notif_stop), stopPi)
                .build();
    }

    private void alert(String message) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build();
        notifications.notify(NOTIF_ALERT, n);
    }

    private PendingIntent openAppIntent() {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
