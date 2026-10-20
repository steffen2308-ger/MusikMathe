package de.steffen.musikmathe;

import android.content.Context;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import java.util.List;

/** Public entry point loaded by the Cast SDK, only after a normal-tab Cast action. */
public final class BrowserCastOptions implements OptionsProvider {
    @Override public CastOptions getCastOptions(Context context) {
        return new CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setResumeSavedSession(false)
            .setEnableReconnectionService(false)
            .setCastMediaOptions(new CastMediaOptions.Builder().setNotificationOptions(null).build())
            .build();
    }
    @Override public List<SessionProvider> getAdditionalSessionProviders(Context context) { return null; }
}
