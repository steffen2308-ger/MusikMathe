package de.steffen.musikmathe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import org.json.JSONObject;
import org.json.JSONTokener;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.Assert.*;

/** Live-site diagnostic: network/consent/bot blocks are recorded as inconclusive, not parser success. */
@RunWith(AndroidJUnit4.class)
public class YouTubeLiveProbeTest {
    @Test public void probeReportedVideoOnLiveYouTube()throws Exception{
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch done=new CountDownLatch(1);AtomicReference<WebView> view=new AtomicReference<>();
        AtomicReference<JSONObject> snapshot=new AtomicReference<>();AtomicReference<Throwable> failure=new AtomicReference<>();
        VideoCatalog network=new VideoCatalog();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            WebView web=new WebView(app);view.set(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setMediaPlaybackRequiresUserGesture(false);
            YouTubeBridge.attach(app,web,snapshot::set);
            web.setWebViewClient(new WebViewClient(){
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){if("GET".equals(r.getMethod()))network.observe(r.getUrl().toString(),Collections.emptyMap());return null;}
                @Override public void onPageFinished(WebView v,String url){if(YouTubeSources.originAllowed(url))v.evaluateJavascript(YouTubeSources.script(app),null);}
            });
            web.loadUrl("https://m.youtube.com/watch?v=qiaOQ0RWmKw");
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                web.evaluateJavascript(YouTubeSources.script(app),value->{
                    try{
                        JSONObject report=new JSONObject().put("requestedVideoId","qiaOQ0RWmKw").put("pageOrigin",VideoCatalog.origin(web.getUrl()==null?"about:blank":web.getUrl()));
                        JSONObject data=snapshot.get();try{data=new JSONObject(String.valueOf(new JSONTokener(value).nextValue()));}catch(Exception ignored){}
                        if(data!=null){
                            report.put("detectedVideoId",data.optString("videoId")).put("title",data.optString("title")).put("playerData",data.optBoolean("playerData")).put("status",data.optString("status")).put("reason",data.optString("reason"));
                            report.put("formatCount",data.optJSONArray("formats")==null?0:data.optJSONArray("formats").length());
                            int count=0;try{count=YouTubeSources.parse(data,Collections.emptyMap(),network.observed()).items.size();}catch(Exception ignored){}
                            report.put("downloadableSources",count).put("outcome",data.optBoolean("playerData")?"player response received":"inconclusive: no player response");snapshot.set(data);
                        }else report.put("outcome","inconclusive: no page data");
                        try(java.io.FileOutputStream out=app.openFileOutput("youtube-probe.json",Context.MODE_PRIVATE)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
                    }catch(Throwable e){failure.set(e);}finally{done.countDown();}
                });
            },25000);
        });
        try{assertTrue("Probe timed out",done.await(40,TimeUnit.SECONDS));if(failure.get()!=null)throw new AssertionError(failure.get());
            JSONObject data=snapshot.get();Assume.assumeTrue("Live YouTube unavailable from CI; see youtube-probe.json",data!=null&&data.optBoolean("playerData"));assertEquals("qiaOQ0RWmKw",data.optString("videoId"));
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(view.get()!=null){view.get().stopLoading();view.get().destroy();}});}
    }
}
