package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real DOM -> settings selection -> three workers -> SAF, with authenticated HTTP responses. */
@RunWith(AndroidJUnit4.class)
public class MediaBatchDeviceTest {
    private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void main(Runnable task){InstrumentationRegistry.getInstrumentation().runOnMainSync(task);}
    private VideoDownloadService.Snapshot snapshot(){AtomicReference<VideoDownloadService.Snapshot> ref=new AtomicReference<>();main(()->ref.set(VideoDownloadService.snapshot()));return ref.get();}
    private void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}fail("Media batch timed out: "+snapshot().message);}
    private void click(UiDevice device,String text){UiObject2 item=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text),java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE))),8000);assertNotNull(text,item);item.click();}
    private void open(UiDevice device)throws Exception{UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),8000);assertNotNull(menu);menu.click();click(device,"Einstellungen");if(!device.hasObject(By.text("Alle Medien dieser Seite herunterladen")))new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector().className("android.widget.ListView")).scrollIntoView(new androidx.test.uiautomator.UiSelector().text("Alle Medien dieser Seite herunterladen"));click(device,"Alle Medien dieser Seite herunterladen");assertTrue(device.wait(Until.hasObject(By.text("Medien dieser Seite · 4")),10000));}
    @Test public void linkedMediaRequireConfirmationKeepCookiesAndUseCancellableParallelQueue()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");if(android.os.Build.VERSION.SDK_INT>=33)device.executeShellCommand("pm grant "+app.getPackageName()+" android.permission.POST_NOTIFICATIONS");
        long idle=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);
        File metadata=new File(app.getFilesDir(),"video-download-folder.json");byte[] previous=metadata.isFile()?Files.readAllBytes(metadata.toPath()):null;
        BrowserStore store=new BrowserStore(app);boolean preloading=store.linkPreloadingEnabled();store.linkPreloadingEnabled(false);
        String authority="de.steffen.musikmathe.test.media";Uri tree=DocumentsContract.buildTreeDocumentUri(authority,"root");
        Files.write(metadata.toPath(),("{\"uri\":\""+tree+"\",\"label\":\"Medientest\"}").getBytes(StandardCharsets.UTF_8));
        Map<String,byte[]> payloads=new HashMap<>();payloads.put("photo.png",android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nS8AAAAASUVORK5CYII=",0));
        payloads.put("sound.wav",new byte[]{'R','I','F','F',36,0,0,0,'W','A','V','E','f','m','t',' ',16,0,0,0,1,0,1,0,64,31,0,0,(byte)128,62,0,0,2,0,16,0,'d','a','t','a',0,0,0,0});
        try(InputStream video=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("video/source.mp4")){java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=video.read(buffer))!=-1)bytes.write(buffer,0,n);payloads.put("movie.mp4",bytes.toByteArray());}
        payloads.put("fourth.mp4",payloads.get("movie.mp4"));
        Map<String,String> requests=Collections.synchronizedMap(new HashMap<>());CountDownLatch release=new CountDownLatch(1);MainActivity activity=null;
        try(ServerSocket server=new ServerSocket(0)){
            Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->{try(Socket close=socket){
                socket.setSoTimeout(10000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine();if(first==null)return;String path=first.split(" ")[1].substring(1),cookie="",line;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.regionMatches(true,0,"Cookie:",0,7))cookie=line.substring(7).trim();
                byte[] body;String mime,extra="";int status=200;
                if(path.equals("page")){mime="text/html; charset=utf-8";extra="Set-Cookie: batch=normal; Path=/\r\n";body=("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Medien-Sammeltest</title><h1>Medien-Sammeltest</h1><a href='photo.png'>Bild</a><a href='sound.wav'>Ton</a><a href='movie.mp4'>Film</a><a href='fourth.mp4'>Später</a><a href='photo.png#duplicate'>Doppelt</a><a href='article'>Artikel</a><a href='javascript:void(0)'>Aktion</a>").getBytes(StandardCharsets.UTF_8);}
                else if(payloads.containsKey(path)){requests.put(path,cookie);mime=path.endsWith("png")?"image/png":path.endsWith("wav")?"audio/wav":"video/mp4";body=payloads.get(path);if(!cookie.contains("batch=normal")){status=403;body=new byte[0];}}
                else{mime="text/html";body=new byte[0];}
                socket.getOutputStream().write(("HTTP/1.1 "+status+" OK\r\nConnection: close\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\n"+extra+"\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
                if(payloads.containsKey(path))release.await(90,TimeUnit.SECONDS);socket.getOutputStream().write(body);
            }catch(Exception ignored){}});response.setDaemon(true);response.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();
            activity=(MainActivity)InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/page")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity browser=activity;CountDownLatch loaded=new CountDownLatch(1);
            for(int attempt=0;attempt<100&&loaded.getCount()!=0;attempt++){main(()->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("activeView");method.setAccessible(true);WebView web=(WebView)method.invoke(browser);web.evaluateJavascript("document.title",value->{if("\"Medien-Sammeltest\"".equals(value))loaded.countDown();});}catch(Exception error){throw new RuntimeException(error);}});loaded.await(100,TimeUnit.MILLISECONDS);}assertEquals("Fixture loaded",0,loaded.getCount());
            open(device);assertTrue(requests.isEmpty());click(device,"Schließen");assertTrue(requests.isEmpty());open(device);click(device,"Auswahl herunterladen");
            await(()->snapshot().active.size()==3&&snapshot().waiting.size()==1&&requests.size()==3);assertEquals("fourth.mp4",snapshot().waiting.get(0).title);assertFalse(requests.containsKey("fourth.mp4"));
            assertTrue(device.wait(Until.hasObject(By.text("Medien-Downloads")),8000));String cancel="Download aus Warteschlange entfernen: fourth.mp4";
            UiObject2 button=device.findObject(By.desc(cancel));if(button==null)new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector().className("android.widget.ScrollView")).scrollIntoView(new androidx.test.uiautomator.UiSelector().description(cancel));button=device.wait(Until.findObject(By.desc(cancel)),6000);assertNotNull(button);button.click();await(()->snapshot().waiting.isEmpty());
            release.countDown();await(()->!snapshot().running);assertFalse(requests.containsKey("fourth.mp4"));
            for(String name:new String[]{"photo.png","sound.wav","movie.mp4"}){assertTrue("Cookies for "+name,requests.get(name).contains("batch=normal"));try(InputStream input=app.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,name))){java.io.ByteArrayOutputStream saved=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[8192];int n;while((n=input.read(bytes))!=-1)saved.write(bytes,0,n);assertArrayEquals(name,payloads.get(name),saved.toByteArray());}}
            try(InputStream removed=app.getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,"fourth.mp4"))){fail("Queued cancellation must not create a file");}catch(java.io.FileNotFoundException expected){}
        }finally{
            release.countDown();main(VideoDownloadService::cancelAll);await(()->!snapshot().running);for(String name:payloads.keySet())try{DocumentsContract.deleteDocument(app.getContentResolver(),DocumentsContract.buildDocumentUriUsingTree(tree,name));}catch(Exception ignored){}
            if(previous==null)metadata.delete();else Files.write(metadata.toPath(),previous);store.linkPreloadingEnabled(preloading);Configurator.getInstance().setWaitForIdleTimeout(idle);if(activity!=null){MainActivity end=activity;main(end::finish);}
        }
    }
}
