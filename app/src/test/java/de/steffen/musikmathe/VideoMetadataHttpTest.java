package de.steffen.musikmathe;

import android.webkit.CookieManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class VideoMetadataHttpTest {
    private OkHttpClient client(){return new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(3,TimeUnit.SECONDS).build();}
    @Test public void headRedirectKeepsCredentialsOnTheirOwnOriginAndDoesNotDownloadVideo()throws Exception{
        try(ServerSocket server=new ServerSocket(0)){
            List<String> requests=Collections.synchronizedList(new ArrayList<>());int port=server.getLocalPort();Thread responses=new Thread(()->{try{for(int i=0;i<2;i++)try(Socket socket=server.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));StringBuilder request=new StringBuilder();String line;while((line=reader.readLine())!=null&&!line.isEmpty())request.append(line).append('\n');requests.add(request.toString());String response=i==0?"HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:"+port+"/movie.mp4\r\nContent-Length: 0\r\n\r\n":"HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: 2000000\r\n\r\n";socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            }}catch(Exception e){throw new RuntimeException(e);}});responses.setDaemon(true);responses.start();
            CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().setCookie("http://localhost:"+port,"source=secret");CookieManager.getInstance().setCookie("http://127.0.0.1:"+port,"target=correct");
            VideoCatalog.Item item=new VideoCatalog.Item("http://localhost:"+port+"/movie.mp4?token=secret","MP4",Map.of("Authorization","Bearer secret"));VideoMetadata metadata=VideoMetadataProbe.read(item,client());assertEquals("2,00 MB",metadata.get("Dateigröße"));responses.join(2000);assertEquals(2,requests.size());assertTrue(requests.get(0).startsWith("HEAD "));assertTrue(requests.get(1).startsWith("HEAD "));assertTrue(requests.get(0).contains("Bearer secret"));assertFalse(requests.get(1).contains("Bearer secret"));assertFalse(requests.get(1).contains("source=secret"));assertTrue(requests.get(1).contains("target=correct"));assertFalse(metadata.details(item).contains("token=secret"));
        }
    }
    @Test public void manifestReadExtractsDurationWithoutFollowingSegmentUrls()throws Exception{
        try(ServerSocket server=new ServerSocket(0)){
            List<String> requests=Collections.synchronizedList(new ArrayList<>());byte[] body="#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nsecret-segment.ts\n#EXT-X-ENDLIST\n".getBytes(StandardCharsets.UTF_8);Thread response=new Thread(()->{try(Socket socket=server.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String line;while((line=reader.readLine())!=null&&!line.isEmpty())requests.add(line);socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: "+body.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
            }catch(Exception e){throw new RuntimeException(e);}});response.setDaemon(true);response.start();VideoCatalog.Item item=new VideoCatalog.Item("http://127.0.0.1:"+server.getLocalPort()+"/movie.m3u8","HLS",Map.of());VideoMetadata metadata=VideoMetadataProbe.read(item,client());assertEquals("0:02",metadata.get("Dauer"));assertEquals("",metadata.get("Dateigröße"));assertFalse(metadata.get("Manifestgröße").isEmpty());assertEquals(1,requests.stream().filter(r->r.startsWith("GET ")).count());assertFalse(requests.stream().anyMatch(r->r.contains("secret-segment.ts")));
        }
    }
}
