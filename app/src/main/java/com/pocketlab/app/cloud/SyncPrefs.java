package com.pocketlab.app.cloud;

import android.content.Context;
import android.content.SharedPreferences;

/** Small settings and status for cloud sync, kept in SharedPreferences. */
public final class SyncPrefs {

    private final SharedPreferences p;

    public SyncPrefs(Context context) {
        p = context.getApplicationContext().getSharedPreferences("cloud", Context.MODE_PRIVATE);
    }

    public boolean isAutoSync() { return p.getBoolean("autoSync", false); }
    public void setAutoSync(boolean v) { p.edit().putBoolean("autoSync", v).apply(); }

    public boolean isWifiOnly() { return p.getBoolean("wifiOnly", true); }
    public void setWifiOnly(boolean v) { p.edit().putBoolean("wifiOnly", v).apply(); }

    /** The account the local sync links belong to. */
    public String getLastUid() { return p.getString("lastUid", null); }
    public void setLastUid(String uid) { p.edit().putString("lastUid", uid).apply(); }

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

    // experiment definitions
    public long getExperimentCheckAt() { return p.getLong("expCheckAt", 0); }
    public String getExperimentMessage() { return p.getString("expMessage", null); }

    public void recordExperimentCheck(String message) {
        p.edit().putLong("expCheckAt", System.currentTimeMillis()).putString("expMessage", message).apply();
    }
}
