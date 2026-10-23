package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Opt-in playback proof uses Vimeo's public player demonstration, without user videos. */
@RunWith(AndroidJUnit4.class)
public class VimeoPlaybackDeviceTest {
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private JSONObject state(MainActivity activity)throws Exception{
        String[] value={null};CountDownLatch done=new CountDownLatch(1);main(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("activeView");m.setAccessible(true);WebView web=(WebView)m.invoke(activity);web.evaluateJavascript("JSON.stringify((function(){var v=document.querySelector('video'),buttons=Array.from(document.querySelectorAll('button,[role=button]')),play=null;buttons.some(function(b){var label=b.getAttribute('aria-label')||b.getAttribute('title')||b.textContent||'',r=b.getBoundingClientRect();if((/\\b(play|abspielen|wiedergeben)\\b/i.test(label)||b.hasAttribute('data-play-button')||/PlayButton/.test(String(b.className)))&&r.width>0&&r.height>0&&r.top>=0&&r.bottom<=innerHeight){play={x:r.left+r.width/2,y:r.top+r.height/2};return true;}return false;});return {video:!!v,time:v?v.currentTime:0,ready:v?v.readyState:0,paused:v?v.paused:true,error:v&&v.error?v.error.code:0,play:play,viewportWidth:innerWidth,viewportHeight:innerHeight,buttons:buttons.slice(0,12).map(b=>b.getAttribute('aria-label')||b.getAttribute('title')||(b.textContent||'').slice(0,60)),body:document.body?document.body.innerText.slice(0,1100):'',drm:!!(window.playerConfig&&((playerConfig.video&&(playerConfig.video.drm||playerConfig.video.is_drm||playerConfig.video.files&&playerConfig.video.files.drm))||(playerConfig.request&&playerConfig.request.files&&playerConfig.request.files.drm))),fileTypes:window.playerConfig&&playerConfig.request&&playerConfig.request.files?Object.keys(playerConfig.request.files):[]}})())",result->{value[0]=result;done.countDown();});}catch(Exception error){throw new RuntimeException(error);}});assertTrue("Player JavaScript must respond",done.await(8,TimeUnit.SECONDS));return new JSONObject(String.valueOf(new JSONTokener(value[0]).nextValue()));
    }
    private boolean allowProtectedDemo(UiDevice device){
        if(!device.hasObject(By.text("Geschützte Inhalte")))return false;
        UiObject2 allow=device.findObject(By.text(Pattern.compile("Erlauben",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)));if(allow==null)return false;allow.click();Log.i("MenuPreview","vimeo-demo-protected-id-consent=true");return true;
    }
    @Test public void publicDemonstrationAdvancesAfterANativePlayGesture()throws Exception{
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("vimeoLive","false")));
        String mode=InstrumentationRegistry.getArguments().getString("vimeoPlaybackMode","baseline");Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();app.getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).putBoolean("ad_blocking",!mode.equals("no-adblock")&&!mode.equals("no-extras")).commit();UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        long idle=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);MainActivity activity=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));JSONObject report=new JSONObject();
        android.content.pm.PackageInfo provider=androidx.webkit.WebViewCompat.getCurrentWebViewPackage(app);Log.i("MenuPreview","vimeo-runtime Android="+android.os.Build.VERSION.RELEASE+" provider="+(provider==null?"unknown":provider.packageName+" "+provider.versionName)+" widevine="+android.media.MediaDrm.isCryptoSchemeSupported(new java.util.UUID(0xedef8ba979d64aceL,0xa3c827dcd51d21edL)));
        try{
            main(()->{try{
                java.lang.reflect.Method active=MainActivity.class.getDeclaredMethod("activeView");active.setAccessible(true);WebView web=(WebView)active.invoke(activity);
                if(mode.equals("chrome-agent")||mode.equals("no-extras"))web.getSettings().setUserAgentString(web.getSettings().getUserAgentString().replace("; wv","").replace(" Version/4.0",""));
                android.webkit.WebViewClient original=web.getWebViewClient();web.setWebViewClient(new android.webkit.WebViewClient(){
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){return original.shouldInterceptRequest(v,r);}
                    @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){original.onPageStarted(v,url,icon);}
                    @Override public void onPageFinished(WebView v,String url){original.onPageFinished(v,url);}
                    @Override public void doUpdateVisitedHistory(WebView v,String url,boolean reload){original.doUpdateVisitedHistory(v,url,reload);}
                    @Override public void onReceivedHttpError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse response){Log.i("MenuPreview","vimeo-http "+response.getStatusCode()+" "+r.getUrl().getHost()+r.getUrl().getPath());original.onReceivedHttpError(v,r,response);}
                    @Override public void onReceivedError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError error){Log.i("MenuPreview","vimeo-network "+error.getErrorCode()+" "+error.getDescription()+" "+r.getUrl().getHost()+r.getUrl().getPath());original.onReceivedError(v,r,error);}
                    @Override public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail detail){return original.onRenderProcessGone(v,detail);}
                });
                java.lang.reflect.Method navigate=MainActivity.class.getDeclaredMethod("navigate",String.class);navigate.setAccessible(true);navigate.invoke(activity,"https://player.vimeo.com/video/76979871?h=8272103f6e"+(mode.equals("auto-quality")?"":"&quality=360p"));
            }catch(Exception error){throw new RuntimeException(error);}});
            long end=SystemClock.uptimeMillis()+20000;JSONObject current;
            do{Thread.sleep(300);current=state(activity);}while((!current.optBoolean("video")||current.isNull("play"))&&SystemClock.uptimeMillis()<end);
            allowProtectedDemo(device);report.put("initial",current);Assume.assumeTrue("No public Vimeo demonstration was delivered; see vimeo-playback report",current.optBoolean("video"));
            UiObject2 play=device.findObject(By.desc(Pattern.compile("(?i).*\\b(play|abspielen|wiedergeben)\\b.*")));
            if(play!=null)play.click();else{
                JSONObject point=current.optJSONObject("play");assertNotNull("Public player must expose its visible Play control: "+current,point);int[] location=new int[2];double[] scale=new double[2];JSONObject viewport=current;
                main(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("activeView");m.setAccessible(true);WebView web=(WebView)m.invoke(activity);web.getLocationOnScreen(location);scale[0]=web.getWidth()/viewport.optDouble("viewportWidth",1);scale[1]=web.getHeight()/viewport.optDouble("viewportHeight",1);}catch(Exception error){throw new RuntimeException(error);}});
                device.click(location[0]+(int)Math.round(point.getDouble("x")*scale[0]),location[1]+(int)Math.round(point.getDouble("y")*scale[1]));
            }
            end=SystemClock.uptimeMillis()+18000;double first=current.optDouble("time");do{Thread.sleep(300);allowProtectedDemo(device);current=state(activity);}while((current.optBoolean("paused")||current.optDouble("time")<first+.5)&&current.optInt("error")==0&&SystemClock.uptimeMillis()<end);
            report.put("playing",current);assertEquals("Public demonstration media error",0,current.optInt("error"));assertFalse("Video must play after the native gesture",current.optBoolean("paused"));assertTrue("Playback time must actually advance",current.optDouble("time")>=first+.5);
        }finally{Log.i("MenuPreview","vimeo-playback-mode "+mode);Log.i("MenuPreview","vimeo-playback-initial "+report.optJSONObject("initial"));Log.i("MenuPreview","vimeo-playback-playing "+report.optJSONObject("playing"));Configurator.getInstance().setWaitForIdleTimeout(idle);main(activity::finish);}
    }
}
