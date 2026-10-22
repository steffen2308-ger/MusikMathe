package de.steffen.musikmathe;

import android.content.Context;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Slow renderers must not acquire an ever-growing queue of optional DOM scans. */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class VideoMarkersSchedulingTest {
    private static final class DeferredWebView extends WebView {
        final List<ValueCallback<String>> callbacks=new ArrayList<>();
        int evaluations;
        DeferredWebView(Context context){super(context);layout(0,0,800,600);}
        @Override public boolean isShown(){return true;}
        @Override public String getUrl(){return "https://video.example/watch";}
        @Override public void evaluateJavascript(String script,ValueCallback<String> callback){evaluations++;callbacks.add(callback);}
        void answer(String value){assertFalse(callbacks.isEmpty());callbacks.remove(0).onReceiveValue(value);}
    }
    private void tick(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));}
    private String emptyReport(){return org.json.JSONObject.quote("{\"href\":\"https://video.example/watch\",\"width\":800,\"height\":600,\"rows\":[]}");}
    @Test public void repeatedRefreshesWaitForTheOutstandingRendererCallback(){
        Context context=RuntimeEnvironment.getApplication();FrameLayout host=new FrameLayout(context);DeferredWebView web=new DeferredWebView(context);
        VideoMarkers markers=new VideoMarkers(context,host,()->true,(view,row)->fail("No stale capture"));
        try{
            markers.attach(web);markers.resume();tick();assertEquals(1,web.evaluations);
            for(int i=0;i<100;i++){markers.changed(web);tick();}
            assertEquals("Refresh events must coalesce while JavaScript is pending",1,web.evaluations);assertEquals(1,web.callbacks.size());
            web.answer("null");tick();assertEquals("One fresh scan after the stale response",2,web.evaluations);
            web.answer("null");assertEquals("Install once, without overlapping requests",3,web.evaluations);
            for(int i=0;i<10;i++){markers.changed(web);tick();}assertEquals(3,web.evaluations);
            web.answer("true");tick();assertEquals(4,web.evaluations);web.answer(emptyReport());
            markers.pause();tick();assertEquals(4,web.evaluations);
        }finally{markers.destroy();web.destroy();}
    }
    @Test public void switchingBackToASlowTabDoesNotSendAnotherScanToItsRenderer(){
        Context context=RuntimeEnvironment.getApplication();FrameLayout host=new FrameLayout(context);DeferredWebView a=new DeferredWebView(context),b=new DeferredWebView(context);
        VideoMarkers markers=new VideoMarkers(context,host,()->true,(view,row)->fail("No stale capture"));
        try{
            markers.attach(a);markers.resume();tick();markers.attach(b);tick();markers.attach(a);tick();
            assertEquals(1,a.evaluations);assertEquals(1,b.evaluations);
            b.answer(emptyReport());tick();assertEquals(1,a.evaluations);
            a.answer(emptyReport());tick();assertEquals(2,a.evaluations);assertEquals(1,a.callbacks.size());
            markers.destroy();a.answer(emptyReport());tick();assertEquals(2,a.evaluations);assertEquals(1,b.evaluations);
        }finally{markers.destroy();a.destroy();b.destroy();}
    }
}
