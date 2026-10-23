package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Live provider access is reported separately from deterministic frame/source tests. */
@RunWith(AndroidJUnit4.class)
public class VimeoLiveProbeTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private JSONObject snapshot(MainActivity activity)throws Exception{
        String[] result={null};CountDownLatch done=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->{try{
            java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("activeView");method.setAccessible(true);WebView web=(WebView)method.invoke(activity);
            web.evaluateJavascript("JSON.stringify({ready:document.readyState,title:document.title,config:!!(window.playerConfig&&window.playerConfig.video),videoState:Array.from(document.querySelectorAll('video')).slice(0,2).map(function(v){var r=v.getBoundingClientRect(),s=getComputedStyle(v),hit=document.elementFromPoint(Math.max(0,r.right-26),Math.max(0,r.top+26));return {opacity:s.opacity,display:s.display,visibility:s.visibility,rect:{left:r.left,top:r.top,width:r.width,height:r.height},owner:!!document.getElementById('player'),hit:hit?{tag:hit.tagName,id:hit.id,classes:String(hit.className).slice(0,200)}:null}}),visibleVideos:Array.from(document.querySelectorAll('video')).filter(function(v){var r=v.getBoundingClientRect(),s=getComputedStyle(v);return r.width>70&&r.height>70&&r.top<innerHeight&&r.bottom>0&&s.display!=='none'&&s.visibility!=='hidden'}).length,visibleFrames:Array.from(document.querySelectorAll('iframe')).filter(function(f){var r=f.getBoundingClientRect();return /^https:\\/\\/player\\.vimeo\\.com\\/video\\/[0-9]+/.test(f.src)&&r.width>70&&r.height>70&&r.top<innerHeight&&r.bottom>0}).length})",value->{result[0]=value;done.countDown();});
        }catch(Exception e){throw new RuntimeException(e);}});
        assertTrue(done.await(8,TimeUnit.SECONDS));return new JSONObject(String.valueOf(new JSONTokener(result[0]).nextValue()));
    }
    @Test public void pointsAppearOnPublicVimeoPlayerAndOfficialEmbedDemo()throws Exception{
        Assume.assumeTrue("Live probe enabled only on the modern Android job",Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("vimeoLive","false")));
        int verified=0;
        for(String url:new String[]{"https://player.vimeo.com/video/76979871?h=8272103f6e","https://player.vimeo.com/api/demo"}){
            MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            JSONObject report=new JSONObject().put("page",url.contains("/api/")?"official embed demo":"public player");
            try{
                JSONObject state=new JSONObject();long end=SystemClock.uptimeMillis()+20000;
                do{Thread.sleep(500);state=snapshot(activity);}while(state.optInt("visibleVideos")+state.optInt("visibleFrames")==0&&SystemClock.uptimeMillis()<end);
                report.put("dom",state);
                if(state.optInt("visibleVideos")+state.optInt("visibleFrames")==0){report.put("outcome","inconclusive: no visible player delivered by Vimeo");continue;}
                boolean point=device.wait(Until.hasObject(By.descStartsWith("Video herunterladen")),6000);
                report.put("pointVisible",point).put("outcome",point?"verified: native point visible on live Vimeo":"failed: visible live player has no point");
                assertTrue("A visible Vimeo video or frame must have a native green point",point);verified++;
            }finally{Log.i("MenuPreview","vimeo-live "+report);instrumentation.runOnMainSync(activity::finish);}
        }
        Assume.assumeTrue("No visible Vimeo player was delivered from the CI network; see vimeo-live report",verified>0);
    }
}
