package de.steffen.musikmathe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VideoDownloadProgressTest {
    private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private VideoDownloadService.Snapshot snapshot(){AtomicReference<VideoDownloadService.Snapshot> ref=new AtomicReference<>();main(()->ref.set(VideoDownloadService.snapshot()));return ref.get();}
    private void await(BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}fail("Download did not reach expected state: "+snapshot().message);}
    private Uri uri(String name){return Uri.parse("content://de.steffen.musikmathe.test.progress/"+name+".mp4");}
    private void enqueue(int port,String name){
        Intent intent=new Intent(app,VideoDownloadService.class).putExtra("url","http://127.0.0.1:"+port+"/"+name).putExtra("type","MP4").putExtra("headers","{}").putExtra("title",name+".mp4").putExtra("original",true).putExtra("destination",uri(name).toString());
        if(Build.VERSION.SDK_INT>=26)app.startForegroundService(intent);else app.startService(intent);
    }
    private void click(UiDevice device,String label){UiObject2 item=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(label),java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE))),6000);assertNotNull(label,item);item.click();}
    @Test public void settingsShowLiveKnownAndUnknownSizeAndKeepCancelResultsVisible()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        CountDownLatch knownRelease=new CountDownLatch(1),unknownRelease=new CountDownLatch(1);
        try(ServerSocket server=new ServerSocket(0)){
            Thread responses=new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String request=in.readLine(),line;while((line=in.readLine())!=null&&!line.isEmpty()){}
                boolean known=request.contains("/known");String headers="HTTP/1.1 200 OK\r\nConnection: close\r\n"+(known?"Content-Length: 262144\r\n":"")+"\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(new byte[65536]);socket.getOutputStream().flush();
                if(known){knownRelease.await(45,TimeUnit.SECONDS);socket.getOutputStream().write(new byte[196608]);}else unknownRelease.await(45,TimeUnit.SECONDS);
            }catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            enqueue(server.getLocalPort(),"known");enqueue(server.getLocalPort(),"unknown");
            await(()->snapshot().progress!=null&&snapshot().progress.bytes==65536);
            assertEquals(25,snapshot().progress.percent);assertEquals(262144,snapshot().progress.total);
            UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),6000);assertNotNull(menu);menu.click();click(device,"Einstellungen");click(device,"Video-Downloads · Fortschritt und Warteschlange");
            assertTrue(device.wait(Until.hasObject(By.text("known.mp4")),6000));
            assertTrue(device.wait(Until.hasObject(By.textContains("25 %")),6000));
            assertTrue(device.wait(Until.hasObject(By.textContains("Restzeit für diesen Schritt: ca.")),6000));
            assertTrue(device.hasObject(By.textContains("In Warteschlange: 1")));
            click(device,"Schließen");app.startActivity(new Intent(app,MainActivity.class).setAction(VideoDownloadService.SHOW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            assertTrue(device.wait(Until.hasObject(By.text("Video-Downloads")),6000));
            knownRelease.countDown();await(()->snapshot().title.equals("unknown.mp4")&&snapshot().progress!=null&&snapshot().progress.bytes==65536);
            assertEquals(-1,snapshot().progress.percent);assertEquals(-1,snapshot().progress.eta);
            assertTrue(device.wait(Until.hasObject(By.text("unknown.mp4")),6000));assertTrue(device.wait(Until.hasObject(By.textContains("Gesamtgröße unbekannt")),6000));
            try(InputStream saved=app.getContentResolver().openInputStream(uri("known"))){byte[] buffer=new byte[8192];int n;long total=0;while((n=saved.read(buffer))!=-1)total+=n;assertEquals(262144,total);}
            enqueue(server.getLocalPort(),"waiting");await(()->snapshot().waiting.size()==1);click(device,"Alle abbrechen");
            await(()->!snapshot().running);assertTrue(snapshot().waiting.isEmpty());assertNull(snapshot().progress);
            assertTrue(device.wait(Until.hasObject(By.text("Keine laufenden Video-Downloads")),6000));assertTrue(device.hasObject(By.text("Videospeicherung abgebrochen.")));
        }finally{
            knownRelease.countDown();unknownRelease.countDown();main(VideoDownloadService::cancelAll);for(String name:new String[]{"known","unknown","waiting"})app.getContentResolver().delete(uri(name),null,null);main(activity::finish);
        }
    }
}
