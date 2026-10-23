package de.steffen.musikmathe;

import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

public final class BrowserApplication extends Application {
    static boolean privateReady;
    static boolean privateProcess;
    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 28 && (getPackageName()+":private").equals(Application.getProcessName())) {
            // Authentication and restoration must happen before the first WebView API call.
            privateProcess=true;
        }
        BrowserAppearance.sync(this);
    }
    @android.annotation.TargetApi(28)
    static void preparePrivateWebView(){if(!privateReady){WebView.setDataDirectorySuffix(PrivateSession.SUFFIX);privateReady=true;}}
}
