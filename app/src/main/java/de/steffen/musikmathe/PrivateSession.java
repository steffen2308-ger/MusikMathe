package de.steffen.musikmathe;

import android.app.ActivityManager;
import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Only the dedicated process may open this WebView directory. Never touch the normal profile. */
@android.annotation.TargetApi(28)
final class PrivateSession {
    static final String SUFFIX = "private";
    static void wipe(Context context) throws IOException {
        File data = context.getDataDir();
        remove(new File(context.getCacheDir(), "video-private"));
        remove(new File(data, "app_webview_" + SUFFIX));
        remove(new File(context.getCacheDir(), "WebView_" + SUFFIX));
        remove(new File(context.getCacheDir(), "webview_" + SUFFIX));
    }
    static void remove(File file) throws IOException {
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) { java.nio.file.Files.deleteIfExists(file.toPath()); return; }
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Private Daten konnten nicht gelesen werden.");
            for (File child : children) remove(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("Private Daten konnten nicht gelöscht werden.");
    }
    static boolean running(Context context) {
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return true; // Never delete a potentially live profile.
        for (ActivityManager.RunningAppProcessInfo process : processes)
            if ((context.getPackageName() + ":private").equals(process.processName)) return true;
        return false;
    }
    static void cleanIfStopped(Context context) {
        PrivateProfiles.recoverStopped(context);
    }
}
