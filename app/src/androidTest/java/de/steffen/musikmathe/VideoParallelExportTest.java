package de.steffen.musikmathe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Two real Media3 MP4 exports overlap an original-file transfer and produce distinct playable outputs. */
@RunWith(AndroidJUnit4.class)
public class VideoParallelExportTest {
    private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void main(Runnable action){InstrumentationRegistry.getInstrumentation().runOnMainSync(action);}
    private VideoDownloadService.Snapshot snapshot(){AtomicReference<VideoDownloadService.Snapshot> value=new AtomicReference<>();main(()->value.set(VideoDownloadService.snapshot()));return value.get();}
    private Uri destination(String name){return Uri.parse("content://de.steffen.musikmathe.test.progress/"+name+".mp4");}
    @Test public void convertsTwoVideosWhileDownloadingAnOriginal()throws Exception{
        Activity activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        byte[] body;try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("video/source.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);body=out.toByteArray();}
        String[] names={"convert-a","convert-b","original-parallel"};Set<String> started=ConcurrentHashMap.newKeySet();CountDownLatch allStarted=new CountDownLatch(3),release=new CountDownLatch(1);
        try(ServerSocket server=new ServerSocket(0)){
            Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->{try(Socket close=socket){
                socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine(),line,range=null;if(first==null)return;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();
                String path=first.split(" ")[1];if(started.add(path))allStarted.countDown();if(!release.await(30,TimeUnit.SECONDS))return;
                int start=0,end=body.length-1;if(range!=null){String[] limits=range.replace("bytes=","").split("-",-1);start=Integer.parseInt(limits[0]);if(limits.length>1&&!limits[1].isEmpty())end=Math.min(end,Integer.parseInt(limits[1]));}
                if(start> end)return;String headers="HTTP/1.1 "+(range==null?"200 OK":"206 Partial Content")+"\r\nContent-Type: video/mp4\r\nContent-Length: "+(end-start+1)+"\r\nAccept-Ranges: bytes\r\nConnection: close\r\n"+(range==null?"":"Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n")+"\r\n";socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body,start,end-start+1);socket.getOutputStream().flush();
            }catch(Exception ignored){}});response.setDaemon(true);response.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();
            for(int i=0;i<names.length;i++){String name=names[i];app.getContentResolver().delete(destination(name),null,null);Intent intent=new Intent(app,VideoDownloadService.class).putExtra("url","http://127.0.0.1:"+server.getLocalPort()+"/"+name).putExtra("type","MP4").putExtra("headers","{}").putExtra("title",name).putExtra("destination",destination(name).toString()).putExtra("original",i==2);if(Build.VERSION.SDK_INT>=26)app.startForegroundService(intent);else app.startService(intent);}
            assertTrue("Both exporters and the original must connect before any response finishes: "+started,allStarted.await(15,TimeUnit.SECONDS));assertEquals(3,snapshot().active.size());release.countDown();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(snapshot().running&&System.nanoTime()<deadline)Thread.sleep(100);assertFalse("Parallel MP4 export timed out: "+snapshot().message,snapshot().running);
            for(String name:names)try(ParcelFileDescriptor saved=app.getContentResolver().openFileDescriptor(destination(name),"r")){assertNotNull(name,saved);MediaExtractor media=new MediaExtractor();try{media.setDataSource(saved.getFileDescriptor());boolean audio=false,video=false;for(int i=0;i<media.getTrackCount();i++){MediaFormat format=media.getTrackFormat(i);String mime=format.getString(MediaFormat.KEY_MIME);audio|=mime.startsWith("audio/");video|=mime.startsWith("video/");assertTrue(format.getLong(MediaFormat.KEY_DURATION)>1000000);}assertTrue(name+" missing audio",audio);assertTrue(name+" missing video",video);}finally{media.release();}}
        }finally{release.countDown();main(VideoDownloadService::cancelAll);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);while(snapshot().running&&System.nanoTime()<end)Thread.sleep(100);for(String name:names)app.getContentResolver().delete(destination(name),null,null);main(activity::finish);}
    }
}
