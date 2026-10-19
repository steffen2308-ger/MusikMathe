package de.steffen.musikmathe;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/** A single explicit appearance preference shared by normal and private browser processes. */
final class BrowserAppearance {
    private BrowserAppearance(){}
    @SuppressWarnings("deprecation") static boolean savedNight(Context context){
        // Only this independent boolean is shared; no browsing data is written here.
        return context.getSharedPreferences("appearance",Context.MODE_MULTI_PROCESS).getBoolean("night",false);
    }
    static void saveNight(Context context,boolean enabled){
        context.getSharedPreferences("appearance",0).edit().putBoolean("night",enabled).commit();
    }
    static void sync(Context context){
        int mode=savedNight(context)?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO;
        if(AppCompatDelegate.getDefaultNightMode()!=mode)AppCompatDelegate.setDefaultNightMode(mode);
    }
    static boolean night(Context context){return (context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
}
