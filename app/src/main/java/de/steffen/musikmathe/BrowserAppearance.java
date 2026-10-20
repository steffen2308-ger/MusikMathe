package de.steffen.musikmathe;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/** Normal appearance preference; unlocked private areas keep an independent encrypted choice. */
final class BrowserAppearance {
    private BrowserAppearance(){}
    @SuppressWarnings("deprecation") static boolean savedNight(Context context){
        PrivateProfiles.Session profile=PrivateProfiles.forContext(context);if(profile!=null)return profile.bool("night",false);
        // The normal preference also supplies the neutral lock-screen appearance.
        return context.getSharedPreferences("appearance",Context.MODE_MULTI_PROCESS).getBoolean("night",false);
    }
    static void saveNight(Context context,boolean enabled){
        PrivateProfiles.Session profile=PrivateProfiles.forContext(context);if(profile!=null){profile.put("night",enabled);return;}
        context.getSharedPreferences("appearance",0).edit().putBoolean("night",enabled).commit();
    }
    static void sync(Context context){
        int mode=savedNight(context)?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO;
        if(AppCompatDelegate.getDefaultNightMode()!=mode)AppCompatDelegate.setDefaultNightMode(mode);
    }
    static boolean night(Context context){return (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
}
