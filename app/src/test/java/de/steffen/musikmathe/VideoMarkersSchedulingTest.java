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
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Slow renderers must not acquire an ever-growing queue of optional DOM scans. */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class VideoMarkersSchedulingTest {
    private static final class DeferredWebView extends WebView {
        final List<ValueCallback<String>> callbacks=new ArrayList<>();
        int evaluations;
        DeferredWebView(Context context){
            super(context);
            // Robolectric's WebView provider has a no-op setFrame delegate. Supply
            // the synthetic viewport directly; real geometry is covered on devices.
            org.robolectric.util.ReflectionHelpers.setField(this,"mRight",800);
            org.robolectric.util.ReflectionHelpers.setField(this,"mBottom",600);
        }
        @Override public boolean isShown(){return true;}
        @Override public String getUrl(){return "https://video.example/watch";}
        @Override public void evaluateJavascript(String script,ValueCallback<String> callback){evaluations++;callbacks.add(callback);}
        void answer(String value){assertFalse(callbacks.isEmpty());callbacks.remove(0).onReceiveValue(value);}
    }
    private void tick(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));}
    private void assertReady(VideoMarkers markers,DeferredWebView web){
        assertTrue("Measured width: "+web.getWidth(),web.getWidth()>0);assertTrue("Measured height: "+web.getHeight(),web.getHeight()>0);
        assertTrue(web.isShown());assertTrue(Address.isWeb(web.getUrl()));
        try{java.lang.reflect.Method ready=VideoMarkers.class.getDeclaredMethod("ready");ready.setAccessible(true);assertEquals("Scheduling fixture is a ready web page",Boolean.TRUE,ready.invoke(markers));}catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }
    private String emptyReport(){return org.json.JSONObject.quote("{\"href\":\"https://video.example/watch\",\"width\":800,\"height\":600,\"rows\":[]}");}
    @Test public void repeatedRefreshesWaitForTheOutstandingRendererCallback(){
        Context context=RuntimeEnvironment.getApplication();FrameLayout host=new FrameLayout(context);DeferredWebView web=new DeferredWebView(context);
        VideoMarkers markers=new VideoMarkers(context,host,()->true,(view,row)->fail("No stale capture"));
        try{
            markers.attach(web);markers.resume();assertReady(markers,web);tick();assertEquals(1,web.evaluations);
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
            markers.attach(a);markers.resume();assertReady(markers,a);tick();markers.attach(b);assertReady(markers,b);tick();markers.attach(a);tick();
            assertEquals(1,a.evaluations);assertEquals(1,b.evaluations);
            b.answer(emptyReport());tick();assertEquals(1,a.evaluations);
            a.answer(emptyReport());tick();assertEquals(2,a.evaluations);assertEquals(1,a.callbacks.size());
            markers.destroy();a.answer(emptyReport());tick();assertEquals(2,a.evaluations);assertEquals(1,b.evaluations);
        }finally{markers.destroy();a.destroy();b.destroy();}
    }
}
