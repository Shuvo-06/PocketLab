package com.pocketlab.app.cloud;

import android.content.Context;
import android.os.Build;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Realtime Database presence: while a phone records, it publishes {@code status/{uid}} every few
 * seconds, and any other device signed in to the same account sees it live through a listener (no
 * polling). {@code onDisconnect().removeValue()} makes the server clear the entry by itself if the phone
 * loses its connection or the app dies. Everything is best effort and silently skipped when the account
 * or the Realtime Database is not available. Rules: {@code "status": {"$uid": {".read"/".write": "auth.uid == $uid"}}}.
 */
public final class LiveStatus {

    /** What another (or this) device is recording right now. */
    public static final class Status {
        public final String deviceId, deviceName, sensor;
        public final long startedAt, samples;

        Status(String deviceId, String deviceName, String sensor, long startedAt, long samples) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.sensor = sensor;
            this.startedAt = startedAt;
            this.samples = samples;
        }
    }

    public interface Listener {
        /** @param status null when nothing is being recorded */
        void onStatus(@Nullable Status status);
    }

    /** Call to stop listening. */
    public interface Handle {
        void stop();
    }

    private LiveStatus() {}

    private static DatabaseReference ref(String uid) {
        return FirebaseDatabase.getInstance(RtdbSampleStore.DB_URL).getReference("status").child(uid);
    }

    public static void publish(Context context, String sensorName, long startedAtMillis, long samples) {
        try {
            FirebaseUser u = AuthManager.user(context);
            if (u == null) return;
            Map<String, Object> m = new HashMap<>();
            m.put("deviceId", new SyncPrefs(context).deviceId());
            m.put("deviceName", Build.MODEL);
            m.put("sensor", sensorName);
            m.put("startedAt", startedAtMillis);
            m.put("samples", samples);
            m.put("updatedAt", ServerValue.TIMESTAMP);
            DatabaseReference r = ref(u.getUid());
            r.onDisconnect().removeValue();
            r.setValue(m);
        } catch (Exception ignored) {
            // Presence is a nicety; never let it disturb recording.
        }
    }

    public static void clear(Context context) {
        try {
            FirebaseUser u = AuthManager.user(context);
            if (u != null) ref(u.getUid()).removeValue();
        } catch (Exception ignored) {
            // see publish()
        }
    }

    /** @return a handle to stop listening, or null if listening is not possible right now */
    @Nullable
    public static Handle listen(Context context, Listener listener) {
        try {
            FirebaseUser u = AuthManager.user(context);
            if (u == null) return null;
            DatabaseReference r = ref(u.getUid());
            ValueEventListener l = new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot s) {
                    if (!s.exists()) {
                        listener.onStatus(null);
                        return;
                    }
                    String sensor = s.child("sensor").getValue(String.class);
                    Long started = s.child("startedAt").getValue(Long.class);
                    Long samples = s.child("samples").getValue(Long.class);
                    listener.onStatus(new Status(String.valueOf(s.child("deviceId").getValue(String.class)),
                            String.valueOf(s.child("deviceName").getValue(String.class)),
                            sensor == null ? "?" : sensor, started == null ? 0 : started, samples == null ? 0 : samples));
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    listener.onStatus(null);
                }
            };
            r.addValueEventListener(l);
            return () -> r.removeEventListener(l);
        } catch (Exception e) {
            return null;
        }
    }
}
