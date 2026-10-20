package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.PermissionRequest;
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
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real native consent UI and configured WebChromeClient; no Netflix account or licence server. */
@RunWith(AndroidJUnit4.class)
public class ProtectedContentDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private MainActivity activity;
    private WebView web;
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(80);}fail("Protected-content request did not settle");}
    private void click(String label){UiObject2 item=device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(label),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))),8000);assertNotNull(label,item);item.click();}
    private void ready()throws Exception{
        CountDownLatch loaded=new CountDownLatch(1);
        main(()->{try{
            Method method=MainActivity.class.getDeclaredMethod("activeView");method.setAccessible(true);web=(WebView)method.invoke(activity);
            WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    if(request.isForMainFrame()&&"media.example".equals(request.getUrl().getHost()))return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Medien-Testseite</title><h1>Medien-Testseite</h1></html>".getBytes(StandardCharsets.UTF_8)));
                    return delegate.shouldInterceptRequest(view,request);
                }
                @Override public void onPageStarted(WebView view,String url,Bitmap icon){delegate.onPageStarted(view,url,icon);}
                @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){delegate.doUpdateVisitedHistory(view,url,reload);}
                @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);if(url.equals("https://media.example/watch"))loaded.countDown();}
            });web.loadUrl("https://media.example/watch");
        }catch(Exception e){throw new RuntimeException(e);}});
        assertTrue("HTTPS fixture loaded",loaded.await(12,TimeUnit.SECONDS));
    }
    private static final class MediaRequest extends PermissionRequest {
        volatile boolean denied;
        volatile String[] granted;
        @Override public Uri getOrigin(){return Uri.parse("https://media.example/");}
        @Override public String[] getResources(){return new String[]{RESOURCE_PROTECTED_MEDIA_ID};}
        @Override public void grant(String[] resources){granted=resources;}
        @Override public void deny(){denied=true;}
    }
    private MediaRequest request(){MediaRequest request=new MediaRequest();main(()->web.getWebChromeClient().onPermissionRequest(request));return request;}
    private void prompt(){assertTrue("Protected content has its own prompt",device.wait(Until.hasObject(By.text("Geschützte Inhalte")),8000));assertTrue(device.hasObject(By.textContains("media.example")));}
    private void closed(){assertTrue(device.wait(Until.gone(By.text("Geschützte Inhalte")),8000));}
    private void preview(){
        Bitmap full=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(full);Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);
        String encoded=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int count=(encoded.length()+1799)/1800;
        for(int i=0;i<count;i++)Log.i("MenuPreview","protected "+i+"/"+count+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    @Test public void protectedContentConsentCanBeAllowedDeniedAndCancelledByNavigation()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try{
            ready();MediaRequest denied=request();prompt();assertNull(denied.granted);assertFalse(denied.denied);preview();click("Ablehnen");await(()->denied.denied);assertNull(denied.granted);closed();
            MediaRequest allowed=request();prompt();assertNull(allowed.granted);click("Erlauben");await(()->allowed.granted!=null);assertArrayEquals(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID},allowed.granted);assertFalse(allowed.denied);closed();
            MediaRequest cancelled=request();prompt();main(()->web.loadUrl("https://media.example/other"));await(()->cancelled.denied);assertNull(cancelled.granted);closed();assertFalse("Settled consent must not receive later denial",allowed.denied);
        }finally{main(activity::finish);}
    }
}
