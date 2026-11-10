package com.pocketlab.app.cloud;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/** Small settings and status for accounts, cloud sync and notifications, kept in SharedPreferences. */
public final class SyncPrefs {

    private final SharedPreferences p;

    public SyncPrefs(Context context) {
        p = context.getApplicationContext().getSharedPreferences("cloud", Context.MODE_PRIVATE);
    }

    // ---- account mode ----
    /** True if the user chose "continue without an account" on the login page. */
    public boolean isGuest() { return p.getBoolean("guest", false); }
    public void setGuest(boolean v) { p.edit().putBoolean("guest", v).apply(); }

    // ---- sync settings (on by default: that is what makes data follow the user to other devices) ----
    public boolean isAutoSync() { return p.getBoolean("autoSync", true); }
    public void setAutoSync(boolean v) { p.edit().putBoolean("autoSync", v).apply(); }

    public boolean isWifiOnly() { return p.getBoolean("wifiOnly", false); }
    public void setWifiOnly(boolean v) { p.edit().putBoolean("wifiOnly", v).apply(); }

    // ---- notifications ----
    /** Notifications about saved recordings, backups and experiment updates (not the recording notification). */
    public boolean isEventNotifications() { return p.getBoolean("events", true); }
    public void setEventNotifications(boolean v) { p.edit().putBoolean("events", v).apply(); }

    public boolean wasNotificationPermissionAsked() { return p.getBoolean("askedNotifications", false); }
    public void setNotificationPermissionAsked() { p.edit().putBoolean("askedNotifications", true).apply(); }

    /** A random id for this installation, used to tell "this device" from "another device" in live status. */
    public String deviceId() {
        String id = p.getString("deviceId", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            p.edit().putString("deviceId", id).apply();
        }
        return id;
    }

    // ---- sync status ----
    public boolean isRunning() { return p.getBoolean("running", false); }
    public void setRunning(boolean v) { p.edit().putBoolean("running", v).apply(); }

    public long getLastSyncAt() { return p.getLong("lastSyncAt", 0); }
    public String getLastMessage() { return p.getString("lastMessage", null); }
    public boolean wasLastOk() { return p.getBoolean("lastOk", true); }

    public void recordSuccess(String message) {
        p.edit().putLong("lastSyncAt", System.currentTimeMillis()).putString("lastMessage", message)
                .putBoolean("lastOk", true).apply();
    }

    public void recordFailure(String message) {
        p.edit().putString("lastMessage", message).putBoolean("lastOk", false).apply();
    }

    // ---- experiment definitions ----
    public long getExperimentCheckAt() { return p.getLong("expCheckAt", 0); }
    public String getExperimentMessage() { return p.getString("expMessage", null); }

    public void recordExperimentCheck(String message) {
        p.edit().putLong("expCheckAt", System.currentTimeMillis()).putString("expMessage", message).apply();
    }
}
