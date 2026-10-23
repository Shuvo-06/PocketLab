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
import com.pocketlab.app.R;

/**
 * Firebase Authentication with email and password. If the app was built without a
 * {@code google-services.json} Firebase is not initialised; {@link #isConfigured} is then false
 * and the rest of the app simply works without cloud features.
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

    public static void register(Context context, String email, String password, Callback cb) {
        String problem = validate(context, email, password);
        if (problem != null) {
            cb.onDone(problem);
            return;
        }
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(r -> cb.onDone(null))
                .addOnFailureListener(e -> cb.onDone(message(context, e)));
    }

    public static void signOut(Context context) {
        if (isConfigured(context)) FirebaseAuth.getInstance().signOut();
    }

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
