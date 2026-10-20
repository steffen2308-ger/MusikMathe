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
        for(int i=0;i<chunks;i++)Log.i("MenuPreview","parallel-downloads "+i+"/"+chunks+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(scaled!=full)scaled.recycle();full.recycle();
    }
    private VideoDownloadService.ActiveVideo active(String title){return snapshot().active.stream().filter(row->row.title.equals(title+".mp4")).findFirst().orElse(null);}
    private UiObject2 scrollTo(UiDevice device,androidx.test.uiautomator.BySelector selector,androidx.test.uiautomator.UiSelector target)throws Exception{
        UiObject2 found=device.findObject(selector);if(found==null)new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector().className("android.widget.ScrollView")).scrollIntoView(target);found=device.wait(Until.findObject(selector),6000);assertNotNull("Download control",found);return found;
    }
    @Test public void settingsShowThreeIndependentDownloadsAndCancelOnlyTheSelectedVideo()throws Exception{
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long idleTimeout=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);
        java.util.Map<String,CountDownLatch> gates=new java.util.concurrent.ConcurrentHashMap<>();for(String name:new String[]{"known","unknown","held","waiting"})gates.put(name,new CountDownLatch(1));
        List<String> requests=Collections.synchronizedList(new ArrayList<>());
        try(ServerSocket server=new ServerSocket(0)){
            Thread responses=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->{try(Socket close=socket){
                socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String request=in.readLine(),line;if(request==null)return;while((line=in.readLine())!=null&&!line.isEmpty()){}
                String name=request.split(" ")[1].substring(1);requests.add(name);boolean known=!name.equals("unknown");String headers="HTTP/1.1 200 OK\r\nConnection: close\r\n"+(known?"Content-Length: 262144\r\n":"")+"\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(new byte[65536]);socket.getOutputStream().flush();CountDownLatch gate=gates.get(name);if(gate!=null)gate.await(90,TimeUnit.SECONDS);if(known)socket.getOutputStream().write(new byte[196608]);
            }catch(Exception ignored){}});response.setDaemon(true);response.start();}catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            try(java.io.OutputStream reserved=app.getContentResolver().openOutputStream(uri("removed"))){assertNotNull(reserved);}
            enqueue(server.getLocalPort(),"known");enqueue(server.getLocalPort(),"unknown");enqueue(server.getLocalPort(),"held");
            await(()->snapshot().active.size()==3&&snapshot().active.stream().allMatch(row->row.progress!=null&&row.progress.bytes==65536));
            assertEquals(3,requests.size());assertEquals(25,active("known").progress.percent);assertEquals(262144,active("known").progress.total);assertEquals(-1,active("unknown").progress.percent);assertEquals(-1,active("unknown").progress.eta);
            enqueue(server.getLocalPort(),"removed");enqueue(server.getLocalPort(),"waiting");await(()->snapshot().waiting.size()==2);assertFalse(requests.contains("removed"));assertFalse(requests.contains("waiting"));
            UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),6000);assertNotNull(menu);menu.click();click(device,"Einstellungen");click(device,"Video-Downloads · Fortschritt und Warteschlange");
            assertTrue(device.wait(Until.hasObject(By.text("Gleichzeitig aktiv: 3 von 3")),6000));assertTrue(device.wait(Until.hasObject(By.textContains("25 %")),6000));assertTrue(device.wait(Until.hasObject(By.textContains("Restzeit für diesen Schritt: ca.")),6000));preview();
            String removed="Video aus Warteschlange entfernen: removed.mp4";scrollTo(device,By.desc(removed),new androidx.test.uiautomator.UiSelector().description(removed)).click();await(()->snapshot().waiting.size()==1);assertEquals("waiting.mp4",snapshot().waiting.get(0).title);
            try(InputStream deleted=app.getContentResolver().openInputStream(uri("removed"))){fail("Cancelled waiting destination must be deleted");}catch(FileNotFoundException expected){}
            String held="Video-Download abbrechen: held.mp4";scrollTo(device,By.desc(held),new androidx.test.uiautomator.UiSelector().description(held)).click();gates.get("held").countDown();
            await(()->active("held")==null&&active("waiting")!=null&&active("waiting").progress.bytes==65536);assertNotNull(active("known"));assertNotNull(active("unknown"));assertFalse(requests.contains("removed"));
            gates.get("known").countDown();await(()->active("known")==null);assertNotNull(active("unknown"));assertEquals(-1,active("unknown").progress.total);
            try(InputStream saved=app.getContentResolver().openInputStream(uri("known"))){byte[] buffer=new byte[8192];int n;long total=0;while((n=saved.read(buffer))!=-1)total+=n;assertEquals(262144,total);}
            click(device,"Schließen");app.startActivity(new Intent(app,MainActivity.class).setAction(VideoDownloadService.SHOW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));assertTrue(device.wait(Until.hasObject(By.text("Video-Downloads")),6000));
            scrollTo(device,By.text("unknown.mp4"),new androidx.test.uiautomator.UiSelector().text("unknown.mp4"));assertTrue(device.wait(Until.hasObject(By.textContains("Gesamtgröße unbekannt")),6000));
            click(device,"Alle abbrechen");await(()->!snapshot().running||snapshot().active.stream().allMatch(row->row.cancelling));gates.values().forEach(CountDownLatch::countDown);await(()->!snapshot().running);assertTrue(snapshot().waiting.isEmpty());assertTrue(device.wait(Until.hasObject(By.text("Keine laufenden Video-Downloads")),6000));assertTrue(device.hasObject(By.text("Videospeicherung abgebrochen.")));
        }finally{Configurator.getInstance().setWaitForIdleTimeout(idleTimeout);gates.values().forEach(CountDownLatch::countDown);main(VideoDownloadService::cancelAll);await(()->!snapshot().running);for(String name:new String[]{"known","removed","unknown","held","waiting"})app.getContentResolver().delete(uri(name),null,null);main(activity::finish);}
    }
}
