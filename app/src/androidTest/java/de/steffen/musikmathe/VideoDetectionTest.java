package de.steffen.musikmathe;
import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class VideoDetectionTest {
    @Test public void listsNetworkHlsAndExtensionlessHtmlVideo()throws Exception{
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext(),test=InstrumentationRegistry.getInstrumentation().getContext();UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ServerSocket server=new ServerSocket(0)){
            Thread responses=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->{try(Socket close=socket){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
                String path=first.split(" ")[1],mime="text/html";byte[] body;
                if(path.startsWith("/stream")){mime="video/mp4";try(InputStream in=test.getAssets().open("video/source.mp4");ByteArrayOutputStream data=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)data.write(buffer,0,n);body=data.toByteArray();}}
                else if(path.endsWith(".m3u8")){mime="application/vnd.apple.mpegurl";body="#EXTM3U\n#EXT-X-ENDLIST\n".getBytes(StandardCharsets.UTF_8);}
                else body="<html><meta name='viewport' content='width=device-width,initial-scale=1'><h1>Video-Testseite</h1><video controls preload='metadata' src='/stream?id=1'></video><script>fetch('/detected.m3u8');</script></html>".getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
            }catch(Exception ignored){}});response.setDaemon(true);response.start();}catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            app.startActivity(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            UiObject2 address=device.wait(Until.findObject(By.desc("Adresse oder Suchbegriff")),10000);assertNotNull(address);address.click();address.setText("http://127.0.0.1:"+server.getLocalPort()+"/");device.pressEnter();assertTrue(device.wait(Until.hasObject(By.text("Video-Testseite")),15000));
            UiObject2 button=device.wait(Until.findObject(By.descStartsWith("Videos auf dieser Seite")),10000);assertNotNull(button);button.click();
            assertTrue("HLS request must be detected",device.wait(Until.hasObject(By.textStartsWith("HLS ·")),10000));assertTrue("Extensionless HTML video must be detected",device.wait(Until.hasObject(By.textStartsWith("Video ·")),10000));device.pressBack();
        }
    }
}
