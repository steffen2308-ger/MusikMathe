package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Same instrumented workload on the previous APK and this APK; no wall-time pass threshold. */
@RunWith(AndroidJUnit4.class)
public class BrowserPerformanceBenchmarkTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context app=instrumentation.getTargetContext();
    private MainActivity activity;
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private Object call(String method,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    @SuppressWarnings("unchecked") private List<MainActivity.Tab> tabs(){try{Field field=MainActivity.class.getDeclaredField("tabs");field.setAccessible(true);return (List<MainActivity.Tab>)field.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private WebView web(){return (WebView)call("activeView",new Class<?>[0]);}
    private void frame()throws Exception{
        CountDownLatch drawn=new CountDownLatch(1);main(()->web().postVisualStateCallback(SystemClock.uptimeMillis(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){Choreographer.getInstance().postFrameCallback(time->drawn.countDown());}}));
        assertTrue("Visible tab reaches a drawable frame",drawn.await(12,TimeUnit.SECONDS));
    }
    private String js(String script)throws Exception{String[] value={null};CountDownLatch done=new CountDownLatch(1);main(()->web().evaluateJavascript(script,s->{value[0]=s;done.countDown();}));assertTrue(done.await(8,TimeUnit.SECONDS));return value[0];}
    private void loaded()throws Exception{long end=SystemClock.uptimeMillis()+15000;String result;do{result=js("document.readyState==='complete'&&(document.title==='Performance fixture'||!!window.BestBrowserHome)");if("true".equals(result)){frame();return;}Thread.sleep(30);}while(SystemClock.uptimeMillis()<end);fail("Fixture finishes loading");}
    private void seed(String base){
        app.getSharedPreferences("browser",0).edit().clear().putBoolean("wifi_link_preloading",false).commit();
        BrowserStore store=new BrowserStore(app);List<BrowserStore.Entry> history=new ArrayList<>(),rows=new ArrayList<>();
        for(int i=0;i<500;i++)history.add(new BrowserStore.Entry("https://history-"+i+".example/article","Stored article "+i,i,false,500-i));store.write("history",history);
        rows.add(new BrowserStore.Entry(base+"/old","Restored article",0));for(int i=1;i<19;i++)rows.add(new BrowserStore.Entry(Address.HOME,"Start "+i,0));store.write("tabs",rows);store.selected(0);
        app.getSharedPreferences("browser",0).edit().putBoolean("wifi_link_preloading",false).commit();
    }
    private static double median(List<Double> values){List<Double> sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(sorted.size()/2);}
    @Test public void benchmarkRestoredStartupAndWarmTabSwitches()throws Exception{
        String label=InstrumentationRegistry.getArguments().getString("performanceBenchmark","");Assume.assumeTrue("Explicit benchmark invocation only",!label.isEmpty());
        androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation);device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");
        List<Double> starts=new ArrayList<>(),switchUi=new ArrayList<>(),switchFrames=new ArrayList<>();int[] created={0};AtomicInteger attaches=new AtomicInteger(),detaches=new AtomicInteger();
        try(Fixture fixture=new Fixture()){
            for(int attempt=0;attempt<4;attempt++){
                seed(fixture.base());long begin=SystemClock.elapsedRealtimeNanos();
                activity=(MainActivity)instrumentation.startActivitySync(new Intent(app,MainActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse(fixture.base()+"/incoming")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                loaded();starts.add((SystemClock.elapsedRealtimeNanos()-begin)/1000000.0);
                main(()->{created[0]=0;for(MainActivity.Tab tab:tabs())if(tab.view!=null)created[0]++;});
                if("current".equals(label))assertEquals("Incoming URL does not instantiate the previously selected tab",1,created[0]);
                if(attempt<3){main(activity::finish);instrumentation.waitForIdleSync();activity=null;}
            }
            for(int index:new int[]{0,1,19}){main(()->call("switchTab",new Class<?>[]{int.class},index));loaded();js("window.performanceTabToken='retained';true");}
            View.OnAttachStateChangeListener listener=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){attaches.incrementAndGet();}public void onViewDetachedFromWindow(View view){detaches.incrementAndGet();}};
            main(()->{for(int i:new int[]{0,1,19})tabs().get(i).view.addOnAttachStateChangeListener(listener);});int requestsBefore=fixture.requests.get();
            for(int round=0;round<30;round++){
                int index=new int[]{0,1,19}[round%3];double[] ui={0};long begin=SystemClock.elapsedRealtimeNanos();
                main(()->{long started=SystemClock.elapsedRealtimeNanos();call("switchTab",new Class<?>[]{int.class},index);ui[0]=(SystemClock.elapsedRealtimeNanos()-started)/1000000.0;});frame();switchUi.add(ui[0]);switchFrames.add((SystemClock.elapsedRealtimeNanos()-begin)/1000000.0);
            }
            assertEquals("Warm switching does not reload the pages",requestsBefore,fixture.requests.get());assertEquals("\"retained\"",js("window.performanceTabToken"));
            if("current".equals(label)){assertEquals("Recent tabs keep their native attachment",0,attaches.get());assertEquals(0,detaches.get());}
            main(()->{for(int i:new int[]{0,1,19})tabs().get(i).view.removeOnAttachStateChangeListener(listener);});
            JSONObject report=new JSONObject().put("version",label).put("restoredTabs",19).put("historyRows",500).put("startupCreatedWebViews",created[0]).put("firstActivityReadyMs",starts.get(0)).put("warmActivityReadyMedianMs",median(starts.subList(1,starts.size()))).put("tabSwitchUiMedianMs",median(switchUi)).put("tabSwitchFrameMedianMs",median(switchFrames)).put("warmSwitches",30).put("attachEvents",attaches.get()).put("detachEvents",detaches.get());
            Log.i("MenuPreview","browser-performance "+report);
            try(java.io.FileOutputStream out=app.openFileOutput("browser-performance-"+label+".json",Context.MODE_PRIVATE)){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}
        }finally{if(activity!=null)main(activity::finish);}
    }
    private static final class Fixture implements AutoCloseable{
        final ServerSocket server=new ServerSocket(0);final AtomicInteger requests=new AtomicInteger();
        Fixture()throws Exception{Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread worker=new Thread(()->serve(socket));worker.setDaemon(true);worker.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();}
        String base(){return "http://127.0.0.1:"+server.getLocalPort();}
        void serve(Socket socket){try(Socket close=socket){socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=in.readLine(),line;if(first==null)return;while((line=in.readLine())!=null&&!line.isEmpty()){}if(first.contains(" /old ")||first.contains(" /incoming "))requests.incrementAndGet();StringBuilder html=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Performance fixture</title><h1>Article</h1>");for(int i=0;i<2000;i++)html.append("<span>Article text </span>");byte[] body=html.toString().getBytes(StandardCharsets.UTF_8);socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();}catch(Exception ignored){}}
        @Override public void close()throws Exception{server.close();}
    }
}
