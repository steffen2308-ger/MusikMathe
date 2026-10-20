package de.steffen.musikmathe;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real password authentication, two persistent WebView areas and recovery after private-process death. */
@RunWith(AndroidJUnit4.class)
public class PrivateIsolationTest {
    private UiDevice device;
    private String base;
    private int navigation;
    private final java.util.List<String> requests=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private void menu(String label){UiObject2 button=device.wait(Until.findObject(By.desc("Menü")),10000);assertNotNull("Browser menu",button);button.click();UiObject2 item=device.findObject(By.text(label));if(item==null)try{new androidx.test.uiautomator.UiScrollable(new androidx.test.uiautomator.UiSelector().className("android.widget.ListView")).scrollIntoView(new androidx.test.uiautomator.UiSelector().text(label));}catch(Exception error){throw new AssertionError(label,error);}item=device.wait(Until.findObject(By.text(label)),5000);assertNotNull(label,item);item.click();}
    private void navigate(String path,String expected)throws Exception{String token=String.valueOf(++navigation);UiObject2 input=device.wait(Until.findObject(By.desc("Adresse oder Suchbegriff")),10000);assertNotNull("Address field",input);input.click();input.setText(base+path+"?fixture="+token);device.pressEnter();assertTrue("Fresh fixture navigation "+token,device.wait(Until.hasObject(By.text("Navigation "+token)),25000));state(expected);}
    private void state(String expected){
        if(device.wait(Until.hasObject(By.textContains(expected)),25000))return;
        java.util.List<String> visible=new java.util.ArrayList<>();for(UiObject2 item:device.findObjects(By.textStartsWith("cookie:")))visible.add(item.getText());
        UiObject2 address=device.findObject(By.desc("Adresse oder Suchbegriff"));
        fail("Expected page state: "+expected+"; actual="+visible+"; address="+(address==null?"none":address.getText())+"; fixture requests="+requests);
    }
    private void open(String password)throws Exception{menu("Zu privaten Tabs");PrivateTestSupport.unlock(device,password);}
    private void home(){device.pressHome();String launcher=device.getLauncherPackageName();assertNotNull("Launcher",launcher);assertTrue("Home is foreground",device.wait(Until.hasObject(By.pkg(launcher).depth(0)),10000));}
    private void normal(){menu("Zu normalen Tabs");assertTrue(device.wait(Until.gone(By.descStartsWith("Privater Tab")),15000));}
    private void history(String present,String absent){menu("Verlauf");assertTrue("Own private history",device.wait(Until.hasObject(By.textContains(present)),8000));assertFalse("Other area history stays hidden",device.hasObject(By.textContains(absent)));device.pressBack();}
    @Test public void passwordsRestoreIndependentHistoryCookiesStorageAndRecoverAfterProcessDeath()throws Exception{
        device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        String passwordA=PrivateTestSupport.password(),passwordB=PrivateTestSupport.password();
        try(ServerSocket server=new ServerSocket(0)){
            base="http://127.0.0.1:"+server.getLocalPort();
            Thread responses=new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine();if(first==null)continue;String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
                requests.add(first.split(" ")[1]);
                String seed=first.contains("/normal")?"N":first.contains("/area-a")?"A":first.contains("/area-b")?"B":null;
                String requestPath=first.split(" ")[1];String nonce=requestPath.contains("?fixture=")?requestPath.substring(requestPath.indexOf("?fixture=")+9):"restored";
                String title="A".equals(seed)?"Area A private history":"B".equals(seed)?"Area B private history":"N".equals(seed)?"Normal browser history":"Probe state";
                String script=seed==null?"":"localStorage.setItem('marker','"+seed+"');";
                String html="<html><meta name='viewport' content='width=device-width,initial-scale=1'><title>"+title+"</title><p>Navigation "+nonce+"</p><h1 id='result'>Loading</h1><script>"+script+"document.getElementById('result').textContent='cookie:'+(document.cookie||'none')+' storage:'+(localStorage.getItem('marker')||'none');</script></html>";
                byte[] body=html.getBytes(StandardCharsets.UTF_8);String headers="HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nConnection: close\r\n"+(seed==null?"":"Set-Cookie: marker="+seed+"; Path=/; Max-Age=86400; SameSite=Lax\r\n")+"Content-Length: "+body.length+"\r\n\r\n";socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();
            }catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            context.getSharedPreferences("browser",0).edit().clear().commit();context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            navigate("/normal","cookie:marker=N storage:N");
            open(passwordA);navigate("/probe","cookie:none storage:none");navigate("/area-a","cookie:marker=A storage:A");navigate("/probe","cookie:marker=A storage:A");normal();navigate("/probe","cookie:marker=N storage:N");
            open(passwordB);
            navigate("/probe","cookie:none storage:none");
            navigate("/area-b","cookie:marker=B storage:B");
            navigate("/probe","cookie:marker=B storage:B");
            history("Area B private history","Area A private history");normal();
            open(passwordA);state("cookie:marker=A storage:A");history("Area A private history","Area B private history");
            // Home/background must hide the area and Back must not bypass its native password gate.
            home();context.startActivity(new Intent(context,PrivateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            assertNotNull(device.wait(Until.findObject(By.desc("Passwort für privaten Bereich")),15000));assertFalse(device.hasObject(By.textContains("cookie:marker=A")));device.pressBack();assertTrue(device.hasObject(By.desc("Passwort für privaten Bereich")));PrivateTestSupport.unlock(device,passwordA);state("cookie:marker=A storage:A");
            // Kill only the separate private PID. Its Keystore lease must recover the closed databases.
            home();String pid=device.executeShellCommand("pidof de.steffen.musikmathe:private").trim();assertTrue("Separate private PID",pid.matches("[0-9]+"));device.executeShellCommand("run-as de.steffen.musikmathe kill -9 "+pid);
            InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));navigate("/probe","cookie:marker=N storage:N");
            open(passwordA);state("cookie:marker=A storage:A");history("Area A private history","Area B private history");normal();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);File working=new File(context.getDataDir(),"app_webview_private"),lease=new File(PrivateProfiles.directory(context),"active.lease");while((working.exists()||lease.exists())&&System.nanoTime()<deadline)Thread.sleep(100);assertFalse("Plaintext working directory removed after closing",working.exists());assertFalse("Temporary recovery key removed after sealing",lease.exists());
            File[] files=PrivateProfiles.directory(context).listFiles();assertNotNull(files);boolean web=false;for(File file:files)if(file.getName().endsWith(".data")||file.getName().endsWith(".web")){String raw=new String(Files.readAllBytes(file.toPath()),StandardCharsets.ISO_8859_1);assertFalse(raw.contains("Area A private history"));assertFalse(raw.contains("Area B private history"));assertFalse(raw.contains(passwordA));assertFalse(raw.contains(base));if(file.getName().endsWith(".web"))web=true;}assertTrue("Website state archived with authenticated encryption",web);
            assertTrue(new BrowserStore(context).read("history").stream().noneMatch(row->row.title.startsWith("Area A private")||row.title.startsWith("Area B private")));
        }finally{if(device!=null&&device.hasObject(By.descStartsWith("Privater Tab")))try{normal();}catch(Exception ignored){}}
    }
}
