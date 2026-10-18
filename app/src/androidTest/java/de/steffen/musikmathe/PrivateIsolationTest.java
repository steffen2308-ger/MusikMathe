package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Exercises the actual Android WebView in both processes, without an external website. */
@RunWith(AndroidJUnit4.class)
public class PrivateIsolationTest {
    private UiDevice device;
    private String base;
    private void menu(String label) {
        UiObject2 button=device.wait(Until.findObject(By.desc("Menü")),10000);assertNotNull("Browser menu",button);button.click();
        UiObject2 item=device.wait(Until.findObject(By.text(label)),10000);assertNotNull(label,item);item.click();
    }
    private void navigate(String path,String expected) throws Exception {
        UiObject2 input=device.wait(Until.findObject(By.desc("Adresse oder Suchbegriff")),10000);assertNotNull("Address field",input);
        input.click();input.setText(base+path);device.pressEnter();
        assertTrue("Expected page text: "+expected,device.wait(Until.hasObject(By.textContains(expected)),20000));
    }
    @Test public void cookiesAndDomStorageStayIsolatedAndPrivateSessionIsReset() throws Exception {
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.pressHome();
        try(ServerSocket server=new ServerSocket(0)) {
            base="http://127.0.0.1:"+server.getLocalPort();
            Thread responses=new Thread(()->{
                while(!server.isClosed())try(Socket socket=server.accept()) {
                    BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
                    String first=reader.readLine();if(first==null)continue;String line;while((line=reader.readLine())!=null && !line.isEmpty()){}
                    String seed=first.contains("/normal")?"N":first.contains("/private")?"P":null;
                    String script=seed==null?"":"localStorage.setItem('marker','"+seed+"');";
                    String html="<html><meta name='viewport' content='width=device-width,initial-scale=1'><h1 id='result'>Loading</h1><script>"+script+"document.getElementById('result').textContent='cookie:'+(document.cookie||'none')+' storage:'+(localStorage.getItem('marker')||'none');</script></html>";
                    byte[] body=html.getBytes(StandardCharsets.UTF_8);
                    String headers="HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\n"+(seed==null?"":"Set-Cookie: marker="+seed+"; Path=/\r\n")+"Content-Length: "+body.length+"\r\n\r\n";
                    socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
                }catch(Exception ignored){}
            });responses.setDaemon(true);responses.start();
            Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
            context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            navigate("/normal","cookie:marker=N storage:N");
            menu("Neuer privater Tab");assertTrue(device.wait(Until.hasObject(By.descStartsWith("Privater Tab")),15000));
            navigate("/probe","cookie:none storage:none");
            navigate("/private","cookie:marker=P storage:P");
            menu("Zu normalen Tabs");navigate("/probe","cookie:marker=N storage:N");
            menu("Zu privaten Tabs");navigate("/probe","cookie:marker=P storage:P");
            menu("Alle privaten Tabs schließen");assertTrue(device.wait(Until.gone(By.descStartsWith("Privater Tab")),15000));
            navigate("/probe","cookie:marker=N storage:N");
            menu("Neuer privater Tab");assertTrue(device.wait(Until.hasObject(By.descStartsWith("Privater Tab")),15000));
            navigate("/probe","cookie:none storage:none");
            menu("Alle privaten Tabs schließen");
        }
    }
}
