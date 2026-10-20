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
    private void preview(){android.graphics.Bitmap full=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();if(full==null)return;android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out);String value=android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP);int count=(value.length()+1799)/1800;for(int i=0;i<count;i++)android.util.Log.i("MenuPreview","video-thumbnails "+i+"/"+count+" "+value.substring(i*1800,Math.min(value.length(),(i+1)*1800)));if(small!=full)small.recycle();full.recycle();}
    @Test public void listsNetworkHlsAndExtensionlessHtmlVideo()throws Exception{
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext(),test=InstrumentationRegistry.getInstrumentation().getContext();UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try(ServerSocket server=new ServerSocket(0)){
            Thread responses=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->{try(Socket close=socket){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine(),line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
                String path=first.split(" ")[1],mime="text/html";byte[] body;
                if(path.startsWith("/stream")){mime="video/mp4";try(InputStream in=test.getAssets().open("video/source.mp4");ByteArrayOutputStream data=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)data.write(buffer,0,n);body=data.toByteArray();}}
                else if(path.equals("/poster.jpg")){mime="image/jpeg";android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(160,90,android.graphics.Bitmap.Config.ARGB_8888);image.eraseColor(android.graphics.Color.rgb(20,160,220));ByteArrayOutputStream data=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,data);image.recycle();body=data.toByteArray();}
                else if(path.endsWith(".m3u8")){mime="application/vnd.apple.mpegurl";body="#EXTM3U\n#EXT-X-ENDLIST\n".getBytes(StandardCharsets.UTF_8);}
                else body="<html><meta name='viewport' content='width=device-width,initial-scale=1'><h1>Video-Testseite</h1><video title='Vorschau-Testfilm' poster='/poster.jpg' controls preload='metadata' src='/stream?id=1'></video><p id='ready'>Waiting for media sources</p><script>fetch('/detected.m3u8');function media(){if(!window.__mmVideoHook){setTimeout(media,100);return;}fetch('/stream-other').then(function(r){return r.arrayBuffer();}).then(function(){document.getElementById('ready').textContent='Media sources ready';});}media();</script></html>".getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
            }catch(Exception ignored){}});response.setDaemon(true);response.start();}catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(android.net.Uri.parse("http://127.0.0.1:"+server.getLocalPort()+"/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            assertTrue(device.wait(Until.hasObject(By.text("Video-Testseite")),15000));
            assertTrue("Wait for a MIME response after media hooks are installed",device.wait(Until.hasObject(By.text("Media sources ready")),15000));
            UiObject2 button=device.wait(Until.findObject(By.descStartsWith("Videos auf dieser Seite")),10000);assertNotNull(button);button.click();
            assertTrue("HLS request must be detected",device.wait(Until.hasObject(By.textStartsWith("HLS ·")),10000));assertTrue("Extensionless HTML video must be detected",device.wait(Until.hasObject(By.textStartsWith("Vorschau-Testfilm")),10000));preview();assertTrue("Fetched video MIME type must be detected",device.wait(Until.hasObject(By.textContains("stream-other")),10000));assertNotNull("Real poster next to the video name",device.wait(Until.findObject(By.desc("Video-Vorschaubild")),10000));
            preview();device.findObject(By.textStartsWith("Vorschau-Testfilm")).click();assertNotNull(device.wait(Until.findObject(By.text("Als MP4 speichern")),5000));assertNotNull("Selected movie retains its preview",device.wait(Until.findObject(By.desc("Video-Vorschaubild")),10000));device.pressBack();
        }
    }
}
