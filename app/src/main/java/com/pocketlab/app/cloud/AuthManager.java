package com.pocketlab.app.cloud;

import android.content.Context;
import android.util.Patterns;

import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.pocketlab.app.PocketLabApp;
import com.pocketlab.app.R;
import com.pocketlab.app.work.SyncScheduler;

/**
 * Accounts with Firebase Authentication (email and password). If the app was built without a
 * {@code google-services.json}, Firebase is not initialised: {@link #isConfigured} is then false, the
 * login page offers only "continue without an account", and everything else works locally.
 */
public final class AuthManager {

    public interface Callback {
        /** @param error null on success, otherwise a message for the user */
        void onDone(@Nullable String error);
    }

    private AuthManager() {}

    public static boolean isConfigured(Context context) {
        return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
    }

    @Nullable
    public static FirebaseUser user(Context context) {
        return isConfigured(context) ? FirebaseAuth.getInstance().getCurrentUser() : null;
    }

    /** The signed-in account's uid, or "" when nobody is signed in. Recordings are owned by this value. */
    public static String uid(Context context) {
        FirebaseUser u = user(context);
        return u == null ? "" : u.getUid();
    }

    /** The login page must be shown: accounts are available, nobody is signed in, and guest mode was not chosen. */
    public static boolean needsLogin(Context context) {
        return isConfigured(context) && user(context) == null && !new SyncPrefs(context).isGuest();
    }

    public static String displayName(Context context) {
        FirebaseUser u = user(context);
        if (u == null) return context.getString(R.string.profile_guest_name);
        if (u.getDisplayName() != null && !u.getDisplayName().trim().isEmpty()) return u.getDisplayName();
        String email = u.getEmail();
        return email == null ? context.getString(R.string.profile_guest_name) : email.split("@")[0];
    }

    // ------------------------------------------------------------------ actions

    public static void signIn(Context context, String email, String password, Callback cb) {
        String problem = validate(context, email, password);
        if (problem != null) {
            cb.onDone(problem);
            return;
        }
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(r -> cb.onDone(null))
                .addOnFailureListener(e -> cb.onDone(message(context, e)));
    }

    /** @param displayName optional; blank is fine */
    public static void register(Context context, String email, String password, String displayName, Callback cb) {
        String problem = validate(context, email, password);
        if (problem != null) {
            cb.onDone(problem);
            return;
        }
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> {
                    String name = displayName == null ? "" : displayName.trim();
                    FirebaseUser created = result.getUser();
                    if (name.isEmpty() || created == null) {
                        cb.onDone(null);
                        return;
                    }
                    created.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build())
                            .addOnCompleteListener(t -> cb.onDone(null));   // the account exists even if the name failed
                })
                .addOnFailureListener(e -> cb.onDone(message(context, e)));
    }

    public static void sendPasswordReset(Context context, String email, Callback cb) {
        if (!isConfigured(context)) {
            cb.onDone(context.getString(R.string.cloud_not_configured));
            return;
        }
        if (email == null || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            cb.onDone(context.getString(R.string.auth_error_email));
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(v -> cb.onDone(null))
                .addOnFailureListener(e -> cb.onDone(message(context, e)));
    }

    public static void updateDisplayName(Context context, String name, Callback cb) {
        FirebaseUser u = user(context);
        if (u == null) {
            cb.onDone(context.getString(R.string.cloud_signed_out));
            return;
        }
        u.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
                .addOnSuccessListener(v -> {
                    ProfileManager.sync(context);
                    cb.onDone(null);
                })
                .addOnFailureListener(e -> cb.onDone(message(context, e)));
    }

    public static void signOut(Context context) {
        if (isConfigured(context)) FirebaseAuth.getInstance().signOut();
        new SyncPrefs(context).setGuest(false);
        SyncScheduler.applyAutoSync(context, false, true);   // no account, nothing to sync
    }

    /**
     * Everything that must happen right after a successful sign-in or registration: leave guest mode,
     * mirror the profile to Firestore, give recordings made without an account to this account, and
     * start a first sync, which also downloads what was recorded on the user's other devices.
     */
    public static void onSignedIn(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        SyncPrefs prefs = new SyncPrefs(app);
        prefs.setGuest(false);
        FirebaseUser u = user(app);
        if (u == null) {
            done.run();
            return;
        }
        ProfileManager.sync(app);
        ((PocketLabApp) app).getSessionRepository().claimGuestSessions(u.getUid(), claimed -> {
            SyncScheduler.applyAutoSync(app, prefs.isAutoSync(), prefs.isWifiOnly());
            SyncScheduler.syncNow(app, SyncMode.SYNC);
            done.run();
        });
    }

    // ------------------------------------------------------------------ helpers

    private static String validate(Context context, String email, String password) {
        if (!isConfigured(context)) return context.getString(R.string.cloud_not_configured);
        if (email == null || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return context.getString(R.string.auth_error_email);
        }
        if (password == null || password.length() < 6) return context.getString(R.string.auth_error_short_password);
        return null;
    }

    private static String message(Context context, Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException || e instanceof FirebaseAuthInvalidUserException) {
            return context.getString(R.string.auth_error_credentials);
        }
        if (e instanceof FirebaseAuthUserCollisionException) return context.getString(R.string.auth_error_exists);
        if (e instanceof FirebaseAuthWeakPasswordException) return context.getString(R.string.auth_error_short_password);
        if (e instanceof FirebaseNetworkException) return context.getString(R.string.auth_error_network);
        return context.getString(R.string.auth_error_other, e.getMessage() == null ? "" : e.getMessage());
    }
}
