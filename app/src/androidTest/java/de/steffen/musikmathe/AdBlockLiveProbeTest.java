package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in external probe. Uses the unmodified browser client and the live site's own results. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=26)
public class AdBlockLiveProbeTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private Object invoke(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);
    }
    private String js(WebView web,String expression)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] answer={null};
        instrumentation.runOnMainSync(()->web.evaluateJavascript(expression,value->{answer[0]=value;done.countDown();}));
        assertTrue("WebView callback",done.await(10,TimeUnit.SECONDS));
        return new JSONArray("["+answer[0]+"]").optString(0);
    }
    private int nativeCount(MainActivity activity)throws Exception{
        Object[] tab={null};instrumentation.runOnMainSync(()->{try{tab[0]=invoke(activity,"active",new Class<?>[0]);}catch(Exception e){throw new RuntimeException(e);}});
        Field field=tab[0].getClass().getDeclaredField("blockedRequests");field.setAccessible(true);
        return ((AtomicInteger)field.get(tab[0])).get();
    }
    private JSONObject snapshot(MainActivity activity,WebView web)throws Exception{
        String raw=js(web,"JSON.stringify({url:location.href,title:document.title,ready:document.readyState,result:document.querySelector('#adb_test_r')?.innerText||'',log:document.querySelector('#test_log')?.innerText||'',cosmetic:!!window.__bestBrowserAdblock,adsScript:typeof s_test_ads,pageadScript:typeof s_test_pagead})");
        return new JSONObject(raw).put("nativeBlocked",nativeCount(activity)).put("serverRules",AdBlocker.hostCount()).put("filterStatus",AdBlockResources.status());
    }
    private JSONObject runPage(MainActivity activity,WebView web)throws Exception{
        String url="https://adblock.turtlecute.org/?bestbrowser_probe="+System.nanoTime();
        instrumentation.runOnMainSync(()->{web.stopLoading();web.loadUrl(url);});
        long deadline=System.currentTimeMillis()+90000;JSONObject result;
        do{
            Thread.sleep(250);result=snapshot(activity,web);
            if(url.equals(result.optString("url"))&&result.optString("result").contains("not blocked"))return result;
        }while(System.currentTimeMillis()<deadline);
        throw new AssertionError("Live page did not finish: "+result);
    }
    @Test public void measureLivePageWithAndWithoutBlocking()throws Exception{
        Assume.assumeTrue("External network probe is opt-in", "true".equals(InstrumentationRegistry.getArguments().getString("adblockLive")));
        Context app=instrumentation.getTargetContext();
        app.getSharedPreferences("browser",0).edit().clear().commit();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        JSONObject report=new JSONObject().put("site","https://adblock.turtlecute.org/").put("appVersion",app.getPackageManager().getPackageInfo(app.getPackageName(),0).versionName);
        try{
            WebView[] holder={null};instrumentation.runOnMainSync(()->{try{holder[0]=(WebView)invoke(activity,"activeView",new Class<?>[0]);}catch(Exception e){throw new RuntimeException(e);}});
            WebView web=holder[0];report.put("webView",WebView.getCurrentWebViewPackage().versionName);
            JSONObject enabled=runPage(activity,web);report.put("enabled",enabled);
            instrumentation.runOnMainSync(()->{try{invoke(activity,"setAdBlockingEnabled",new Class<?>[]{boolean.class},false);}catch(Exception e){throw new RuntimeException(e);}});
            JSONObject disabled=runPage(activity,web);report.put("disabled",disabled);
            assertTrue("Bundled list missing: "+report,enabled.getInt("serverRules")>60000);
            assertTrue("Native blocking did not run on the live page: "+report,enabled.getInt("nativeBlocked")>=100);
            assertEquals("Toggle did not disable native blocking: "+report,0,disabled.getInt("nativeBlocked"));
            assertEquals("Live ad script should be blocked: "+report,"undefined",enabled.getString("adsScript"));
            assertEquals("Live pagead script should be blocked: "+report,"undefined",enabled.getString("pageadScript"));
            assertNotEquals("Disabled live script should load: "+report,"undefined",disabled.getString("adsScript"));
            assertNotEquals("Disabled live pagead script should load: "+report,"undefined",disabled.getString("pageadScript"));
            report.put("outcome","verified");
        }finally{
            if(!report.has("outcome"))report.put("outcome","failed; see test assertions");
            android.util.Log.i("AdBlockLiveProbe",report.toString());
            try(FileOutputStream out=app.openFileOutput("adblock-live-probe.json",Context.MODE_PRIVATE)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
