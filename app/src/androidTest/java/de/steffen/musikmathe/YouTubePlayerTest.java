package de.steffen.musikmathe;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Context;
import org.json.JSONObject;
import org.json.JSONTokener;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class YouTubePlayerTest {
    @Test public void extractsSeparateTracksFromYouTubePlayerAndHandlesShortsNavigation()throws Exception{
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();CountDownLatch done=new CountDownLatch(1);AtomicReference<JSONObject> result=new AtomicReference<>();AtomicReference<WebView> view=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            WebView web=new WebView(app);view.set(web);web.getSettings().setJavaScriptEnabled(true);
            web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){v.evaluateJavascript(YouTubeSources.script(app),value->{try{result.set(new JSONObject(String.valueOf(new JSONTokener(value).nextValue())));}catch(Exception ignored){}done.countDown();});}});
            String html="<html><title>YouTube Test</title><script>window.ytInitialPlayerResponse={videoDetails:{videoId:'aqz-KE-bpKQ',title:'Testfilm'},streamingData:{adaptiveFormats:[{itag:137,mimeType:'video/mp4',height:1080,url:'https://r1.googlevideo.com/videoplayback?id=A&itag=137'},{itag:140,mimeType:'audio/mp4',audioQuality:'AUDIO_QUALITY_MEDIUM',url:'https://r1.googlevideo.com/videoplayback?id=A&itag=140'}]}};</script></html>";
            web.loadDataWithBaseURL("https://www.youtube.com/shorts/aqz-KE-bpKQ",html,"text/html","UTF-8",null);
        });
        try{assertTrue(done.await(15,TimeUnit.SECONDS));assertNotNull(result.get());assertEquals("aqz-KE-bpKQ",result.get().getString("videoId"));assertTrue(result.get().getBoolean("playerData"));assertEquals(2,result.get().getJSONArray("formats").length);assertEquals(1,YouTubeSources.parse(result.get(),java.util.Collections.emptyMap(),java.util.Collections.emptyList()).items.size());}
        finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->view.get().destroy());}
    }
}
