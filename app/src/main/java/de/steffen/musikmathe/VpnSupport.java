package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.provider.Settings;

/** VPN hand-off to real Android VPN clients; no simulated connection or bundled public proxy. */
final class VpnSupport {
    enum Status { DETECTED, NOT_DETECTED, UNKNOWN }
    static final String PROTON_PACKAGE="ch.protonvpn.android", WIREGUARD_PACKAGE="com.wireguard.android", OPENVPN_PACKAGE="net.openvpn.openvpn";
    private VpnSupport() { }
    static Status status(Context context){
        try{
            ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(manager==null)return Status.UNKNOWN;
            Network network=manager.getActiveNetwork();if(network==null)return Status.NOT_DETECTED;
            NetworkCapabilities capabilities=manager.getNetworkCapabilities(network);
            if(capabilities==null)return Status.UNKNOWN;
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?Status.DETECTED:Status.NOT_DETECTED;
        }catch(RuntimeException ignored){return Status.UNKNOWN;}
    }
    static String statusText(Context context){
        Status state=status(context);
        return state==Status.DETECTED?"Android meldet für BestBrowser eine VPN-Verbindung.":state==Status.NOT_DETECTED?"Für BestBrowser wird gerade keine VPN-Verbindung erkannt.":"Der VPN-Status lässt sich gerade nicht ermitteln.";
    }
    static Intent openClient(Context context,String packageName){
        Intent launch=context.getPackageManager().getLaunchIntentForPackage(packageName);
        if(launch!=null)return launch;
        // Restrict the installation link to the Play Store so BestBrowser does not open itself.
        return new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+packageName)).setPackage("com.android.vending");
    }
    static Intent settings(){return new Intent(Settings.ACTION_VPN_SETTINGS);}
}
