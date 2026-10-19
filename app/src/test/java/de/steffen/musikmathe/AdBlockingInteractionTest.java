package de.steffen.musikmathe;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class AdBlockingInteractionTest {
    @Before public void clearSettings() {
        RuntimeEnvironment.getApplication().getSharedPreferences("browser",0).edit().clear().commit();
    }
    private Object invoke(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception {
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);
    }
    private WebView web(MainActivity activity)throws Exception {
        return (WebView)invoke(activity,"activeView",new Class<?>[0]);
    }
    private WebResourceRequest request(String url,boolean mainFrame) {
        return new WebResourceRequest() {
            @Override public Uri getUrl(){return Uri.parse(url);}
            @Override public boolean isForMainFrame(){return mainFrame;}
            @Override public boolean isRedirect(){return false;}
            @Override public boolean hasGesture(){return false;}
            @Override public String getMethod(){return "GET";}
            @Override public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}
        };
    }
    @Test public void realWebViewClientBlocksAdsAndToggleAppliesToExistingTabs()throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        try {
            MainActivity activity=controller.get();WebView first=web(activity);
            WebResourceRequest ad=request("https://ads.doubleclick.net/banner.js",false);
            WebResourceResponse response=first.getWebViewClient().shouldInterceptRequest(first,ad);
            assertNotNull(response);assertEquals(204,response.getStatusCode());assertEquals(-1,response.getData().read());
            assertEquals("no-store",response.getResponseHeaders().get("Cache-Control"));
            assertNull(first.getWebViewClient().shouldInterceptRequest(first,request("https://doubleclick.net/",true)));
            assertNull(first.getWebViewClient().shouldInterceptRequest(first,request("https://example.org/script.js",false)));
            invoke(activity,"newTab",new Class<?>[]{String.class,boolean.class},Address.HOME,true);
            WebView second=web(activity);
            invoke(activity,"setAdBlockingEnabled",new Class<?>[]{boolean.class},false);
            assertNull(first.getWebViewClient().shouldInterceptRequest(first,ad));
            assertNull(second.getWebViewClient().shouldInterceptRequest(second,ad));
            invoke(activity,"setAdBlockingEnabled",new Class<?>[]{boolean.class},true);
            assertNotNull(first.getWebViewClient().shouldInterceptRequest(first,ad));
            assertNotNull(second.getWebViewClient().shouldInterceptRequest(second,ad));
        } finally { controller.pause().stop().destroy(); }
    }
    @Test public void disabledBlockerSurvivesBrowserRestart()throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        invoke(controller.get(),"setAdBlockingEnabled",new Class<?>[]{boolean.class},false);
        controller.pause().stop().destroy();
        ActivityController<MainActivity> next=Robolectric.buildActivity(MainActivity.class).setup();
        try {
            WebView view=web(next.get());
            assertNull(view.getWebViewClient().shouldInterceptRequest(view,request("https://doubleclick.net/banner.js",false)));
        } finally { next.pause().stop().destroy(); }
    }
}
