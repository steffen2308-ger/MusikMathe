package de.steffen.musikmathe;

import android.app.AlertDialog;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class CastTest {
    private static VideoCatalog.Item item(String url,String type){return new VideoCatalog.Item(url,type,Map.of());}
    private static CastVideo source(String suffix){return CastVideo.from(item("https://cdn.example/"+suffix+".mp4","MP4"),"Film "+suffix);}
    @Test public void acceptsSupportedPublicAndLanMediaAndMapsActualReceiverPayload(){
        String[] types={"MP4","WebM","HLS","DASH"},mimes={"video/mp4","video/webm","application/x-mpegURL","application/dash+xml"};
        for(int i=0;i<types.length;i++){
            VideoCatalog.Item item=item("https://cdn.example/media?token=media-token",types[i]);assertEquals("",CastVideo.problem(item,"https://page.example/",false,false));
            MediaInfo payload=GoogleCastTransport.mediaInfo(CastVideo.from(item,"Mein Film"));assertEquals(item.url,payload.getContentId());assertEquals(mimes[i],payload.getContentType());assertEquals("Mein Film",payload.getMetadata().getString(MediaMetadata.KEY_TITLE));assertNull(payload.getCustomData());
        }
        assertEquals("",CastVideo.problem(item("http://192.168.1.50/movie.mp4","MP4"),"https://page.example/",false,false));
    }
    @Test public void excludesPrivateProtectedAuthenticatedAndUnsupportedSources(){
        VideoCatalog.Item video=item("https://cdn.example/movie.mp4","MP4");
        assertFalse(CastVideo.problem(video,"https://page.example",false,true).isEmpty());
        assertFalse(CastVideo.problem(video,"https://page.example",true,false).isEmpty());
        assertFalse(CastVideo.problem(video,"https://www.netflix.com/watch/1",false,false).isEmpty());
        assertFalse(CastVideo.problem(video,"https://www.youtube.com/watch?v=123",false,false).isEmpty());
        assertFalse(CastVideo.problem(item("https://cdn.example/movie.ogv","Video"),"https://page.example",false,false).isEmpty());
        assertFalse(CastVideo.problem(new VideoCatalog.Item(video.url,"MP4",Map.of("authorization","secret")),"https://page.example",false,false).isEmpty());
        assertFalse(CastVideo.problem(new VideoCatalog.Item(video.url,"MP4",Map.of(),"https://cdn.example/audio.mp4","",""),"https://page.example",false,false).isEmpty());
        assertFalse(CastVideo.problem(new VideoCatalog.Item(video.url,"MP4",Map.of(),"","youtube-id",""),"https://page.example",false,false).isEmpty());
        assertEquals("",CastVideo.problem(new VideoCatalog.Item(video.url,"MP4",Map.of("Referer","https://site.example/private","User-Agent","browser")),"https://page.example",false,false));
    }
    @Test public void excludesLocalAndNonNetworkUrlsAndDoesNotConfuseSimilarDomains(){
        for(String url:new String[]{"blob:https://page.example/id","file:///movie.mp4","data:video/mp4,abc","http://localhost/x.mp4","http://127.0.0.5/x.mp4","http://[::1]/x.mp4","http://user:pass@cdn.example/movie.mp4"})
            assertFalse(url,CastVideo.problem(item(url,"MP4"),"https://page.example/",false,false).isEmpty());
        assertEquals("",CastVideo.problem(item("https://cdn.example/movie.mp4","MP4"),"https://netflix.com.example/",false,false));
    }
    static final class Fake implements BrowserCast.Transport {
        Runnable changed;Consumer<String> failed;boolean connected,ready,media,playing;int chosen,paused,played,stopped,disconnected,destroyed;
        final List<CastVideo> loads=new ArrayList<>();final List<Consumer<Boolean>> results=new ArrayList<>();
        @Override public void start(Runnable changed,Consumer<String> failed){this.changed=changed;this.failed=failed;}
        @Override public boolean ready(){return ready;}
        @Override public boolean connected(){return connected;}
        @Override public boolean hasMedia(){return media;}
        @Override public boolean playing(){return playing;}
        @Override public String device(){return "Wohnzimmer";}
        @Override public String title(){return "Testfilm";}
        @Override public String unavailable(){return "Google Play-Dienste fehlen";}
        @Override public void chooseDevice(){chosen++;}
        @Override public void load(CastVideo video,Consumer<Boolean> completed){loads.add(video);results.add(completed);}
        @Override public void play(){played++;}
        @Override public void pause(){paused++;}
        @Override public void stop(){stopped++;}
        @Override public void disconnect(){disconnected++;connected=false;changed.run();}
        @Override public void destroy(){destroyed++;}
        void connect(){ready=true;connected=true;changed.run();}
    }
    private Button find(View view,String label){
        if(view instanceof Button&&label.contentEquals(((Button)view).getText()))return (Button)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button b=find(group.getChildAt(i),label);if(b!=null)return b;}}
        return null;
    }
    private Button button(String label){Button button=find(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),label);assertNotNull(label,button);return button;}
    @Test public void selectionWaitsForDeviceAndRejectsAChangedPage(){
        ActivityController<MainActivity> a=Robolectric.buildActivity(MainActivity.class).setup();Fake fake=new Fake();BrowserCast cast=new BrowserCast(a.get(),()->{},fake);AtomicBoolean valid=new AtomicBoolean(true);
        try{
            cast.select(source("one"),valid::get);assertTrue(fake.loads.isEmpty());fake.ready=true;fake.changed.run();assertEquals(1,fake.chosen);
            valid.set(false);fake.connect();assertTrue("No stale-page playback",fake.loads.isEmpty());
            cast.select(source("two"),()->true);assertEquals(1,fake.loads.size());assertTrue(fake.loads.get(0).url.endsWith("two.mp4"));
        }finally{cast.destroy();a.pause().stop().destroy();}
    }
    @Test public void controlsWorkAndLateLoadsCannotChangeNewPlaybackOrStoppedState(){
        ActivityController<MainActivity> a=Robolectric.buildActivity(MainActivity.class).setup();Fake fake=new Fake();fake.ready=true;fake.connected=true;BrowserCast cast=new BrowserCast(a.get(),()->{},fake);
        try{
            cast.select(source("one"),()->true);cast.select(source("two"),()->true);fake.media=true;fake.playing=true;
            fake.changed.run();fake.results.get(0).accept(false);assertFalse("New load is still in progress",button("Pause").isEnabled());fake.results.get(1).accept(true);
            button("Pause").performClick();assertEquals(1,fake.paused);fake.playing=false;fake.changed.run();button("Wiedergabe fortsetzen").performClick();assertEquals(1,fake.played);
            cast.select(source("three"),()->true);button("Wiedergabe stoppen").performClick();assertEquals(1,fake.stopped);fake.results.get(2).accept(false);assertTrue("Stale load failure cannot disable controls",button("Wiedergabe fortsetzen").isEnabled());
            button("Verbindung trennen").performClick();assertEquals(1,fake.disconnected);assertFalse(button("Wiedergabe fortsetzen").isEnabled());
        }finally{cast.destroy();assertEquals(1,fake.destroyed);a.pause().stop().destroy();}
    }
    @Test public void unavailableTransportKeepsControlsDisabledAndDestroyDropsPendingLoad(){
        ActivityController<MainActivity> a=Robolectric.buildActivity(MainActivity.class).setup();Fake fake=new Fake();BrowserCast cast=new BrowserCast(a.get(),()->{},fake);
        try{
            cast.select(source("one"),()->true);assertFalse(button("Gerät auswählen").isEnabled());assertFalse(button("Video auswählen").isEnabled());assertFalse(button("Wiedergabe fortsetzen").isEnabled());
            cast.destroy();fake.connect();assertTrue(fake.loads.isEmpty());
        }finally{cast.destroy();a.pause().stop().destroy();}
    }
    @Test public void actualBrowserBindingRejectsNavigationBeforeTheDeviceConnects()throws Exception{
        ActivityController<MainActivity> a=Robolectric.buildActivity(MainActivity.class).setup();Fake fake=new Fake();fake.ready=true;BrowserCast cast=new BrowserCast(a.get(),()->{},fake);
        try{
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("browserCast");field.setAccessible(true);field.set(a.get(),cast);
            java.lang.reflect.Method active=MainActivity.class.getDeclaredMethod("active");active.setAccessible(true);MainActivity.Tab tab=(MainActivity.Tab)active.invoke(a.get());tab.url="https://page.example/watch";
            java.lang.reflect.Method select=MainActivity.class.getDeclaredMethod("castVideo",MainActivity.Tab.class,int.class,String.class,VideoCatalog.Item.class);select.setAccessible(true);select.invoke(a.get(),tab,tab.videoPage,tab.url,item("https://cdn.example/movie.mp4","MP4"));
            tab.view.getWebViewClient().onPageStarted(tab.view,"https://page.example/next",null);fake.connect();assertTrue(fake.loads.isEmpty());
        }finally{cast.destroy();a.pause().stop().destroy();}
    }
    @Test public void privateBrowserNeverCreatesTheCastService()throws Exception{
        BrowserApplication.privateReady=true;ActivityController<PrivateActivity> a=Robolectric.buildActivity(PrivateActivity.class).setup();
        try{
            java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showCast");show.setAccessible(true);show.invoke(a.get());
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("browserCast");field.setAccessible(true);assertNull(field.get(a.get()));
        }finally{a.pause().stop().destroy();BrowserApplication.privateReady=false;}
    }
}
