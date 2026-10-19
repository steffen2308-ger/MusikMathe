package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises native menu actions, accessible state and the fixed settings footer. */
@RunWith(AndroidJUnit4.class)
public class BrowserMenuDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private String stage="launch";
    private int openings;
    private UiObject2 text(String label){stage="menu #"+openings+" text "+label;UiObject2 item=device.wait(Until.findObject(By.text(label)),6000);assertNotNull(label,item);return item;}
    private UiObject2 description(String label){stage="menu #"+openings+" description "+label;UiObject2 item=device.wait(Until.findObject(By.desc(label)),6000);assertNotNull(label,item);return item;}
    private void open(){openings++;description("Menü").click();text("Neuer Tab");text("Einstellungen");device.waitForIdle();instrumentation.waitForIdleSync();}
    private void closed(){assertTrue("Action closes the native menu",device.wait(Until.gone(By.clazz("android.widget.ListView").hasDescendant(By.text("Neuer Tab"))),6000));instrumentation.waitForIdleSync();}
    private void scroll(String label)throws Exception{assertTrue("Scroll to "+label,new UiScrollable(new UiSelector().className("android.widget.ListView")).scrollIntoView(new UiSelector().text(label)));}
    private WebView web(MainActivity activity)throws Exception{Method method=MainActivity.class.getDeclaredMethod("activeView");method.setAccessible(true);return (WebView)method.invoke(activity);}
    private void preview(String name){
        Bitmap full=instrumentation.getUiAutomation().takeScreenshot();if(full==null){Log.w("MenuPreview","Screenshot unavailable: "+name);return;}
        Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);
        String encoded=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int chunks=(encoded.length()+1799)/1800;
        for(int i=0;i<chunks;i++)Log.i("MenuPreview",name+" "+i+"/"+chunks+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    @Test public void quickActionsReflectPageStateAndSettingsStayReachableAfterScrolling()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try{
            WebView[] holder={null};instrumentation.runOnMainSync(()->{try{holder[0]=web(activity);}catch(Exception e){throw new RuntimeException(e);}});
            open();assertFalse("Home bookmark is disabled",description("Lesezeichen hinzufügen").isEnabled());assertFalse("Home sharing is disabled",description("Seite teilen").isEnabled());preview("top");
            device.pressBack();closed();
            String url="https://menu.example/page";CountDownLatch loaded=new CountDownLatch(1);
            instrumentation.runOnMainSync(()->{
                WebViewClient original=holder[0].getWebViewClient();
                holder[0].setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                        if(request.getUrl().toString().startsWith("https://menu.example/"))return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<html><title>Menüprobe</title><body>Menüprobe</body></html>".getBytes(StandardCharsets.UTF_8)));
                        return original.shouldInterceptRequest(view,request);
                    }
                    @Override public void onPageStarted(WebView view,String target,Bitmap icon){original.onPageStarted(view,target,icon);}
                    @Override public void onPageFinished(WebView view,String target){original.onPageFinished(view,target);if(url.equals(target))loaded.countDown();}
                    @Override public void doUpdateVisitedHistory(WebView view,String target,boolean reload){original.doUpdateVisitedHistory(view,target,reload);}
                });holder[0].loadUrl(url);
            });
            assertTrue("Fixture loaded",loaded.await(12,TimeUnit.SECONDS));
            open();assertTrue("Website sharing is enabled",description("Seite teilen").isEnabled());description("Lesezeichen hinzufügen").click();closed();
            assertTrue("Bookmark stored after clicking the quick action",new BrowserStore(instrumentation.getTargetContext()).bookmarked(url));
            open();description("Lesezeichen entfernen");scroll("Desktopwebsite");text("Desktopwebsite").click();closed();
            instrumentation.runOnMainSync(()->assertTrue(holder[0].getSettings().getUserAgentString().contains("X11; Linux x86_64")));
            open();scroll("Desktopwebsite");assertNotNull("Desktop checkbox accessible state",device.findObject(By.desc("Desktopwebsite").checked(true)));
            scroll("VPN");text("Werbeblocker");text("Ein");preview("tools");
            // The settings action is outside the scrolled list and remains visible at its end.
            text("Einstellungen").click();text("Browserdaten löschen");text("Suchmaschine");text("Über BestBrowser").click();text("BestBrowser · 0.4.13");text("OK").click();
            instrumentation.runOnMainSync(()->assertEquals("Menu dismissal and settings do not navigate",url,holder[0].getUrl()));
        }catch(Throwable error){
            try{preview("failure");}catch(Exception ignored){}
            throw new AssertionError("Menu test stage: "+stage,error);
        }finally{instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();}
    }
}
