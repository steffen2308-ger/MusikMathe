package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewRenderProcessCompat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RendererRecoveryDeviceTest {
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private WebView web(MainActivity activity){try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("activeView");m.setAccessible(true);return (WebView)m.invoke(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private void click(UiDevice device,String text){UiObject2 button=device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(text),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))),15000);assertNotNull(text,button);button.click();}
    @Test public void nativeControlsRecoverAnActuallyStalledRenderer()throws Exception{
        Assume.assumeTrue(Build.VERSION.SDK_INT>=29&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)&&WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)&&WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER));
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();app.getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).commit();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");long idle=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);
        MainActivity activity=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));WebViewRenderProcessCompat[] process={null};
        try{
            CountDownLatch loaded=new CountDownLatch(1);main(()->{WebView view=web(activity);view.loadDataWithBaseURL("https://stalled.example/","<html><meta name='viewport' content='width=device-width,initial-scale=1'><body><button id='ready'>Testseite</button></body></html>","text/html","UTF-8",null);view.evaluateJavascript("document.readyState",v->loaded.countDown());});assertTrue(loaded.await(8,TimeUnit.SECONDS));
            assertTrue(device.wait(Until.hasObject(By.text("Testseite")),8000));main(()->{WebView view=web(activity);process[0]=WebViewCompat.getWebViewRenderProcess(view);view.evaluateJavascript("setTimeout(function(){while(true){}},100);",null);});Thread.sleep(500);device.click(device.getDisplayWidth()/2,device.getDisplayHeight()/2);
            assertTrue("An unresponsive page must leave a native exit",device.wait(Until.hasObject(By.text("Seite reagiert nicht")),18000));click(device,"Seite beenden");
            assertTrue(device.wait(Until.hasObject(By.text("Seite neu laden")),12000));click(device,"Zur Startseite");
            CountDownLatch recovered=new CountDownLatch(1);for(int attempt=0;attempt<40&&recovered.getCount()!=0;attempt++){main(()->web(activity).evaluateJavascript("location.href",value->{if(value.contains("android_asset/start.html"))recovered.countDown();}));recovered.await(200,TimeUnit.MILLISECONDS);}assertEquals("A new renderer must navigate after termination",0,recovered.getCount());
        }finally{main(()->{if(process[0]!=null)process[0].terminate();activity.finish();});Configurator.getInstance().setWaitForIdleTimeout(idle);}
    }
}
