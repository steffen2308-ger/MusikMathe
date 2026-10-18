package de.steffen.musikmathe;

import android.content.Context;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.util.Arrays;
import java.util.HashSet;
import org.json.JSONObject;

/** Only trusted HTTPS YouTube frames can report video metadata; no native actions are exposed. */
final class YouTubeBridge {
    interface Listener { void player(JSONObject data); }
    static void attach(Context context,WebView web,Listener listener){
        try{
            if(!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))return;
            java.util.Set<String> origins=new HashSet<>(Arrays.asList("https://youtube.com","https://*.youtube.com","https://youtube-nocookie.com","https://*.youtube-nocookie.com"));
            WebViewCompat.addWebMessageListener(web,"musikYouTube",origins,(view,message,origin,mainFrame,reply)->{
                if(!YouTubeSources.originAllowed(origin.toString()))return;
                if(message.getType()!=androidx.webkit.WebMessageCompat.TYPE_STRING)return;
                String value=message.getData();if(value==null||value.length()>300000)return;
                try{JSONObject data=new JSONObject(value);if(YouTubeSources.originAllowed(data.optString("page")))listener.player(data);}catch(Exception ignored){}
            });
            if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))WebViewCompat.addDocumentStartJavaScript(web,YouTubeSources.script(context),origins);
        }catch(RuntimeException|LinkageError ignored){ /* Older providers use the top-frame fallback. */ }
    }
}
