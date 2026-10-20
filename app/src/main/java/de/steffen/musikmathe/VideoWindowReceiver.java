package de.steffen.musikmathe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Only explicit, immutable actions created by our own picture-in-picture window. */
public final class VideoWindowReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){BrowserPip.control(intent);}
}
