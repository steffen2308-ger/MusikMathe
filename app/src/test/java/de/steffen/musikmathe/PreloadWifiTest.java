package de.steffen.musikmathe;

import android.net.NetworkCapabilities;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class PreloadWifiTest {
    private NetworkCapabilities change(NetworkCapabilities c,String method,int value){try{NetworkCapabilities.class.getDeclaredMethod(method,int.class).invoke(c,value);return c;}catch(Exception error){throw new AssertionError(error);}}
    private NetworkCapabilities network(int transport){return change(change(new NetworkCapabilities(),"addTransportType",transport),"addCapability",NetworkCapabilities.NET_CAPABILITY_INTERNET);}
    @Test public void onlyKnownUnmeteredWifiIsAllowed(){
        NetworkCapabilities wifi=network(NetworkCapabilities.TRANSPORT_WIFI);
        assertTrue(PreloadWifi.allowed(wifi,false));assertFalse(PreloadWifi.allowed(wifi,true));
        assertFalse(PreloadWifi.allowed(network(NetworkCapabilities.TRANSPORT_CELLULAR),false));
        assertFalse(PreloadWifi.allowed(null,false));assertFalse(PreloadWifi.allowed(network(NetworkCapabilities.TRANSPORT_ETHERNET),false));
        assertFalse(PreloadWifi.allowed(change(wifi,"removeCapability",NetworkCapabilities.NET_CAPABILITY_INTERNET),false));
    }
    @Test public void vpnMustStillExposeWifiAndMustNotIncludeCellular(){
        NetworkCapabilities vpn=network(NetworkCapabilities.TRANSPORT_VPN);assertFalse(PreloadWifi.allowed(vpn,false));
        change(vpn,"addTransportType",NetworkCapabilities.TRANSPORT_WIFI);assertTrue(PreloadWifi.allowed(vpn,false));
        change(vpn,"addTransportType",NetworkCapabilities.TRANSPORT_CELLULAR);assertFalse(PreloadWifi.allowed(vpn,false));
    }
}
