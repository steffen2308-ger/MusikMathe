package de.steffen.musikmathe;

import android.webkit.WebView;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

/** Native WebView darkening preserves media colours and site-provided dark themes. */
final class BrowserWebAppearance {
    private BrowserWebAppearance(){}
    private static boolean supported(String feature){try{return WebViewFeature.isFeatureSupported(feature);}catch(RuntimeException|LinkageError ignored){return false;}}
    @SuppressWarnings("deprecation") static void apply(WebView web,boolean night){
        web.setBackgroundColor(web.getContext().getColor(R.color.browser_page));
        if(supported(WebViewFeature.ALGORITHMIC_DARKENING)){
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.getSettings(),night);
        }else if(supported(WebViewFeature.FORCE_DARK)){
            WebSettingsCompat.setForceDark(web.getSettings(),night?WebSettingsCompat.FORCE_DARK_ON:WebSettingsCompat.FORCE_DARK_OFF);
        }
    }
    static void pageFinished(WebView web,boolean night){
        // Old providers cannot expose prefers-color-scheme. Our local pages still get
        // their complete dark stylesheet, without reloading or inverting images/video.
        String local="";
        String url=web.getUrl();
        if(Address.HOME.equals(url)||WikiTop.URL.equals(url))local="document.documentElement.classList.toggle('bestbrowser-night',"+night+");document.documentElement.classList.toggle('bestbrowser-light',"+(!night)+");";
        boolean legacy=!supported(WebViewFeature.ALGORITHMIC_DARKENING)&&!supported(WebViewFeature.FORCE_DARK);
        String fallback=legacy?"var s=document.getElementById('__bestbrowser_night');if(s)s.remove();"+(night?"s=document.createElement('style');s.id='__bestbrowser_night';s.textContent='html,body{background-color:#142925!important;color:#e3eee8!important;color-scheme:dark}a{color:#80d6bd!important}input,textarea,select,button{background-color:#263730!important;color:#e3eee8!important;border-color:#3a4b44!important}';(document.head||document.documentElement).appendChild(s);":""):"";
        if(!local.isEmpty()||!fallback.isEmpty())web.evaluateJavascript("(function(){"+local+fallback+"})()",null);
    }
}
