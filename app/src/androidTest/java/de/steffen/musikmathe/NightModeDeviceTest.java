package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.View;
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
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real DayNight configuration changes must keep live WebViews and private tabs intact. */
@RunWith(AndroidJUnit4.class)
public class NightModeDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private MainActivity activity;
    private String stage="launch";
    private void main(Runnable run){instrumentation.runOnMainSync(run);}
    private UiObject2 text(String label){stage=label;UiObject2 item=device.wait(Until.findObject(By.text(label)),8000);assertNotNull(label,item);return item;}
    private void open(){UiObject2 button=device.wait(Until.findObject(By.desc("Menü")),8000);assertNotNull("Menu",button);button.click();text("Einstellungen");device.waitForIdle();}
    private void closed(){assertTrue("Native menu closes",device.wait(Until.gone(By.clazz("android.widget.ListView")),8000));instrumentation.waitForIdleSync();}
    private void scroll(String label)throws Exception{assertTrue("Scroll to "+label,new UiScrollable(new UiSelector().className("android.widget.ListView")).scrollIntoView(new UiSelector().text(label)));}
    private void toggle()throws Exception{open();scroll("Nachtmodus");text("Nachtmodus").click();closed();}
    private MainActivity launch(){return (MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private WebView web()throws Exception{WebView[] result={null};main(()->{try{Method method=MainActivity.class.getDeclaredMethod("activeView");method.setAccessible(true);result[0]=(WebView)method.invoke(activity);}catch(Exception e){throw new RuntimeException(e);}});return result[0];}
    private String js(WebView web,String script)throws Exception{String[] result={null};CountDownLatch done=new CountDownLatch(1);main(()->web.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));assertTrue("JavaScript callback",done.await(8,TimeUnit.SECONDS));return result[0];}
    private void expect(WebView web,String script,String expected)throws Exception{long until=SystemClock.uptimeMillis()+8000;String actual;do{actual=js(web,script);if(expected.equals(actual))return;Thread.sleep(80);}while(SystemClock.uptimeMillis()<until);assertEquals(script,expected,actual);}
    private void night(boolean expected)throws Exception{long until=SystemClock.uptimeMillis()+8000;boolean[] actual={false};do{main(()->actual[0]=BrowserAppearance.night(activity));if(actual[0]==expected)return;Thread.sleep(80);}while(SystemClock.uptimeMillis()<until);assertEquals("Night configuration",expected,actual[0]);}
    private void preview(String name){
        Bitmap full=instrumentation.getUiAutomation().takeScreenshot();if(full==null)return;
        Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);
        String value=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int count=(value.length()+1799)/1800;
        for(int i=0;i<count;i++)Log.i("NightPreview",name+" "+i+"/"+count+" "+value.substring(i*1800,Math.min(value.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    private int pixel(WebView web,double x,double y){int[] point={0,0};main(()->{web.getLocationOnScreen(point);point[0]+=(int)(web.getWidth()*x);point[1]+=(int)(web.getHeight()*y);});Bitmap image=instrumentation.getUiAutomation().takeScreenshot();assertNotNull("Rendered screenshot",image);try{return image.getPixel(point[0],point[1]);}finally{image.recycle();}}
    @Test public void nightModeKeepsFormsAndMediaAndPersistsAcrossPrivateAndNormalActivities()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");main(()->{BrowserAppearance.saveNight(instrumentation.getTargetContext(),false);BrowserAppearance.sync(instrumentation.getTargetContext());});
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();activity=launch();boolean privateOpen=false;
        try{
            WebView original=web();CountDownLatch loaded=new CountDownLatch(1);AtomicInteger requests=new AtomicInteger();
            main(()->{WebViewClient delegate=original.getWebViewClient();original.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    if(request.isForMainFrame()&&request.getUrl().toString().startsWith("https://night.example/")){
                        requests.incrementAndGet();String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;min-height:250vh;background:#fff;color:#111}input{height:40px}canvas{position:fixed;right:0;top:0;width:80px;height:50px}</style></head><body><input id='kept' inputmode='none'><canvas id='media' width='80' height='50'></canvas><script>var c=document.getElementById('media').getContext('2d');c.fillStyle='#ff0000';c.fillRect(0,0,80,50);window.fixtureReady=true;</script></body></html>";
                        return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                    }return delegate.shouldInterceptRequest(view,request);
                }
                @Override public void onPageStarted(WebView view,String url,Bitmap icon){delegate.onPageStarted(view,url,icon);}
                @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);if(url.startsWith("https://night.example/"))loaded.countDown();}
                @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){delegate.doUpdateVisitedHistory(view,url,reload);}
            });original.loadUrl("https://night.example/form");});
            assertTrue("Fixture loaded",loaded.await(12,TimeUnit.SECONDS));expect(original,"Boolean(window.fixtureReady)","true");js(original,"document.getElementById('kept').value='keep-this';window.scrollTo(0,450);true");
            toggle();night(true);assertSame("No WebView recreation",original,web());expect(original,"document.getElementById('kept').value","\"keep-this\"");assertEquals("No reload",1,requests.get());assertTrue("Scroll position kept",Integer.parseInt(js(original,"Math.round(scrollY)"))>400);
            if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)){
                expect(original,"matchMedia('(prefers-color-scheme:dark)').matches","true");main(()->assertTrue(WebSettingsCompat.isAlgorithmicDarkeningAllowed(original.getSettings())));
            }
            Thread.sleep(500);int background=pixel(original,.5,.6);assertTrue("Rendered page is dark: "+Integer.toHexString(background),Color.red(background)<150&&Color.green(background)<150&&Color.blue(background)<150);
            JSONArray centre=new JSONArray(new JSONArray("["+js(original,"JSON.stringify((function(){var r=document.getElementById('media').getBoundingClientRect();return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerHeight];})())")+"]").getString(0));
            int red=pixel(original,centre.getDouble(0),centre.getDouble(1));assertTrue("Canvas/media colours stay red: "+Integer.toHexString(red),Color.red(red)>200&&Color.green(red)<60&&Color.blue(red)<60);
            open();scroll("Nachtmodus");assertNotNull("Night checkmark",device.findObject(By.desc("Nachtmodus").checked(true)));device.pressBack();closed();
            // A private process must start dark, save only the appearance option, and
            // return to the same normal WebView when its tabs are closed.
            open();text("Neuer privater Tab").click();privateOpen=true;assertTrue(device.wait(Until.hasObject(By.descStartsWith("Privater Tab")),15000));
            open();scroll("Nachtmodus");assertNotNull("Private process shares night appearance",device.findObject(By.desc("Nachtmodus").checked(true)));text("Nachtmodus").click();closed();
            open();text("Alle privaten Tabs schließen").click();assertTrue(device.wait(Until.gone(By.descStartsWith("Privater Tab")),15000));privateOpen=false;night(false);
            assertSame(original,web());expect(original,"document.getElementById('kept').value","\"keep-this\"");assertEquals(1,requests.get());
            if(WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))expect(original,"matchMedia('(prefers-color-scheme:dark)').matches","false");
            toggle();night(true);stage="Navigate home before relaunch";
            UiObject2 homeButton=device.wait(Until.findObject(By.desc("Startseite")),8000);assertNotNull(homeButton);homeButton.click();
            expect(original,"location.href",org.json.JSONObject.quote(Address.HOME));expect(original,"document.documentElement.classList.contains('bestbrowser-night')","true");
            // Persist through the actual browser navigation/lifecycle. Writing test
            // preferences while an Activity is still finishing races its onPause save.
            main(activity::finish);instrumentation.waitForIdleSync();stage="Relaunch dark home";
            activity=launch();night(true);WebView home=web();expect(home,"document.documentElement.classList.contains('bestbrowser-night')","true");expect(home,"getComputedStyle(document.body).backgroundColor","\"rgb(20, 41, 37)\"");preview("home");
            open();preview("menu");text("Einstellungen").click();text("Über BestBrowser");preview("settings");scroll("Nachtmodus: Ein");text("Nachtmodus: Ein").click();night(false);
            expect(home,"document.documentElement.classList.contains('bestbrowser-light')","true");expect(home,"getComputedStyle(document.body).backgroundColor","\"rgb(248, 250, 249)\"");
        }catch(Throwable error){if(!privateOpen)try{preview("failure");}catch(Exception ignored){}throw new AssertionError("Night mode stage: "+stage,error);
        }finally{
            if(privateOpen)try{device.pressBack();open();text("Alle privaten Tabs schließen").click();}catch(Exception ignored){}
            main(()->{BrowserAppearance.saveNight(instrumentation.getTargetContext(),false);BrowserAppearance.sync(instrumentation.getTargetContext());if(activity!=null)activity.finish();});instrumentation.waitForIdleSync();
        }
    }
}
