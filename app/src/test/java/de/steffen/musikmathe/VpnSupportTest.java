package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class VpnSupportTest {
    @Test public void noNetworkDoesNotPretendVpnIsConnected(){
        Context context=RuntimeEnvironment.getApplication();
        android.net.ConnectivityManager manager=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        org.robolectric.Shadows.shadowOf(manager).setActiveNetworkInfo(null);
        assertEquals(VpnSupport.Status.NOT_DETECTED,VpnSupport.status(context));
    }
    @Test public void missingClientOpensTheOfficialStoreEntry(){
        Intent intent=VpnSupport.openClient(RuntimeEnvironment.getApplication(),VpnSupport.PROTON_PACKAGE);
        assertEquals("com.android.vending",intent.getPackage());
        assertEquals("market://details?id=ch.protonvpn.android",intent.getData().toString());
        assertEquals(Settings.ACTION_VPN_SETTINGS,VpnSupport.settings().getAction());
    }
}
