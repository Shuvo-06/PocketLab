package com.pocketlab.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.pocketlab.app.MainActivity;
import com.pocketlab.app.R;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.util.AppDestination;

/**
 * All notifications of the app in one place: the three channels, and notifications that open the app
 * (on a chosen screen) when tapped. They are posted from services and background workers, so they
 * appear whether or not the app is on screen.
 */
public final class Notifier {

    public static final String CHANNEL_RECORDING = "recording";   // ongoing recording (silent)
    public static final String CHANNEL_ALERTS = "alerts";         // battery and limits (heads-up)
    public static final String CHANNEL_EVENTS = "events";         // saved, backup, new experiments

    public static final int ID_RECORDING = 1;
    public static final int ID_ALERT = 2;
    public static final int ID_SAVED = 3;
    public static final int ID_SYNC = 4;
    public static final int ID_EXPERIMENTS = 5;

    private Notifier() {}

    public static void createChannels(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_RECORDING,
                context.getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_EVENTS,
                context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT));
    }

    /**
     * A PendingIntent that opens the app on {@code destination}. Each destination has its own request
     * code, otherwise Android would reuse one PendingIntent and ignore the other destinations' extras.
     */
    public static PendingIntent openApp(Context context, AppDestination destination) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction("com.pocketlab.app.action.OPEN")
                .putExtra(AppDestination.EXTRA, destination.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 100 + destination.ordinal(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * An information notification ("recording saved", "backup finished", "new experiments"). Skipped if the
     * user turned these off in Profile, or if the system blocks notifications (Android 13+ permission).
     */
    public static void event(Context context, int id, String title, String text, AppDestination destination) {
        if (!new SyncPrefs(context).isEventNotifications()) return;
        post(context, id, build(context, CHANNEL_EVENTS, title, text, destination, NotificationCompat.PRIORITY_DEFAULT));
    }

    /** Important warning (low battery, limits): heads-up, always shown. */
    public static void alert(Context context, String text, AppDestination destination) {
        post(context, ID_ALERT, build(context, CHANNEL_ALERTS, context.getString(R.string.app_name), text,
                destination, NotificationCompat.PRIORITY_HIGH));
    }

    private static Notification build(Context context, String channel, String title, String text,
                                      AppDestination destination, int priority) {
        return new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(priority)
                .setAutoCancel(true)
                .setContentIntent(openApp(context, destination))
                .build();
    }

    private static void post(Context context, int id, Notification n) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        if (!nm.areNotificationsEnabled()) return;   // no permission or blocked in system settings
        createChannels(context);
        nm.notify(id, n);
    }
}
