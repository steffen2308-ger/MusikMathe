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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real simultaneous HTTP responses, failure isolation, stable cancellation and SAF cleanup. */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class VideoDownloadQueueTest {
    private ServiceController<VideoDownloadService> controller;
    private VideoDownloadService service;
    private File folder;
    private ServerSocket server;
    private TestDocuments documents;
    private int startId;
    private final List<String> requests=Collections.synchronizedList(new ArrayList<>());
    private final Map<String,CountDownLatch> gates=new ConcurrentHashMap<>();
    private final AtomicInteger connections=new AtomicInteger(),peak=new AtomicInteger();
    @Before public void setup()throws Exception{
        Context context=RuntimeEnvironment.getApplication();folder=Files.createTempDirectory("parallel-documents").toFile();documents=new TestDocuments(folder);ProviderInfo info=new ProviderInfo();info.authority="queue.test";info.exported=true;info.grantUriPermissions=true;info.readPermission=info.writePermission="android.permission.MANAGE_DOCUMENTS";documents.attachInfo(context,info);ShadowContentResolver.registerProviderInternal(info.authority,documents);
        controller=Robolectric.buildService(VideoDownloadService.class).create();service=controller.get();server=new ServerSocket(0);
        Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->respond(socket));response.setDaemon(true);response.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();
    }
    private void respond(Socket socket){
        boolean counted=false;
        try(Socket close=socket){socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine(),line;if(first==null)return;while((line=reader.readLine())!=null&&!line.isEmpty()){}
            String path=first.split(" ")[1];requests.add(path);int simultaneous=connections.incrementAndGet();counted=true;peak.accumulateAndGet(simultaneous,Math::max);
            boolean slow=path.equals("/slow");byte[] body=slow?new byte[262144]:("video-data:"+path).getBytes(StandardCharsets.UTF_8);
            String headers="HTTP/1.1 "+(path.equals("/bad")?"500 Failed":"200 OK")+"\r\nContent-Length: "+body.length+(path.equals("/html")?"\r\nContent-Type: text/html":"")+"\r\nConnection: close\r\n\r\n";socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            if(slow){socket.getOutputStream().write(body,0,131072);socket.getOutputStream().flush();}
            CountDownLatch gate=gates.get(path);if(gate!=null)gate.await(15,TimeUnit.SECONDS);
            socket.getOutputStream().write(body,slow?131072:0,slow?131072:body.length);socket.getOutputStream().flush();
        }catch(Exception ignored){}finally{if(counted)connections.decrementAndGet();}
    }
    private void hold(String...paths){for(String path:paths)gates.put(path,new CountDownLatch(1));}
    private void release(String path){gates.get(path).countDown();}
    private void await(BooleanSupplier ready)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){shadowOf(Looper.getMainLooper()).idle();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("Download state: "+VideoDownloadService.status+"; requests: "+requests);}
    @After public void cleanup()throws Exception{
        gates.values().forEach(CountDownLatch::countDown);documents.releaseCopy.countDown();documents.releaseCreate.countDown();VideoDownloadService.cancelAll();await(()->!VideoDownloadService.busy());server.close();controller.destroy();shadowOf(Looper.getMainLooper()).idle();for(File file:folder.listFiles())file.delete();folder.delete();
    }
    private void enqueue(String path,String name)throws Exception{enqueue(path,name,name);}
    private void enqueue(String path,String name,String title)throws Exception{
        assertTrue(new File(folder,name).createNewFile());Intent intent=new Intent().putExtra("url","http://127.0.0.1:"+server.getLocalPort()+path).putExtra("type","MP4").putExtra("headers","{}").putExtra("title",title).putExtra("destination",DocumentsContract.buildDocumentUri("queue.test",name).toString()).putExtra("original",true);service.onStartCommand(intent,0,++startId);
    }
    private String content(String name)throws Exception{return new String(Files.readAllBytes(new File(folder,name).toPath()),StandardCharsets.UTF_8);}
    private VideoDownloadService.ActiveVideo active(String title){return VideoDownloadService.snapshot().active.stream().filter(row->row.title.equals(title)).findFirst().orElse(null);}
    private Intent batch(String... paths)throws Exception{
        java.util.List<MediaLinks.Item> items=new java.util.ArrayList<>();for(String path:paths){MediaLinks.Item item=MediaLinks.item(new org.json.JSONObject().put("url","http://127.0.0.1:"+server.getLocalPort()+path).put("mime","image/png"));assertNotNull(item);items.add(item);}
        VideoDownloadService.enqueueMedia(RuntimeEnvironment.getApplication(),items,DocumentsContract.buildTreeDocumentUri("queue.test","root"),"http://127.0.0.1:"+server.getLocalPort()+"/page","Agent");
        Intent intent=shadowOf(RuntimeEnvironment.getApplication()).getNextStartedService();assertNotNull(intent);assertEquals("media-batch",intent.getAction());assertEquals(1,intent.getExtras().size());assertNull(intent.getStringExtra("url"));return intent;
    }
    @Test public void mediaBatchCreatesFilesOnlyWhenStartedAndNeverOverwritesExistingFiles()throws Exception{
        Files.write(new File(folder,"photo.png").toPath(),"keep".getBytes(StandardCharsets.UTF_8));hold("/photo.png","/second.png","/third.png");
        Intent intent=batch("/photo.png","/second.png","/third.png","/fourth.png");assertTrue(VideoDownloadService.busy());assertEquals(4,VideoDownloadService.snapshot().waiting.size());assertEquals(1,folder.listFiles().length);
        service.onStartCommand(intent,0,++startId);await(()->requests.size()==3&&connections.get()==3);assertEquals(3,VideoDownloadService.snapshot().active.size());assertEquals(1,VideoDownloadService.snapshot().waiting.size());assertFalse(new File(folder,"fourth.png").exists());
        assertTrue(VideoDownloadService.cancelWaiting(VideoDownloadService.snapshot().waiting.get(0).id));release("/photo.png");release("/second.png");release("/third.png");await(()->!VideoDownloadService.busy());
        assertEquals("keep",content("photo.png"));assertEquals("video-data:/photo.png",content("photo (2).png"));assertEquals("video-data:/second.png",content("second.png"));assertFalse(requests.contains("/fourth.png"));assertEquals(3,peak.get());
    }
    @Test public void cancelledBatchTicketCannotStartLaterAndWrongContentTypeDeletesItsFile()throws Exception{
        Intent old=batch("/unused.png");AtomicInteger callbacks=new AtomicInteger();VideoDownloadService.cancelAll(callbacks::incrementAndGet);assertEquals(1,callbacks.get());assertFalse(VideoDownloadService.busy());service.onStartCommand(old,0,++startId);shadowOf(Looper.getMainLooper()).idle();assertTrue(requests.isEmpty());assertEquals(0,folder.listFiles().length);
        Intent next=batch("/html","/valid.png");service.onStartCommand(next,0,++startId);await(()->requests.size()==2&&!VideoDownloadService.busy());assertFalse(new File(folder,"html.png").exists());assertEquals("video-data:/valid.png",content("valid.png"));
    }
    @Test public void cancellingDuringLateMediaFileCreationWaitsForCleanup()throws Exception{
        documents.holdCreate=true;Intent intent=batch("/late.png");service.onStartCommand(intent,0,++startId);assertTrue(documents.createStarted.await(5,TimeUnit.SECONDS));AtomicInteger callbacks=new AtomicInteger();VideoDownloadService.cancelAll(callbacks::incrementAndGet);assertEquals(0,callbacks.get());documents.releaseCreate.countDown();await(()->!VideoDownloadService.busy());assertEquals(1,callbacks.get());assertFalse(new File(folder,"late.png").exists());assertTrue(requests.isEmpty());
    }
    @Test public void threeRequestsOverlapAndFailureFreesOnlyItsOwnSlot()throws Exception{
        hold("/first","/second","/third");enqueue("/first","one.mp4");enqueue("/second","two.mp4");enqueue("/third","three.mp4");await(()->requests.size()==3);assertEquals(3,connections.get());assertEquals(3,VideoDownloadService.snapshot().active.size());
        enqueue("/bad","bad.mp4");enqueue("/fifth","five.mp4");assertEquals(2,VideoDownloadService.snapshot().waiting.size());assertFalse(requests.contains("/bad"));
        release("/first");await(()->requests.contains("/fifth")&&new File(folder,"five.mp4").length()>0);assertNotNull(active("two.mp4"));assertNotNull(active("three.mp4"));assertFalse(new File(folder,"bad.mp4").exists());assertTrue(requests.indexOf("/bad")<requests.indexOf("/fifth"));
        release("/second");release("/third");await(()->!VideoDownloadService.busy());assertEquals("video-data:/first",content("one.mp4"));assertEquals("video-data:/second",content("two.mp4"));assertEquals("video-data:/third",content("three.mp4"));assertEquals("video-data:/fifth",content("five.mp4"));
    }
    @Test public void cancelsByIdentityWithoutTouchingEqualTitlesOrPromotedJobs()throws Exception{
        hold("/first","/second","/third","/fourth");enqueue("/first","one.mp4","same");enqueue("/second","two.mp4","same");enqueue("/third","three.mp4","same");await(()->requests.size()==3);enqueue("/fourth","four.mp4","same");enqueue("/fifth","five.mp4","same");
        VideoDownloadService.Snapshot before=VideoDownloadService.snapshot();String promoted=before.waiting.get(0).id,removed=before.waiting.get(1).id,selected=before.active.get(1).id;
        assertTrue(VideoDownloadService.cancelWaiting(removed));assertFalse(VideoDownloadService.cancelWaiting(removed));assertFalse(new File(folder,"five.mp4").exists());
        assertTrue(VideoDownloadService.cancelActive(selected));assertFalse(VideoDownloadService.cancelActive(selected));assertFalse(new File(folder,"two.mp4").exists());release("/second");await(()->requests.contains("/fourth"));assertFalse(VideoDownloadService.cancelWaiting(promoted));assertEquals(3,VideoDownloadService.snapshot().active.size());assertTrue(new File(folder,"one.mp4").exists());assertTrue(new File(folder,"three.mp4").exists());
        release("/first");release("/third");release("/fourth");await(()->!VideoDownloadService.busy());assertFalse(requests.contains("/fifth"));assertFalse(new File(folder,"two.mp4").exists());assertEquals("video-data:/fourth",content("four.mp4"));
    }
    @Test public void cancellationWaitsForOpenStreamsBeforeReleasingTheSlotAndCallingBack()throws Exception{
        hold("/slow");documents.holdCopy=true;enqueue("/slow","slow.mp4");await(()->active("slow.mp4")!=null&&active("slow.mp4").progress.bytes==131072);assertEquals(50,active("slow.mp4").progress.percent);
        release("/slow");await(()->documents.copyStarted.getCount()==0);assertTrue(active("slow.mp4").progress.phase.contains("2/2"));assertEquals(0,active("slow.mp4").progress.bytes);
        AtomicInteger callbacks=new AtomicInteger();VideoDownloadService.cancelAll(callbacks::incrementAndGet);documents.releaseCopy.countDown();await(()->!VideoDownloadService.busy());assertEquals(1,callbacks.get());assertFalse("Late provider open must not recreate cancelled destination",new File(folder,"slow.mp4").exists());
        File temporary=new File(service.getCacheDir(),"video-export");assertTrue(temporary.listFiles()==null||temporary.listFiles().length==0);
    }
    @Test public void duplicateDestinationAndLateNotificationCannotCancelAnotherJob()throws Exception{
        hold("/first","/second");enqueue("/first","one.mp4");await(()->requests.contains("/first"));String old=active("one.mp4").id;
        Intent duplicate=new Intent().putExtra("url","http://127.0.0.1:"+server.getLocalPort()+"/duplicate").putExtra("type","MP4").putExtra("headers","{}").putExtra("destination",DocumentsContract.buildDocumentUri("queue.test","one.mp4").toString()).putExtra("original",true);service.onStartCommand(duplicate,0,++startId);assertEquals(1,VideoDownloadService.snapshot().active.size());assertTrue(new File(folder,"one.mp4").exists());
        enqueue("/second","two.mp4");release("/first");await(()->active("one.mp4")==null);service.onStartCommand(new Intent().setAction("cancel-job").putExtra("job",old),0,++startId);assertNotNull(active("two.mp4"));assertFalse(active("two.mp4").cancelling);assertFalse(requests.contains("/duplicate"));release("/second");await(()->!VideoDownloadService.busy());
    }
    public static final class TestDocuments extends DocumentsProvider {
        private final File folder;
        volatile boolean holdCopy,holdCreate;
        final CountDownLatch createStarted=new CountDownLatch(1),releaseCreate=new CountDownLatch(1);
        final CountDownLatch copyStarted=new CountDownLatch(1),releaseCopy=new CountDownLatch(1);
        TestDocuments(File folder){this.folder=folder;}
        @Override public boolean onCreate(){return true;}
        @Override public Cursor queryRoots(String[] projection){return new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID});}
        @Override public Cursor queryDocument(String id,String[] projection){MatrixCursor result=new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});result.addRow(new Object[]{id});return result;}
        @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort){MatrixCursor result=new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME});for(File file:folder.listFiles())result.addRow(new Object[]{file.getName()});return result;}
        @Override public boolean isChildDocument(String parent,String child){return "root".equals(parent)&&!child.contains("/");}
        @Override public String createDocument(String parent,String mime,String name)throws java.io.FileNotFoundException{
            if(holdCreate){createStarted.countDown();while(releaseCreate.getCount()>0)try{releaseCreate.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}
            try{if(!new File(folder,name).createNewFile())throw new java.io.IOException("Exists");return name;}catch(java.io.IOException error){throw new java.io.FileNotFoundException(error.getMessage());}
        }
        @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws java.io.FileNotFoundException{if(holdCopy){copyStarted.countDown();boolean interrupted=false;while(releaseCopy.getCount()>0)try{releaseCopy.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){interrupted=true;}if(interrupted)Thread.currentThread().interrupt();}return ParcelFileDescriptor.open(new File(folder,id),ParcelFileDescriptor.parseMode(mode));}
        @Override public void deleteDocument(String id){new File(folder,id).delete();}
    }
}
