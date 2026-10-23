package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in live diagnosis of the exact reported gallery; no private profile or account. */
@RunWith(AndroidJUnit4.class)
public class VimeoShowcaseProbeTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MainActivity activity;
    private Object call(String method,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(activity,args);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private JSONObject inspect(String stage)throws Exception{
        JSONObject report=new JSONObject().put("stage",stage);long started=SystemClock.elapsedRealtime();CountDownLatch ui=new CountDownLatch(1),js=new CountDownLatch(1);String[] value={null};
        handler.post(()->{ui.countDown();try{WebView web=(WebView)call("activeView",new Class<?>[0]);web.evaluateJavascript("JSON.stringify({href:location.href,title:document.title,ready:document.readyState,nodes:document.getElementsByTagName('*').length,links:document.links.length,frameCount:document.querySelectorAll('iframe').length,frames:Array.from(document.querySelectorAll('iframe')).slice(0,2).map(f=>({host:new URL(f.src,location.href).hostname,width:f.clientWidth,height:f.clientHeight,loading:f.loading})),videos:Array.from(document.querySelectorAll('video')).slice(0,4).map(v=>({ready:v.readyState,time:v.currentTime,paused:v.paused,error:v.error&&v.error.code,width:v.videoWidth,height:v.videoHeight})),resources:performance.getEntriesByType('resource').length,cosmetic:!!window.__bestBrowserAdblock})",result->{value[0]=result;js.countDown();});}catch(Exception error){value[0]=error.toString();js.countDown();}});
        report.put("uiResponsive",ui.await(4,TimeUnit.SECONDS));report.put("jsResponsive",js.await(8,TimeUnit.SECONDS));report.put("latencyMs",SystemClock.elapsedRealtime()-started);report.put("dom",value[0]);Log.i("VimeoShowcase",report.toString());return report;
    }
    @Test public void diagnoseReportedShowcase()throws Exception{
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("vimeoShowcaseProbe","false")));
        String mode=InstrumentationRegistry.getArguments().getString("vimeoProbeMode","baseline");Context app=instrumentation.getTargetContext();
        boolean ads=!mode.equals("no-adblock")&&!mode.equals("no-extras"),preload=!mode.equals("no-prefetch")&&!mode.equals("no-extras");
        app.getSharedPreferences("browser",0).edit().clear().putBoolean("ad_blocking",ads).putBoolean("wifi_link_preloading",preload).commit();
        UiDevice device=UiDevice.getInstance(instrumentation);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        Log.i("VimeoShowcase","mode="+mode);
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.runOnMainSync(()->{try{
            if(mode.equals("no-markers")||mode.equals("no-extras"))((VideoMarkers)field("videoMarkers")).pause();
            WebView web=(WebView)call("activeView",new Class<?>[0]);if(mode.equals("chrome-agent"))web.getSettings().setUserAgentString(web.getSettings().getUserAgentString().replace("; wv","").replace(" Version/4.0",""));
            call("navigate",new Class<?>[]{String.class},"https://vimeo.com/showcase/2600007");
        }catch(Exception error){throw new RuntimeException(error);}});
        try{
            Thread.sleep(10000);inspect("loaded");
            java.io.File screenshot=new java.io.File(app.getFilesDir(),"vimeo-showcase-"+mode+".png");device.takeScreenshot(screenshot);
            Thread.sleep(6000);inspect("settled");
        }finally{handler.post(()->{if(activity!=null)activity.finish();});}
    }
}
