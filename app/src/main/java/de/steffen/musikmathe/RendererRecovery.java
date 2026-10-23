package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.webkit.WebView;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewRenderProcessClient;
import androidx.webkit.WebViewRenderProcessCompat;
import java.util.function.Predicate;

/** Native controls remain outside an unresponsive webpage's JavaScript event loop. */
final class RendererRecovery {
    private final Activity activity;
    private final Predicate<WebView> visible;
    private AlertDialog dialog;
    private WebView stalled;
    RendererRecovery(Activity activity,Predicate<WebView> visible){this.activity=activity;this.visible=visible;}
    void attach(WebView web){
        try{if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE))WebViewCompat.setWebViewRenderProcessClient(web,new WebViewRenderProcessClient(){
            @Override public void onRenderProcessUnresponsive(WebView view,WebViewRenderProcessCompat process){unresponsive(view,process);}
            @Override public void onRenderProcessResponsive(WebView view,WebViewRenderProcessCompat process){responsive(view);}
        });}catch(RuntimeException|LinkageError unavailable){/* Older providers still have crash recovery. */}
    }
    void unresponsive(WebView view,WebViewRenderProcessCompat process){
        if(!visible.test(view)||dialog!=null)return;
        stalled=view;dialog=BrowserDialogs.builder(activity).setTitle("Seite reagiert nicht")
            .setMessage("Die Webseite antwortet nicht. Du kannst weiter warten oder die blockierte Seite beenden. Andere Seiten desselben Webprozesses müssen danach eventuell neu geladen werden.")
            .setNegativeButton("Weiter warten",(d,w)->clear())
            .setPositiveButton("Seite beenden",(d,w)->{boolean current=stalled==view&&visible.test(view);clear();if(current&&process!=null)try{if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE))process.terminate();}catch(RuntimeException|LinkageError ignored){}})
            .setOnCancelListener(d->clear()).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(process!=null&&canTerminate());
    }
    private boolean canTerminate(){try{return WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE);}catch(RuntimeException|LinkageError unavailable){return false;}}
    void responsive(WebView web){if(stalled==web)clear();}
    void clear(){stalled=null;AlertDialog old=dialog;dialog=null;if(old!=null)old.dismiss();}
}
