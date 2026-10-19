package de.steffen.musikmathe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private void preview(){
        Bitmap full=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(full==null)return;
        Bitmap scaled=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,85,bytes);
        String encoded=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int chunks=(encoded.length()+1799)/1800;
        for(int i=0;i<chunks;i++)Log.i("MenuPreview","queue "+i+"/"+chunks+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(scaled!=full)scaled.recycle();full.recycle();
    }
    @Test public void settingsShowLiveKnownAndUnknownSizeAndKeepCancelResultsVisible()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        // A progress screen deliberately never becomes idle. Observe its live tree directly.
        long idleTimeout=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);
        CountDownLatch knownRelease=new CountDownLatch(1),unknownRelease=new CountDownLatch(1);
        List<String> requests=Collections.synchronizedList(new ArrayList<>());
        try(ServerSocket server=new ServerSocket(0)){
            Thread responses=new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String request=in.readLine(),line;while((line=in.readLine())!=null&&!line.isEmpty()){}
                requests.add(request.split(" ")[1]);boolean known=request.contains("/known");String headers="HTTP/1.1 200 OK\r\nConnection: close\r\n"+(known?"Content-Length: 262144\r\n":"")+"\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(new byte[65536]);socket.getOutputStream().flush();
                if(known){knownRelease.await(45,TimeUnit.SECONDS);socket.getOutputStream().write(new byte[196608]);}else unknownRelease.await(45,TimeUnit.SECONDS);
            }catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            try(java.io.OutputStream reserved=app.getContentResolver().openOutputStream(uri("removed"))){assertNotNull(reserved);}
            enqueue(server.getLocalPort(),"known");enqueue(server.getLocalPort(),"removed");enqueue(server.getLocalPort(),"unknown");
            await(()->snapshot().progress!=null&&snapshot().progress.bytes==65536);
            assertEquals(25,snapshot().progress.percent);assertEquals(262144,snapshot().progress.total);
            UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),6000);assertNotNull(menu);menu.click();click(device,"Einstellungen");click(device,"Video-Downloads · Fortschritt und Warteschlange");
            assertTrue("Active filename missing",device.wait(Until.hasObject(By.text("known.mp4")),6000));
            assertTrue("Live percentage missing",device.wait(Until.hasObject(By.textContains("25 %")),6000));
            assertTrue("Per-step ETA missing: "+snapshot().progress.detail(),device.wait(Until.hasObject(By.textContains("Restzeit für diesen Schritt: ca.")),6000));
            assertTrue(device.hasObject(By.textContains("In Warteschlange: 2")));
            assertTrue("Close button must remain visible",device.hasObject(By.text(java.util.regex.Pattern.compile("Schließen",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE))));
            assertTrue("Cancel-all button must remain visible",device.hasObject(By.text(java.util.regex.Pattern.compile("Alle abbrechen",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE))));
            UiObject2 remove=device.wait(Until.findObject(By.desc("Video aus Warteschlange entfernen: removed.mp4")),6000);assertNotNull("Individual queued cancel missing",remove);
            preview();Thread.sleep(1100);remove.click();
            await(()->snapshot().waiting.size()==1);assertEquals("unknown.mp4",snapshot().waiting.get(0).title);assertEquals("known.mp4",snapshot().title);assertEquals(65536,snapshot().progress.bytes);
            assertTrue(device.wait(Until.hasObject(By.textContains("In Warteschlange: 1")),6000));
            assertFalse(device.hasObject(By.desc("Video aus Warteschlange entfernen: removed.mp4")));
            try(InputStream removed=app.getContentResolver().openInputStream(uri("removed"))){fail("Cancelled queued destination must be deleted");}catch(FileNotFoundException expected){}
            click(device,"Schließen");app.startActivity(new Intent(app,MainActivity.class).setAction(VideoDownloadService.SHOW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            assertTrue(device.wait(Until.hasObject(By.text("Video-Downloads")),6000));
            knownRelease.countDown();await(()->snapshot().title.equals("unknown.mp4")&&snapshot().progress!=null&&snapshot().progress.bytes==65536);
            assertEquals(-1,snapshot().progress.percent);assertEquals(-1,snapshot().progress.eta);
            assertEquals(java.util.Arrays.asList("/known","/unknown"),requests);
            assertTrue(device.wait(Until.hasObject(By.text("unknown.mp4")),6000));assertTrue(device.wait(Until.hasObject(By.textContains("Gesamtgröße unbekannt")),6000));
            try(InputStream saved=app.getContentResolver().openInputStream(uri("known"))){byte[] buffer=new byte[8192];int n;long total=0;while((n=saved.read(buffer))!=-1)total+=n;assertEquals(262144,total);}
            enqueue(server.getLocalPort(),"waiting");await(()->snapshot().waiting.size()==1);
            // The service updates before the dialog grows; wait for its new layout
            // before locating the footer button's tap coordinates.
            assertTrue(device.wait(Until.hasObject(By.textContains("In Warteschlange: 1")),6000));click(device,"Alle abbrechen");
            await(()->!snapshot().running);assertTrue(snapshot().waiting.isEmpty());assertNull(snapshot().progress);
            assertTrue(device.wait(Until.hasObject(By.text("Keine laufenden Video-Downloads")),6000));assertTrue(device.hasObject(By.text("Videospeicherung abgebrochen.")));
        }finally{
            Configurator.getInstance().setWaitForIdleTimeout(idleTimeout);knownRelease.countDown();unknownRelease.countDown();main(VideoDownloadService::cancelAll);for(String name:new String[]{"known","removed","unknown","waiting"})app.getContentResolver().delete(uri(name),null,null);main(activity::finish);
        }
    }
}
