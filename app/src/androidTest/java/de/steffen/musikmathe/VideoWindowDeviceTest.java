package de.steffen.musikmathe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real H.264 playback in native PiP while an independent browser task navigates. */
@RunWith(AndroidJUnit4.class) @SdkSuppress(minSdkVersion=26)
public class VideoWindowDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context app=instrumentation.getTargetContext();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private Object field(MainActivity activity,String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception error){throw new RuntimeException(error);}}
    private Object call(MainActivity activity,String name,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}catch(Exception error){throw new RuntimeException(error);}}
    private WebView web(MainActivity activity){WebView[] result={null};main(()->result[0]=(WebView)call(activity,"activeView",new Class<?>[0]));return result[0];}
    private String js(WebView web,String expression)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={null};main(()->web.evaluateJavascript(expression,value->{result[0]=value;done.countDown();}));
        assertTrue("JavaScript callback",done.await(10,TimeUnit.SECONDS));return new JSONArray("["+result[0]+"]").optString(0);
    }
    private void expect(WebView web,String expression,String expected)throws Exception{
        long deadline=SystemClock.uptimeMillis()+12000;String actual;
        do{actual=js(web,expression);if(expected.equals(actual))return;Thread.sleep(100);}while(SystemClock.uptimeMillis()<deadline);
        assertEquals(expression,expected,actual);
    }
    private void await(java.util.function.BooleanSupplier condition,String label)throws Exception{
        long deadline=SystemClock.uptimeMillis()+15000;while(!condition.getAsBoolean()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(100);assertTrue(label,condition.getAsBoolean());
    }
    private boolean pip(MainActivity activity){boolean[] value={false};main(()->value[0]=activity.isInPictureInPictureMode());return value[0];}
    private SecondBrowserActivity second(){SecondBrowserActivity[] found={null};main(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof SecondBrowserActivity)found[0]=(SecondBrowserActivity)a;});return found[0];}
    private void tutorial(){
        if(device.wait(Until.hasObject(By.res("android:id/immersive_cling_title")),2000)){
            UiObject2 accept=device.findObject(By.res("android:id/ok"));assertNotNull(accept);accept.click();assertTrue(device.wait(Until.gone(By.res("android:id/immersive_cling_title")),5000));
        }
    }
    private UiObject2 pipAction(MainActivity activity,String label)throws Exception{
        UiObject2 action=device.wait(Until.findObject(By.desc(label)),1200);if(action!=null)return action;
        for(int attempt=0;attempt<2;attempt++){
            int[] bounds={0,0,0,0};main(()->{View view=(View)field(activity,"customView");assertNotNull("Live fullscreen video",view);int[] position=new int[2];view.getLocationOnScreen(position);bounds[0]=position[0];bounds[1]=position[1];bounds[2]=view.getWidth();bounds[3]=view.getHeight();});
            device.click(bounds[0]+bounds[2]/2,bounds[1]+bounds[3]/2);
            action=device.wait(Until.findObject(By.desc(label)),5000);if(action!=null)return action;
        }
        fail("Native PiP action: "+label);return null;
    }
    private void preview(String name){
        Bitmap full=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(full);Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);String data=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int chunks=(data.length()+1799)/1800;
        for(int i=0;i<chunks;i++)Log.i("MenuPreview",name+" "+i+"/"+chunks+" "+data.substring(i*1800,Math.min(data.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    @Test public void videoContinuesInPipWhileOtherWindowBrowsesAndUsesNativeMediaActions()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");long idle=Configurator.getInstance().getWaitForIdleTimeout();Configurator.getInstance().setWaitForIdleTimeout(0);
        app.getSharedPreferences("browser",0).edit().clear().commit();MainActivity owner=null;SecondBrowserActivity other=null;
        byte[] media;try(InputStream in=instrumentation.getContext().getAssets().open("video/source.mp4");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);media=out.toByteArray();}
        try(ServerSocket server=new ServerSocket(0)){
            Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread response=new Thread(()->{try(Socket close=socket){
                socket.setSoTimeout(5000);BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine(),line,range=null;if(first==null)return;while((line=reader.readLine())!=null&&!line.isEmpty())if(line.toLowerCase(java.util.Locale.ROOT).startsWith("range:"))range=line.substring(6).trim();
                String path=first.split(" ")[1];boolean video=path.startsWith("/movie.mp4");byte[] body;
                if(video)body=media;else{
                    String content=path.startsWith("/browse")?"<h1>Anderes Browserfenster</h1><input id='work' placeholder='Hier weiterarbeiten'>":"<h1>Videofenster-Test</h1><video id='movie' src='/movie.mp4' controls loop playsinline style='width:100%'></video><button onclick=\"var v=document.getElementById('movie');v.muted=true;v.play();v.requestFullscreen();\">Video im Vollbild starten</button><script>window.updates=0;document.getElementById('movie').addEventListener('timeupdate',function(){window.updates++});document.cookie='shared=normal;path=/';</script>";
                    body=("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif}button{min-height:48px}</style>"+content).getBytes(StandardCharsets.UTF_8);
                }
                int start=0,end=body.length-1;boolean partial=video&&range!=null;if(partial){String[] limits=range.replace("bytes=","").split("-",-1);start=Integer.parseInt(limits[0]);if(limits.length>1&&!limits[1].isEmpty())end=Math.min(end,Integer.parseInt(limits[1]));}
                if(start>end)return;String headers="HTTP/1.1 "+(partial?"206 Partial Content":"200 OK")+"\r\nContent-Type: "+(video?"video/mp4":"text/html; charset=utf-8")+"\r\nContent-Length: "+(end-start+1)+"\r\nAccept-Ranges: bytes\r\nCache-Control: no-store\r\nConnection: close\r\n"+(partial?"Content-Range: bytes "+start+"-"+end+"/"+body.length+"\r\n":"")+"\r\n";socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body,start,end-start+1);socket.getOutputStream().flush();
            }catch(Exception ignored){}});response.setDaemon(true);response.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();
            String base="http://127.0.0.1:"+server.getLocalPort();
            owner=(MainActivity)instrumentation.startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(base+"/video")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            MainActivity original=owner;assertTrue(BrowserPip.supported(owner));WebView movie=web(owner);expect(movie,"String(document.querySelector('video')&&document.querySelector('video').readyState>=2)","true");
            UiObject2 start=device.wait(Until.findObject(By.text("Video im Vollbild starten")),8000);assertNotNull(start);start.click();await(()->{boolean[] value={false};main(()->value[0]=field(original,"customView")!=null);return value[0];},"HTML video enters fullscreen");tutorial();
            UiObject2 floatButton=device.wait(Until.findObject(By.desc("Video schweben lassen")),8000);assertNotNull(floatButton);floatButton.click();
            await(()->pip(original),"Native picture-in-picture entered");await(()->second()!=null,"Second browser task opens automatically");other=second();SecondBrowserActivity browser=other;
            assertNotSame(owner,other);assertNotEquals(owner.getTaskId(),other.getTaskId());assertSame(movie,web(owner));
            main(()->{BrowserStore a=(BrowserStore)field(original,"store"),b=(BrowserStore)field(browser,"store");assertTrue(a.read("tabs").stream().anyMatch(row->row.url.equals(base+"/video")));assertEquals(1,b.read("tabs").size());assertEquals(Address.HOME,b.read("tabs").get(0).url);call(browser,"navigate",new Class<?>[]{String.class},base+"/browse");});
            WebView browse=web(browser);expect(browse,"location.pathname","/browse");expect(browse,"String(document.cookie.includes('shared=normal'))","true");js(browse,"document.getElementById('work').value='Arbeit bleibt';true");
            int before=Integer.parseInt(js(movie,"String(window.updates)"));Thread.sleep(1600);assertTrue("Real video continues while browsing",Integer.parseInt(js(movie,"String(window.updates)"))>before);expect(movie,"String(document.querySelector('video').paused)","false");preview("floating-video-browsing");
            pipAction(owner,"Video pausieren").click();expect(movie,"String(document.querySelector('video').paused)","true");preview("floating-video-controls");
            pipAction(owner,"Video fortsetzen").click();expect(movie,"String(document.querySelector('video').paused)","false");
            pipAction(owner,"Im Browser weitersurfen").click();await(()->second()==browser,"Return to existing browser window");expect(browse,"document.getElementById('work').value","Arbeit bleibt");assertTrue(pip(owner));
            main(original::finishAndRemoveTask);await(original::isDestroyed,"Closing the video destroys its WebView");expect(browse,"location.pathname","/browse");expect(browse,"document.getElementById('work').value","Arbeit bleibt");
        }catch(AssertionError failure){preview("floating-video-failure");ByteArrayOutputStream hierarchy=new ByteArrayOutputStream();device.dumpWindowHierarchy(hierarchy);Log.i("MenuPreview","floating-video-state "+hierarchy.toString("UTF-8"));throw failure;}
        finally{
            MainActivity a=owner;SecondBrowserActivity b=other;
            main(()->{if(a!=null&&!a.isDestroyed())a.finishAndRemoveTask();if(b!=null&&!b.isDestroyed())b.finishAndRemoveTask();});
            Configurator.getInstance().setWaitForIdleTimeout(idle);
        }
    }
    @Test public void privateActivityAndUntrustedAppsCannotOpenVideoWindowControls()throws Exception{
        ActivityInfo privateInfo=app.getPackageManager().getActivityInfo(new ComponentName(app,PrivateActivity.class),0);
        assertEquals(0,privateInfo.flags&ActivityInfo.FLAG_SUPPORTS_PICTURE_IN_PICTURE);
        assertFalse(app.getPackageManager().getActivityInfo(new ComponentName(app,SecondBrowserActivity.class),0).exported);
        assertFalse(app.getPackageManager().getReceiverInfo(new ComponentName(app,VideoWindowReceiver.class),0).exported);
    }
}
