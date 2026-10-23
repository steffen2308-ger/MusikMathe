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
        String[] value={null};CountDownLatch done=new CountDownLatch(1);main(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("activeView");m.setAccessible(true);WebView web=(WebView)m.invoke(activity);web.evaluateJavascript("JSON.stringify((function(){var v=document.querySelector('video');return {video:!!v,time:v?v.currentTime:0,ready:v?v.readyState:0,paused:v?v.paused:true,error:v&&v.error?v.error.code:0}})())",result->{value[0]=result;done.countDown();});}catch(Exception error){throw new RuntimeException(error);}});assertTrue("Player JavaScript must respond",done.await(8,TimeUnit.SECONDS));return new JSONObject(String.valueOf(new JSONTokener(value[0]).nextValue()));
    }
    @Test public void publicDemonstrationAdvancesAfterANativePlayGesture()throws Exception{
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("vimeoLive","false")));
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();app.getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).commit();UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        long idle=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);MainActivity activity=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://player.vimeo.com/video/76979871?h=8272103f6e&quality=360p")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));JSONObject report=new JSONObject();
        try{
            long end=SystemClock.uptimeMillis()+20000;JSONObject current;
            do{Thread.sleep(300);current=state(activity);}while(!current.optBoolean("video")&&SystemClock.uptimeMillis()<end);
            report.put("initial",current);Assume.assumeTrue("No public Vimeo demonstration was delivered; see vimeo-playback report",current.optBoolean("video"));
            UiObject2 play=device.wait(Until.findObject(By.desc(Pattern.compile("(?i)^(play|play video|abspielen|video abspielen)$"))),8000);assertNotNull("Public player must expose its native Play control",play);play.click();
            end=SystemClock.uptimeMillis()+18000;double first=current.optDouble("time");do{Thread.sleep(300);current=state(activity);}while((current.optBoolean("paused")||current.optDouble("time")<first+.5)&&current.optInt("error")==0&&SystemClock.uptimeMillis()<end);
            report.put("playing",current);assertEquals("Public demonstration media error",0,current.optInt("error"));assertFalse("Video must play after the native gesture",current.optBoolean("paused"));assertTrue("Playback time must actually advance",current.optDouble("time")>=first+.5);
        }finally{Log.i("MenuPreview","vimeo-playback "+report);Configurator.getInstance().setWaitForIdleTimeout(idle);main(activity::finish);}
    }
}
