package com.pocketlab.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.pocketlab.app.cloud.AuthManager;
import com.pocketlab.app.cloud.NetworkState;
import com.pocketlab.app.cloud.SyncPrefs;
import com.pocketlab.app.databinding.ActivityLoginBinding;
import com.pocketlab.app.util.AppDestination;

/**
 * Login page: sign in, create an account (with a name), reset a forgotten password, or continue without
 * an account. Shown at startup while accounts are available and nobody is signed in. After signing in,
 * recordings made without an account are given to the account and a first sync fetches the data from
 * the user's other devices.
 */
public class LoginActivity extends AppCompatActivity {

    /** Show the page even though guest mode was chosen (opened from Profile). */
    public static final String EXTRA_FORCE = "com.pocketlab.app.extra.FORCE_LOGIN";
    /** Open directly in "create account" mode. */
    public static final String EXTRA_REGISTER = "com.pocketlab.app.extra.REGISTER";

    private ActivityLoginBinding b;
    private boolean registerMode;
    private boolean busy;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        if (!getIntent().getBooleanExtra(EXTRA_FORCE, false) && !AuthManager.needsLogin(this)) {
            goToMain();                    // already signed in, or guest mode was chosen earlier
            return;
        }
        registerMode = getIntent().getBooleanExtra(EXTRA_REGISTER, false);

        boolean configured = AuthManager.isConfigured(this);
        b.notConfigured.setVisibility(configured ? View.GONE : View.VISIBLE);
        b.form.setVisibility(configured ? View.VISIBLE : View.GONE);

        b.btnSubmit.setOnClickListener(v -> submit());
        b.btnToggle.setOnClickListener(v -> {
            registerMode = !registerMode;
            applyMode();
        });
        b.btnForgot.setOnClickListener(v -> forgotPassword());
        // The keyboard's Done key submits the form.
        b.passwordEdit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        b.btnGuest.setOnClickListener(v -> {
            new SyncPrefs(this).setGuest(true);
            goToMain();
        });
        applyMode();
    }

    private void applyMode() {
        b.nameLayout.setVisibility(registerMode ? View.VISIBLE : View.GONE);
        b.btnSubmit.setText(registerMode ? R.string.auth_register : R.string.auth_sign_in);
        b.btnToggle.setText(registerMode ? R.string.login_to_sign_in : R.string.login_to_register);
        b.btnForgot.setVisibility(registerMode ? View.GONE : View.VISIBLE);
        showError(null);
    }

    private void submit() {
        if (busy) return;
        hideKeyboard();
        if (!NetworkState.isOnline(this)) {            // Firebase would only fail after a long wait
            showError(getString(R.string.login_offline));
            return;
        }
        String email = String.valueOf(b.emailEdit.getText());
        String password = String.valueOf(b.passwordEdit.getText());
        String name = String.valueOf(b.nameEdit.getText());
        setBusy(true);
        showError(null);
        AuthManager.Callback done = error -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                setBusy(false);
                showError(error);
            } else {
                AuthManager.onSignedIn(this, this::goToMain);
            }
        };
        if (registerMode) AuthManager.register(this, email, password, name, done);
        else AuthManager.signIn(this, email, password, done);
    }

    private void forgotPassword() {
        String email = String.valueOf(b.emailEdit.getText()).trim();
        hideKeyboard();
        if (!NetworkState.isOnline(this)) {
            showError(getString(R.string.login_offline));
            return;
        }
        setBusy(true);
        AuthManager.sendPasswordReset(this, email, error -> {
            if (isFinishing() || isDestroyed()) return;
            setBusy(false);
            if (error != null) showError(error);
            else Snackbar.make(b.getRoot(), getString(R.string.login_reset_sent, email), Snackbar.LENGTH_LONG).show();
        });
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (focus != null && imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        b.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        b.btnSubmit.setEnabled(!busy);
        b.btnForgot.setEnabled(!busy);
        b.btnGuest.setEnabled(!busy);
        b.btnToggle.setEnabled(!busy);
    }

    private void showError(@Nullable String message) {
        b.errorText.setText(message);
        b.errorText.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String destination = getIntent().getStringExtra(AppDestination.EXTRA);   // keep a pending notification target
        if (destination != null) intent.putExtra(AppDestination.EXTRA, destination);
        startActivity(intent);
        finish();
    }
}
