package de.steffen.musikmathe;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import org.json.JSONObject;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.io.File;
import static org.junit.Assert.*;
/** Exercises the visible browser and real resolver. CI network blocks are reported separately. */
@RunWith(AndroidJUnit4.class)
public class YouTubeLiveProbeTest {
    private static WebView findWebView(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){WebView found=findWebView(g.getChildAt(i));if(found!=null)return found;}}return null;}
    @Test public void probeReportedVideoOnLiveYouTube()throws Exception{
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id="qiaOQ0RWmKw",url="https://m.youtube.com/watch?v="+id;
        Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        JSONObject report=new JSONObject().put("requestedVideoId",id);File output=new File(app.getCacheDir(),"live-youtube.mp4");
        AtomicReference<VideoExporter> exporter=new AtomicReference<>();
        try{
            Thread.sleep(7000);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{WebView web=findWebView(activity.getWindow().getDecorView());if(web!=null){web.getSettings().setMediaPlaybackRequiresUserGesture(false);web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.play().catch(function(){});var p=document.getElementById('movie_player');if(p&&p.playVideo)p.playVideo();})()",null);}});
            YouTubeDownloadResolver.Result sources;
            try{sources=new YouTubeDownloadResolver().resolve(url);report.put("title",sources.title).put("downloadableSources",sources.items.size());}
            catch(Exception error){report.put("outcome","inconclusive: live extraction unavailable").put("errorType",error.getClass().getSimpleName()).put("message",YouTubeDownloadResolver.errorMessage(error));if((error instanceof org.schabi.newpipe.extractor.exceptions.ParsingException&&String.valueOf(error.getMessage()).startsWith("Got HTML document, expected JSON response"))||error instanceof org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException||error instanceof java.io.IOException||error instanceof org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException||error instanceof org.schabi.newpipe.extractor.exceptions.ReCaptchaException){Assume.assumeNoException("Live YouTube request not available from CI",error);return;}throw error;}
            assertFalse(sources.items.isEmpty());VideoCatalog.Item source=sources.items.get(0);report.put("sourceType",source.type);
            CountDownLatch done=new CountDownLatch(1);AtomicReference<String> failure=new AtomicReference<>();output.delete();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{VideoExporter job=new VideoExporter();exporter.set(job);job.start(app,source.url,source.type,source.headers,output,2500,source.audioUrl,new VideoExporter.Listener(){public void completed(){done.countDown();}public void failed(String text){failure.set(text);done.countDown();}public void progress(int n){}});});
            assertTrue("Live export timed out",done.await(90,TimeUnit.SECONDS));assertNull(failure.get(),failure.get());
            MediaExtractor media=new MediaExtractor();boolean video=false,audio=false;
            try{media.setDataSource(output.getAbsolutePath());for(int i=0;i<media.getTrackCount();i++){String mime=media.getTrackFormat(i).getString(MediaFormat.KEY_MIME);video|=mime.startsWith("video/");audio|=mime.startsWith("audio/");}}finally{media.release();}
            assertTrue("Export must contain video",video);assertTrue("Export must contain audio",audio);assertTrue(output.length()>1000);
            report.put("outcome","verified: YouTube clip exported with audio and video").put("mp4Bytes",output.length());
        }finally{
            if(!report.has("outcome"))report.put("outcome","failed: see Android test results");
            try(java.io.FileOutputStream out=app.openFileOutput("youtube-probe.json",Context.MODE_PRIVATE)){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(exporter.get()!=null)exporter.get().cancel();activity.finish();});output.delete();
        }
    }
}
