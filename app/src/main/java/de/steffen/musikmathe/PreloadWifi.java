package de.steffen.musikmathe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class PreloadWifi {
    static boolean allowed(NetworkCapabilities c,boolean metered){
        return c!=null&&!metered&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    static boolean allowed(Context context){
        try{ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(manager==null)return false;Network active=manager.getActiveNetwork();return active!=null&&allowed(manager.getNetworkCapabilities(active),manager.isActiveNetworkMetered());}
        catch(RuntimeException unavailable){return false;}
    }
}
