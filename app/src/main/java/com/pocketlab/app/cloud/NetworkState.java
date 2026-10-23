package com.pocketlab.app.cloud;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Quick "is there a working internet connection right now" check, for status text. */
public final class NetworkState {

    private NetworkState() {}

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
