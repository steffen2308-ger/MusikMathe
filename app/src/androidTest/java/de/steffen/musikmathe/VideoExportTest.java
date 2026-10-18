package de.steffen.musikmathe;
import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VideoExportTest {
    @Test public void exportsMp4HlsAndSeparateDashTracksAsPlayableMp4()throws Exception{
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();Context test=InstrumentationRegistry.getInstrumentation().getContext();
        try(ServerSocket server=new ServerSocket(0)){
            Thread responder=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();new Thread(()->{try(Socket close=socket){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String request=reader.readLine();String range=null,line;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();
                String path=request.split(" ")[1];if(path.contains(".."))throw new IllegalArgumentException();byte[] body;
                try(InputStream input=test.getAssets().open(path.substring(1).replace("live.m3u8","index.m3u8"));ByteArrayOutputStream data=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)data.write(buffer,0,n);body=data.toByteArray();}
                if(path.endsWith("live.m3u8"))body=new String(body,StandardCharsets.UTF_8).replace("#EXT-X-ENDLIST", "").replace("#EXT-X-PLAYLIST-TYPE:VOD", "").getBytes(StandardCharsets.UTF_8);
                int start=0,end=body.length-1;boolean partial=range!=null;
                if(partial){String[] limits=range.replace("bytes=","").split("-",-1);start=Integer.parseInt(limits[0]);if(limits.length>1&&!limits[1].isEmpty())end=Math.min(end,Integer.parseInt(limits[1]));}
                String mime=path.endsWith("m3u8")?"application/vnd.apple.mpegurl":path.endsWith("mpd")?"application/dash+xml":path.endsWith("ts")?"video/mp2t":"video/mp4";
                String headers="HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: "+mime+"\r\nContent-Length: "+(end-start+1)+"\r\nAccept-Ranges: bytes\r\nConnection: close\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n":"")+"\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body,start,end-start+1);socket.getOutputStream().flush();
            }catch(Exception ignored){}}).start();}catch(Exception ignored){}});responder.setDaemon(true);responder.start();
            String[] paths={"/video/source.mp4","/video/hls/index.m3u8","/video/dash/manifest.mpd","/video/hls/live.m3u8"};String[] types={"MP4","HLS","DASH","HLS"};
            for(int i=0;i<paths.length;i++){
                String url="http://127.0.0.1:"+server.getLocalPort()+paths[i],type=types[i];long clip=i==3?1000:0;File output=new File(app.getCacheDir(),"test-export-"+i+".mp4");output.delete();
                CountDownLatch done=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();AtomicReference<VideoExporter> active=new AtomicReference<>();
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{VideoExporter exporter=new VideoExporter();active.set(exporter);exporter.start(app,url,type,Collections.emptyMap(),output,clip,new VideoExporter.Listener(){public void completed(){done.countDown();}public void failed(String message){error.set(message);done.countDown();}public void progress(int percent){}});});
                try{
                    assertTrue(type+" export timed out",done.await(60,TimeUnit.SECONDS));assertNull(type+" export failed: "+error.get(),error.get());assertTrue(output.length()>1000);
                    MediaExtractor media=new MediaExtractor();try{media.setDataSource(output.getAbsolutePath());boolean audio=false,video=false;for(int t=0;t<media.getTrackCount();t++){MediaFormat f=media.getTrackFormat(t);String mime=f.getString(MediaFormat.KEY_MIME);if(mime.startsWith("audio/"))audio=true;if(mime.startsWith("video/"))video=true;assertTrue(f.getLong(MediaFormat.KEY_DURATION)>(clip>0?500000:1000000));}assertTrue(type+" missing audio",audio);assertTrue(type+" missing video",video);}finally{media.release();}
                }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->active.get().cancel());output.delete();}
            }
        }
    }
}
