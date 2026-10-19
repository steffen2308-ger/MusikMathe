package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real foreground-service queue, HTTP reads and distinct SAF outputs. */
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class VideoDownloadQueueTest {
    private ServiceController<VideoDownloadService> controller;
    private VideoDownloadService service;
    private File folder;
    private ServerSocket server;
    private final List<String> requests=Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch releaseFirst=new CountDownLatch(1),releaseSecond=new CountDownLatch(1);
    private boolean holdSecond;
    private int startId;
    @Before public void setup()throws Exception{
        Context context=RuntimeEnvironment.getApplication();folder=Files.createTempDirectory("queue-documents").toFile();
        TestDocuments provider=new TestDocuments(folder);ProviderInfo info=new ProviderInfo();info.authority="queue.test";info.exported=true;info.grantUriPermissions=true;info.readPermission=info.writePermission="android.permission.MANAGE_DOCUMENTS";provider.attachInfo(context,info);ShadowContentResolver.registerProviderInternal(info.authority,provider);
        controller=Robolectric.buildService(VideoDownloadService.class).create();service=controller.get();
        server=new ServerSocket(0);Thread responses=new Thread(()->{
            while(!server.isClosed())try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine();if(first==null)continue;String path=first.split(" ")[1];String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
                requests.add(path);if(path.equals("/first"))releaseFirst.await(10,TimeUnit.SECONDS);if(holdSecond&&path.equals("/second"))releaseSecond.await(10,TimeUnit.SECONDS);
                byte[] body=("video-data:"+path).getBytes(StandardCharsets.UTF_8);String headers="HTTP/1.1 "+(path.equals("/bad")?"500 Failed":"200 OK")+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);
            }catch(Exception ignored){}
        });responses.setDaemon(true);responses.start();
    }
    @After public void cleanup()throws Exception{
        releaseFirst.countDown();releaseSecond.countDown();VideoDownloadService.cancelAll();server.close();
        controller.destroy();shadowOf(Looper.getMainLooper()).idle();for(File file:folder.listFiles())file.delete();folder.delete();
    }
    private void await(BooleanSupplier ready)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("Queue did not reach expected state; "+VideoDownloadService.status+"; HTTP requests: "+requests);
    }
    private void enqueue(String path,String name)throws Exception{
        File file=new File(folder,name);assertTrue(file.createNewFile());
        Uri destination=DocumentsContract.buildDocumentUri("queue.test",name);
        Intent intent=new Intent().putExtra("url","http://127.0.0.1:"+server.getLocalPort()+path).putExtra("type","MP4").putExtra("headers","{}").putExtra("title",name).putExtra("destination",destination.toString()).putExtra("original",true);
        service.onStartCommand(intent,0,++startId);
    }
    @Test public void savesInOrderAndContinuesAfterFailedDownload()throws Exception{
        enqueue("/first","one.mp4");await(()->requests.size()==1);
        enqueue("/bad","bad.mp4");enqueue("/second","two.mp4");enqueue("/third","three.mp4");
        assertEquals(4,VideoDownloadService.queueLabels().size());assertTrue(VideoDownloadService.status.contains("3 in Warteschlange"));assertEquals(1,requests.size());
        releaseFirst.countDown();await(()->!VideoDownloadService.busy());
        assertEquals(java.util.Arrays.asList("/first","/bad","/second","/third"),requests);
        assertEquals("video-data:/first",Files.readString(new File(folder,"one.mp4").toPath()));assertFalse(new File(folder,"bad.mp4").exists());
        assertEquals("video-data:/second",Files.readString(new File(folder,"two.mp4").toPath()));assertEquals("video-data:/third",Files.readString(new File(folder,"three.mp4").toPath()));
    }
    @Test public void cancellationSkipsCurrentAndClearAllDeletesWaitingTargets()throws Exception{
        holdSecond=true;enqueue("/first","one.mp4");await(()->requests.size()==1);enqueue("/second","two.mp4");enqueue("/third","three.mp4");
        VideoDownloadService.cancelCurrent();assertFalse(new File(folder,"one.mp4").exists());assertTrue(VideoDownloadService.busy());
        releaseFirst.countDown();await(()->requests.contains("/second"));assertEquals(2,VideoDownloadService.queueLabels().size());
        VideoDownloadService.cancelAll();assertFalse(VideoDownloadService.busy());assertFalse(new File(folder,"two.mp4").exists());assertFalse(new File(folder,"three.mp4").exists());
        releaseSecond.countDown();enqueue("/fourth","four.mp4");await(()->!VideoDownloadService.busy());
        assertEquals(java.util.Arrays.asList("/first","/second","/fourth"),requests);assertEquals("video-data:/fourth",Files.readString(new File(folder,"four.mp4").toPath()));
        File temporary=new File(service.getCacheDir(),"video-export");assertTrue(temporary.listFiles()==null||temporary.listFiles().length==0);
    }
    public static final class TestDocuments extends DocumentsProvider {
        private final File folder;
        TestDocuments(File folder){this.folder=folder;}
        @Override public boolean onCreate(){return true;}
        @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});}
        @Override public Cursor queryDocument(String id,String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID});}
        @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort){return queryDocument(parent,projection);}
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws java.io.FileNotFoundException{return ParcelFileDescriptor.open(new File(folder,id),ParcelFileDescriptor.parseMode(mode));}
        @Override public void deleteDocument(String id){assertTrue(new File(folder,id).delete());}
    }
}
