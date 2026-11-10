package com.pocketlab.app.cloud;

import android.content.Context;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps a copy of the user's profile in Cloud Firestore ({@code users/{uid}}), so the profile belongs to
 * the account and is the same on every device. Fire and forget: the name and email are also kept by
 * Firebase Authentication, so a failed write only delays the copy until the next sign-in or edit.
 */
public final class ProfileManager {

    private ProfileManager() {}

    public static void sync(Context context) {
        FirebaseUser u = AuthManager.user(context);
        if (u == null) return;
        Map<String, Object> profile = new HashMap<>();
        profile.put("email", u.getEmail());
        profile.put("displayName", u.getDisplayName());
        profile.put("updatedAt", FieldValue.serverTimestamp());
        FirebaseFirestore.getInstance().collection("users").document(u.getUid())
                .set(profile, SetOptions.merge());
    }
}
