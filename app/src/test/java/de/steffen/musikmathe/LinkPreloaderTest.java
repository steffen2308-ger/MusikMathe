package de.steffen.musikmathe;

import android.webkit.WebView;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28) @org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
public class LinkPreloaderTest {
    @Test public void repeatedLifecycleCallbacksCoalesceLinkScansButNewPagesScanImmediately(){
        class Page extends WebView {
            int scans;String url="https://feed.example/";
            Page(){super(RuntimeEnvironment.getApplication());}
            @Override public String getUrl(){return url;}
            @Override public void evaluateJavascript(String script,android.webkit.ValueCallback<String> callback){scans++;callback.onReceiveValue(org.json.JSONObject.quote("{\"page\":\""+url+"\",\"links\":[]}"));}
        }
        LinkPreloader.Backend backend=new LinkPreloader.Backend(){
            public boolean supported(){return true;}
            public PreloadQueue.Cancel fetch(WebView view,String url,Consumer<Boolean> done){throw new AssertionError("Empty fixture has no links");}
            public void clear(){}
        };
        Page page=new Page();LinkPreloader controller=new LinkPreloader(RuntimeEnvironment.getApplication(),()->true,url->false,()->true,backend);
        try{
            controller.page(page,page.url);assertEquals(1,page.scans);
            for(int i=0;i<100;i++)controller.recheck();assertEquals(1,page.scans);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(10));assertEquals(2,page.scans);
            page.url="https://next.example/";controller.page(page,page.url);assertEquals(3,page.scans);
        }finally{controller.destroy();page.destroy();}
    }
    @Test public void unsupportedProviderStaysIdleAndOptOutTakesPriority(){
        AtomicBoolean enabled=new AtomicBoolean(true);
        LinkPreloader.Backend unavailable=new LinkPreloader.Backend(){
            @Override public boolean supported(){return false;}
            @Override public PreloadQueue.Cancel fetch(WebView view,String url,Consumer<Boolean> done){throw new AssertionError("Unsupported provider must never prefetch");}
            @Override public void clear(){}
        };
        WebView web=new WebView(RuntimeEnvironment.getApplication());
        LinkPreloader controller=new LinkPreloader(RuntimeEnvironment.getApplication(),enabled::get,url->false,()->true,unavailable);
        try{
            controller.page(web,Address.HOME);assertTrue(controller.status().contains("aktualisieren"));
            enabled.set(false);controller.recheck();assertEquals("Ausgeschaltet",controller.status());
            controller.navigation("https://example.org/");assertEquals("Ausgeschaltet",controller.status());
        }finally{controller.destroy();web.destroy();}
    }
}
