package com.pocketlab.app.cloud;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Tells the UI, live, whether the phone has a working internet connection. It registers one
 * {@link ConnectivityManager.NetworkCallback} for the life of the app and publishes the result as
 * LiveData, so an Activity can show or hide the offline banner the moment the connection changes.
 *
 * <p>"Online" means the default network has internet AND Android has validated it (a Wi-Fi network with
 * no real internet counts as offline), the same rule as {@link NetworkState}.
 */
public final class ConnectivityMonitor {

    private final MutableLiveData<Boolean> online = new MutableLiveData<>();

    public ConnectivityMonitor(Context context) {
        Context app = context.getApplicationContext();
        online.setValue(NetworkState.isOnline(app));        // constructed on the main thread (Application.onCreate)
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        if (cm == null) return;
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
                    online.postValue(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
                }

                @Override
                public void onLost(@NonNull Network network) {
                    online.postValue(false);
                }
            });
        } catch (RuntimeException ignored) {
            // Some devices limit the number of callbacks; the value then stays at its start-up state.
        }
    }

    /** True while there is a working internet connection. Never null after construction. */
    public LiveData<Boolean> online() {
        return online;
    }
}
