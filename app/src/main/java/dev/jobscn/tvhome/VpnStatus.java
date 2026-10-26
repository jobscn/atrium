package dev.jobscn.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** Observes VPN transport visible to this user; never identifies its provider. */
public enum VpnStatus {
    ACTIVE, INACTIVE, UNKNOWN;

    public static VpnStatus read(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) return UNKNOWN;
            boolean incomplete = false;
            for (Network network : manager.getAllNetworks()) {
                NetworkCapabilities caps = manager.getNetworkCapabilities(network);
                if (caps == null) { incomplete = true; continue; }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return ACTIVE;
            }
            return incomplete ? UNKNOWN : INACTIVE;
        } catch (RuntimeException unavailable) {
            return UNKNOWN;
        }
    }

    /** Safe fallback only: opening the provider does NOT establish or stop a VPN. */
    public static Intent providerIntent(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage("hev.sockstun");
            if (intent == null) intent = pm.getLaunchIntentForPackage("hev.sockstun");
            return intent;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }
}
