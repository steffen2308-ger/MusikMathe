package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real start-page rendering, history ranking, safe text, private isolation and deletion. */
@RunWith(AndroidJUnit4.class)
public class HomePageDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private MainActivity activity;
    private WebView web;
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private Object invoke(String name,Class<?>[] types,Object...args){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private String js(String script)throws Exception{String[] result={null};CountDownLatch ready=new CountDownLatch(1);main(()->web.evaluateJavascript(script,value->{result[0]=value;ready.countDown();}));assertTrue(ready.await(8,TimeUnit.SECONDS));return result[0];}
    private void expect(String script,String expected)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);String actual;do{actual=js(script);if(expected.equals(actual))return;Thread.sleep(80);}while(System.nanoTime()<end);assertEquals(script,expected,actual);}
    private UiObject2 text(String label){UiObject2 item=device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(label),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))),8000);assertNotNull(label,item);return item;}
    private void preview(){
        Bitmap full=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(full);Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);
        String encoded=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int count=(encoded.length()+1799)/1800;
        for(int i=0;i<count;i++)Log.i("MenuPreview","home-compact "+i+"/"+count+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    @Test public void compactTopSitesAreSafeClickablePrivateAndRemovedWhenHistoryIsCleared()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");instrumentation.waitForIdleSync();
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();
        BrowserAppearance.saveNight(instrumentation.getTargetContext(),false);BrowserStore store=new BrowserStore(instrumentation.getTargetContext());
        for(int i=0;i<3;i++)store.visit("https://alpha.example/one","Alpha <img src=x onerror=window.homeInjected=1>");
        for(int i=0;i<2;i++)store.visit("https://www.alpha.example/two","Other Alpha page");
        for(int i=0;i<4;i++)store.visit("https://beta.example/","Beta");
        for(int i=0;i<3;i++)store.visit("https://gamma.example/","Gamma");store.visit("https://recent.example/","Only recent");
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));boolean privateOpen=false;
        try{
            main(()->web=(WebView)invoke("activeView",new Class<?>[0]));
            expect("location.href==='file:///android_asset/start.html'&&!!document.getElementById('frequent-links')&&document.getElementById('frequent-links').children.length===3","true");
            expect("Array.from(document.querySelectorAll('#frequent-links a')).map(function(a){return a.href;}).join('|')","\"https://alpha.example/one|https://beta.example/|https://gamma.example/\"");
            expect("document.querySelectorAll('#frequent-links img, #frequent-links script').length===0&&!window.homeInjected","true");
            expect("(function(){var a=document.querySelectorAll('nav[data-fixed] a'),r=a[0].getBoundingClientRect(),last=a[2].getBoundingClientRect();return r.height<=90&&Math.abs(r.top-last.top)<1&&last.right<=innerWidth+1;})()","true");
            CountDownLatch visited=new CountDownLatch(1);
            main(()->{WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(request.isForMainFrame()&&"alpha.example".equals(request.getUrl().getHost()))return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<html><title>Alpha</title><body>Alpha fixture</body></html>".getBytes(StandardCharsets.UTF_8)));return delegate.shouldInterceptRequest(view,request);}
                @Override public void onPageStarted(WebView view,String url,Bitmap icon){delegate.onPageStarted(view,url,icon);}
                @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);if("https://alpha.example/one".equals(url))visited.countDown();}
                @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){delegate.doUpdateVisitedHistory(view,url,reload);}
            });});
            js("document.querySelector('#frequent-links a').click();true");expect("location.href","\"https://alpha.example/one\"");expect("document.title","\"Alpha\"");assertTrue("Selected page navigation completed",visited.await(12,TimeUnit.SECONDS));
            main(()->invoke("navigate",new Class<?>[]{String.class},Address.HOME));expect("!!document.getElementById('frequent-links')&&document.getElementById('frequent-links').children.length===3","true");
            main(()->invoke("openPrivate",new Class<?>[]{boolean.class},true));privateOpen=true;PrivateTestSupport.unlock(device);assertTrue(device.wait(Until.hasObject(By.descStartsWith("Privater Tab")),15000));assertFalse("Normal top sites stay hidden in private areas",device.hasObject(By.textContains("Alpha <img")));
            UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),8000);assertNotNull(menu);menu.click();text("Alle privaten Tabs schließen").click();assertTrue(device.wait(Until.gone(By.descStartsWith("Privater Tab")),15000));privateOpen=false;
            expect("document.getElementById('frequent-links').children.length","3");text("Meistbesucht");instrumentation.waitForIdleSync();preview();
            assertEquals("Private home does not contribute visits",6,new BrowserStore(instrumentation.getTargetContext()).frequentSites().get(0).visits);
            main(()->invoke("clearData",new Class<?>[0]));text("Löschen").click();expect("document.getElementById('frequent-links').children.length","0");expect("document.getElementById('frequent-empty').hidden","false");assertTrue(new BrowserStore(instrumentation.getTargetContext()).frequentSites().isEmpty());
        }finally{
            if(privateOpen)try{UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),8000);if(menu!=null){menu.click();text("Alle privaten Tabs schließen").click();}}catch(Exception ignored){}
            main(()->{if(activity!=null)activity.finish();});instrumentation.waitForIdleSync();
        }
    }
}
