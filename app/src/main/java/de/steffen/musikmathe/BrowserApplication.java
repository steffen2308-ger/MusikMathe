package de.steffen.musikmathe;

import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

public final class BrowserApplication extends Application {
    static boolean privateReady;
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 28 && (getPackageName()+":private").equals(Application.getProcessName())) {
            // Runs before the first CookieManager, WebSettings or WebView call in this process.
            try {
                PrivateSession.wipe(this);
                WebView.setDataDirectorySuffix(PrivateSession.SUFFIX);
                privateReady = true;
            } catch (Exception error) { privateReady = false; }
        }
        BrowserAppearance.sync(this);
    }
}
