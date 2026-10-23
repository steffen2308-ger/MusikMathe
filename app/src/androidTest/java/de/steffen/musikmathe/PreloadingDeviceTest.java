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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real DOM and native controls with a deterministic transport; a separate probe uses WebView. */
@RunWith(AndroidJUnit4.class)
public class PreloadingDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private MainActivity activity;
    private WebView web;
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private Object invoke(String name,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private Field controllerField(){try{Field f=MainActivity.class.getDeclaredField("linkPreloader");f.setAccessible(true);return f;}catch(Exception e){throw new RuntimeException(e);}}
    private String js(String script)throws Exception{String[] result={null};CountDownLatch done=new CountDownLatch(1);main(()->web.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));assertTrue(done.await(8,TimeUnit.SECONDS));return result[0];}
    private void expect(String script,String expected)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);String actual;do{actual=js(script);if(expected.equals(actual))return;Thread.sleep(80);}while(System.nanoTime()<end);assertEquals(script,expected,actual);}
    private UiObject2 text(String label){UiObject2 item=device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(label),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))),8000);assertNotNull(label,item);return item;}
    private void preview(){Bitmap full=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(full);Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);String value=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int count=(value.length()+1799)/1800;for(int i=0;i<count;i++)Log.i("MenuPreview","preloading "+i+"/"+count+" "+value.substring(i*1800,Math.min(value.length(),(i+1)*1800)));if(small!=full)small.recycle();full.recycle();}
    private MainActivity launch(){return (MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private static final class Request implements PreloadQueue.Cancel {
        final String url;final Consumer<Boolean> done;boolean cancelled;
        Request(String url,Consumer<Boolean> done){this.url=url;this.done=done;}
        @Override public void cancel(){cancelled=true;}
    }
    private static final class FakeBackend implements LinkPreloader.Backend {
        final List<Request> requests=new ArrayList<>();int clears;
        @Override public boolean supported(){return true;}
        @Override public PreloadQueue.Cancel fetch(WebView view,String url,Consumer<Boolean> done){Request r=new Request(url,done);requests.add(r);return r;}
        @Override public void clear(){clears++;}
    }
    private void requests(FakeBackend backend,int count)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);int[] actual={0};do{main(()->actual[0]=backend.requests.size());if(actual[0]==count)return;Thread.sleep(80);}while(System.nanoTime()<end);assertEquals("Prefetch request count",count,actual[0]);}
    @Test public void currentPageOnlyWifiTransitionsAndNativeOptOutPersist()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).commit();
        activity=launch();AtomicBoolean wifi=new AtomicBoolean(true);FakeBackend backend=new FakeBackend();LinkPreloader[] controller={null};AtomicInteger pageLoads=new AtomicInteger();
        try{
            main(()->web=(WebView)invoke("activeView",new Class<?>[0]));expect("location.href","\"file:///android_asset/start.html\"");
            expect("document.querySelectorAll('nav[data-fixed] a svg').length","13");
            expect("Array.from(document.querySelectorAll('nav[data-fixed] a')).slice(0,5).map(function(a){return a.href;}).join('|')","\"https://www.google.com/|https://yandex.com/|https://search.inetol.net/|https://search.brave.com/|https://www.mojeek.com/\"");
            main(()->{
                try{LinkPreloader original=(LinkPreloader)controllerField().get(activity);original.destroy();controller[0]=new LinkPreloader(activity,()->new BrowserStore(activity).linkPreloadingEnabled(),url->url.contains("ads.example"),wifi::get,backend);controllerField().set(activity,controller[0]);}catch(Exception error){throw new RuntimeException(error);}
                WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){if(r.isForMainFrame()&&"preload.example".equals(r.getUrl().getHost())){pageLoads.incrementAndGet();String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body>Prefetch fixture<a href='/one#x'>Article one</a><a href='/two'>Article two</a><a href='/one#y'>Duplicate</a><a href='/logout'>Leave</a><a href='/file.zip'>File</a><a href='/save' download>Save</a><a href='http://preload.example/plain'>Plain</a><a href='https://ads.example/banner'>Ad</a></body></html>";return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}return delegate.shouldInterceptRequest(v,r);}
                    @Override public void onPageStarted(WebView v,String url,Bitmap icon){delegate.onPageStarted(v,url,icon);}
                    @Override public void onPageFinished(WebView v,String url){delegate.onPageFinished(v,url);}
                    @Override public void doUpdateVisitedHistory(WebView v,String url,boolean reload){delegate.doUpdateVisitedHistory(v,url,reload);}
                });invoke("navigate",new Class<?>[]{String.class},"https://preload.example/current");
            });
            expect("document.querySelectorAll('a').length","8");requests(backend,0);
            main(()->invoke("setPreloadingEnabled",new Class<?>[]{boolean.class},true));requests(backend,2);
            main(()->{assertEquals("https://preload.example/one",backend.requests.get(0).url);assertEquals("https://preload.example/two",backend.requests.get(1).url);wifi.set(false);controller[0].recheck();assertTrue(backend.requests.get(0).cancelled);assertTrue(backend.requests.get(1).cancelled);backend.requests.get(0).done.accept(true);});
            requests(backend,2);main(()->{wifi.set(true);controller[0].recheck();});requests(backend,4);
            assertEquals("Only the visible page is executed",1,pageLoads.get());assertEquals("Prefetch never adds visits",1,new BrowserStore(activity).read("history").size());
            main(()->invoke("showPreloadingSettings",new Class<?>[0]));text("Vorladen im WLAN");UiObject2 toggle=device.wait(Until.findObject(By.clazz("android.widget.Switch")),8000);assertNotNull(toggle);assertTrue(toggle.isChecked());instrumentation.waitForIdleSync();preview();toggle.click();
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.Switch").checked(false)),8000));instrumentation.waitForIdleSync();
            assertFalse(new BrowserStore(activity).linkPreloadingEnabled());main(()->{assertTrue(backend.requests.get(2).cancelled);assertTrue(backend.requests.get(3).cancelled);assertTrue(backend.clears>0);backend.requests.get(2).done.accept(true);});
            js("var a=document.createElement('a');a.href='/three';a.textContent='New';document.body.appendChild(a);true");main(()->controller[0].recheck());requests(backend,4);text("Fertig").click();
            main(()->invoke("setPreloadingEnabled",new Class<?>[]{boolean.class},true));requests(backend,6);
            main(()->invoke("newTab",new Class<?>[]{String.class,boolean.class},"about:blank",true));main(()->{assertTrue(backend.requests.get(4).cancelled);assertTrue(backend.requests.get(5).cancelled);});requests(backend,6);
            main(()->invoke("setPreloadingEnabled",new Class<?>[]{boolean.class},false));main(()->activity.finish());instrumentation.waitForIdleSync();activity=launch();assertFalse(new BrowserStore(activity).linkPreloadingEnabled());
        }finally{main(()->{if(activity!=null)activity.finish();});instrumentation.waitForIdleSync();}
    }
    @Test public void nativeWebViewPrefetchReportsAnOutcomeWhenSupported()throws Exception{
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).commit();activity=launch();
        LinkPreloader.NativeBackend backend=new LinkPreloader.NativeBackend();PreloadQueue.Cancel[] cancel={null};
        try{
            boolean[] supported={false};main(()->supported[0]=backend.supported());Log.i("MenuPreview","native-prefetch-supported="+supported[0]);Assume.assumeTrue("Installed WebView lacks native URL prefetch",supported[0]);
            CountDownLatch result=new CountDownLatch(1);AtomicBoolean accepted=new AtomicBoolean();
            main(()->{web=(WebView)invoke("activeView",new Class<?>[0]);cancel[0]=backend.fetch(web,"https://example.com/",success->{accepted.set(success);result.countDown();});});
            assertTrue("Native prefetch completes or reports ineligibility",result.await(25,TimeUnit.SECONDS));Log.i("MenuPreview","native-prefetch-accepted="+accepted.get());
            // Eligibility depends on the installed provider and network. Report it, never fake a cache hit.
            main(()->{cancel[0].cancel();backend.clear();});
        }finally{main(()->{if(cancel[0]!=null)cancel[0].cancel();backend.clear();activity.finish();});instrumentation.waitForIdleSync();}
    }
}
