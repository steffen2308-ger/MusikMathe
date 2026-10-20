package de.steffen.musikmathe;

import android.webkit.WebView;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class LinkPreloaderTest {
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
