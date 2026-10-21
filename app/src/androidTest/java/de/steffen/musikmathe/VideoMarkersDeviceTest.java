package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real native overlays, two distinct sources, DOM changes and a completed file download. */
@RunWith(AndroidJUnit4.class)
public class VideoMarkersDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context app=instrumentation.getTargetContext();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private MainActivity activity;
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(String name,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private String js(String script)throws Exception{String[] result={null};CountDownLatch done=new CountDownLatch(1);main(()->((WebView)call("activeView",new Class<?>[0])).evaluateJavascript(script,value->{result[0]=value;done.countDown();}));assertTrue(done.await(8,TimeUnit.SECONDS));return result[0];}
    private void await(java.util.function.BooleanSupplier condition,String message)throws Exception{long end=SystemClock.uptimeMillis()+20000;while(!condition.getAsBoolean()&&SystemClock.uptimeMillis()<end)Thread.sleep(100);assertTrue(message,condition.getAsBoolean());}
    private UiObject2 text(String title){UiObject2 view=device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(title),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))),8000);assertNotNull(title,view);return view;}
    private UiObject2 marker(String title){UiObject2 view=device.wait(Until.findObject(By.desc("Video herunterladen: "+title)),12000);assertNotNull("Green marker for "+title,view);return view;}
    private void launch(String url)throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");app.getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).commit();
        activity=(MainActivity)instrumentation.startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void preview(){Bitmap full=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(full);Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,out);String encoded=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);int count=(encoded.length()+1799)/1800;for(int i=0;i<count;i++)Log.i("MenuPreview","video-markers "+i+"/"+count+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));small.recycle();full.recycle();}
    private byte[] bytes(InputStream in)throws Exception{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=source.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}}
    private boolean saved(Uri uri,byte[] expected){try{return Arrays.equals(expected,bytes(app.getContentResolver().openInputStream(uri)));}catch(Exception ignored){return false;}}
    private void diagnostic(){
        try{Log.i("MenuPreview","marker-dom-state "+js("JSON.stringify({href:location.href,width:innerWidth,height:innerHeight,videos:Array.from(document.querySelectorAll('video')).map(function(v){var r=v.getBoundingClientRect();return {title:v.title,source:v.currentSrc||v.src,rect:{left:r.left,top:r.top,width:r.width,height:r.height},hit:document.elementFromPoint(r.right-26,r.top+26)&&document.elementFromPoint(r.right-26,r.top+26).tagName};})})"));}catch(Exception ignored){}
        try{main(()->{WebView web=(WebView)call("activeView",new Class<?>[0]);Log.i("MenuPreview","marker-native-state resumed="+field("browserResumed")+" shown="+web.isShown()+" size="+web.getWidth()+"x"+web.getHeight());});preview();}catch(Exception ignored){}
    }
    @Test public void greenPointDownloadsTheSelectedVideoRatherThanItsNeighbour()throws Exception{
        Uri tree=Uri.parse("content://de.steffen.musikmathe.test.markers/tree/root"),saved=DocumentsContract.buildDocumentUriUsingTree(tree,"second.mp4");
        Map<File,byte[]> previous=new HashMap<>();for(String suffix:new String[]{"",".bak",".new"}){File file=new File(app.getFilesDir(),"video-download-folder.json"+suffix);previous.put(file,file.exists()?Files.readAllBytes(file.toPath()):null);file.delete();}
        File config=new File(app.getFilesDir(),"video-download-folder.json");Files.write(config.toPath(),new JSONObject().put("uri",tree.toString()).put("label","Marker test downloads").toString().getBytes(StandardCharsets.UTF_8));app.getContentResolver().delete(saved,null,null);app.getContentResolver().delete(DocumentsContract.buildDocumentUriUsingTree(tree,"first.mp4"),null,null);
        try(Fixture fixture=new Fixture()){
            launch(fixture.base()+"/page");marker("Erster Testfilm");marker("Zweiter Testfilm");preview();marker("Zweiter Testfilm").click();
            text("Originaldatei speichern");text("Informationen").click();assertNotNull(device.wait(Until.findObject(By.textContains("Dateiname / Pfadende: second.mp4")),10000));
            text("Video speichern").click();text("Originaldatei speichern").click();
            await(()->saved(saved,fixture.second),"Native marker must save exactly the second video's bytes");
            assertFalse("The neighbouring video must not be downloaded",saved(DocumentsContract.buildDocumentUriUsingTree(tree,"first.mp4"),fixture.first));
            await(()->{boolean[] running={true};main(()->running[0]=VideoDownloadService.snapshot().running);return !running[0];},"Download completed successfully");
        }catch(AssertionError failure){diagnostic();throw failure;}finally{
            main(VideoDownloadService::cancelAll);app.getContentResolver().delete(saved,null,null);if(activity!=null)main(activity::finish);
            for(Map.Entry<File,byte[]> entry:previous.entrySet()){entry.getKey().delete();if(entry.getValue()!=null)Files.write(entry.getKey().toPath(),entry.getValue());}
        }
    }
    @Test public void pointsTrackScrollAndDynamicVideosAndDisappearOnOtherTabsAndNavigation()throws Exception{
        try(Fixture fixture=new Fixture()){
            launch(fixture.base()+"/page");marker("Erster Testfilm");int before=marker("Zweiter Testfilm").getVisibleCenter().y;
            js("window.scrollTo(0,150);true");await(()->{try{UiObject2 dot=device.findObject(By.desc("Video herunterladen: Zweiter Testfilm"));return dot!=null&&dot.getVisibleCenter().y<before-60;}catch(androidx.test.uiautomator.StaleObjectException updating){return false;}},"Marker follows the actual video after scrolling");
            js("document.getElementById('second').remove();var v=document.createElement('video');v.id='dynamic';v.title='Dritter Testfilm';v.src='/second.mp4';v.style='position:fixed;left:8px;top:100px;width:90%;height:120px;background:blue';document.body.appendChild(v);true");
            assertTrue(device.wait(Until.gone(By.desc("Video herunterladen: Zweiter Testfilm")),8000));marker("Dritter Testfilm");
            js("var cover=document.createElement('div');cover.id='cover';cover.style='position:fixed;z-index:99999;top:0;right:0;bottom:0;left:0;background:white';document.body.appendChild(cover);true");assertTrue(device.wait(Until.gone(By.desc("Video herunterladen: Dritter Testfilm")),8000));
            js("document.getElementById('cover').remove();true");marker("Dritter Testfilm");int[] selected={0};main(()->selected[0]=(Integer)field("selected"));
            main(()->call("newTab",new Class<?>[]{String.class,boolean.class},Address.HOME,true));assertTrue(device.wait(Until.gone(By.descStartsWith("Video herunterladen")),8000));
            main(()->call("switchTab",new Class<?>[]{int.class},selected[0]));marker("Dritter Testfilm");
            main(()->call("navigate",new Class<?>[]{String.class},fixture.base()+"/blank"));assertTrue(device.wait(Until.gone(By.descStartsWith("Video herunterladen")),8000));
        }catch(AssertionError failure){diagnostic();throw failure;}finally{if(activity!=null)main(activity::finish);}
    }
    @Test public void homepagePreviewsSelectTheirOwnVideoAndOpenShadowPlayersAreRecognised()throws Exception{
        try(Fixture fixture=new Fixture()){
            launch(fixture.base()+"/cards");marker("YouTube Vorschau");marker("ntv Vorschau");
            // Use an already resolved source, so this tests the real native click
            // and ID filtering without relying on YouTube's availability in CI.
            main(()->{
                Object tab=call("active",new Class<?>[0]);
                try{Field f=tab.getClass().getDeclaredField("videos");f.setAccessible(true);VideoCatalog catalog=(VideoCatalog)f.get(tab);
                    catalog.add(fixture.base()+"/first.mp4","video/mp4",java.util.Collections.emptyMap());
                    VideoCatalog.Item chosen=new VideoCatalog.Item(fixture.base()+"/second.mp4","MP4",java.util.Collections.emptyMap(),"","abcdefghijk","YouTube Vorschau",fixture.base()+"/blue.png");
                    catalog.resolved(new YouTubeDownloadResolver.Result("abcdefghijk","YouTube Vorschau",java.util.Collections.singletonList(chosen)));
                }catch(Exception e){throw new RuntimeException(e);}
            });
            marker("YouTube Vorschau").click();text("Originaldatei speichern");text("Informationen").click();
            assertNotNull(device.wait(Until.findObject(By.textContains("YouTube-ID: abcdefghijk")),8000));
            assertNotNull(device.findObject(By.textContains("Dateiname / Pfadende: second.mp4")));text("Schließen").click();
            marker("ntv Vorschau").click();text("Video öffnen");assertNull("A linked HTML page must not be offered as an MP4",device.findObject(By.text("Originaldatei speichern")));text("Schließen").click();
            main(()->{try{Object tab=call("active",new Class<?>[0]);Field f=tab.getClass().getDeclaredField("videos");f.setAccessible(true);((VideoCatalog)f.get(tab)).clear();}catch(Exception e){throw new RuntimeException(e);}});
            js("document.body.innerHTML='';var host=document.createElement('div');document.body.appendChild(host);var shadow=host.attachShadow({mode:'open'});shadow.innerHTML=\"<style>.wrap{position:relative;width:100%;height:180px}video{width:100%;height:180px}.controls{position:absolute;top:0;right:0;bottom:0;left:0}</style><div class='wrap'><video title='Shadow Testfilm' src='/second.mp4' poster='/blue.png' preload='none'></video><div class='controls'></div></div>\";true");
            marker("Shadow Testfilm").click();text("Originaldatei speichern");text("Abbrechen").click();
        }catch(AssertionError failure){diagnostic();throw failure;}finally{if(activity!=null)main(activity::finish);}
    }
    private final class Fixture implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0);final byte[] first,second,red,blue;
        Fixture()throws Exception{
            first=bytes(instrumentation.getContext().getAssets().open("video/source.mp4"));second=Arrays.copyOf(first,first.length+17);System.arraycopy("selected-video-b!".getBytes(StandardCharsets.US_ASCII),0,second,first.length,17);
            red=poster(Color.RED);blue=poster(Color.BLUE);Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread worker=new Thread(()->serve(socket));worker.setDaemon(true);worker.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();
        }
        String base(){return "http://127.0.0.1:"+server.getLocalPort();}
        byte[] poster(int color){Bitmap bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();return out.toByteArray();}
        void serve(Socket socket){try(Socket close=socket){
            socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String firstLine=reader.readLine(),line,range=null;if(firstLine==null)return;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();
            String path=firstLine.split(" ")[1],type="text/html; charset=utf-8";byte[] body;
            if(path.endsWith(".mp4")){body=path.startsWith("/first")?first:second;type="video/mp4";}
            else if(path.endsWith(".png")){body=path.startsWith("/red")?red:blue;type="image/png";}
            else{String html=path.startsWith("/blank")?"<h1>Keine Videos</h1>":path.startsWith("/cards")?"<h1>Video-Vorschauen</h1><a href='https://m.youtube.com/watch?v=abcdefghijk' title='YouTube Vorschau'><img alt='YouTube Vorschau' src='/red.png'></a><a href='https://www.n-tv.de/mediathek/videos/politik/Testfilm-id123.html' title='ntv Vorschau'><img alt='ntv Vorschau' src='/blue.png'></a>":"<h1>Video-Punkte</h1><div class='touch-player'><video id='first' title='Erster Testfilm' src='/first.mp4' poster='/red.png' preload='none' controls></video><div class='touch-controls'></div></div><div class='touch-player'><video id='second' title='Zweiter Testfilm' src='/second.mp4' poster='/blue.png' preload='none' controls></video><div class='touch-controls'></div></div>";body=("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;min-height:1800px;margin:8px}h1{font-size:20px}video,img{display:block;width:100%;height:130px;margin:8px 0;object-fit:fill}.touch-player{position:relative;height:146px}.touch-controls{position:absolute;top:0;right:0;bottom:0;left:0}</style>"+html).getBytes(StandardCharsets.UTF_8);}
            int start=0,end=body.length-1;boolean partial=range!=null&&type.equals("video/mp4");if(partial){String[] limits=range.replace("bytes=","").split("-",-1);start=Integer.parseInt(limits[0]);if(limits.length>1&&!limits[1].isEmpty())end=Math.min(end,Integer.parseInt(limits[1]));}if(start>end)return;
            String headers="HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+(end-start+1)+"\r\nAccept-Ranges: bytes\r\nConnection: close\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n":"")+"\r\n";socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body,start,end-start+1);socket.getOutputStream().flush();
        }catch(Exception ignored){}}
        @Override public void close()throws Exception{server.close();}
    }
}
