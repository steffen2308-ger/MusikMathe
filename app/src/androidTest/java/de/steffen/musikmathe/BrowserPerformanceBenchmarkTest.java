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
            if("current".equals(label))main(()->{
                WebView first=tabs().get(0).view,second=tabs().get(1).view;
                activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
                assertSame("Backgrounding preserves recently used tabs",first,tabs().get(0).view);assertSame(second,tabs().get(1).view);
            });
            main(activity::finish);instrumentation.waitForIdleSync();activity=null;
            benchmarkNormalHistory(report);
            benchmarkPrivateStorage(report);
            benchmarkPrivateArchive(report);
            Log.i("MenuPreview","browser-performance "+report);
            try(java.io.FileOutputStream out=app.openFileOutput("browser-performance-"+label+".json",Context.MODE_PRIVATE)){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}
        }finally{if(activity!=null)main(activity::finish);}
    }
    private void benchmarkNormalHistory(JSONObject report)throws Exception{
        BrowserStore store=new BrowserStore(app);List<BrowserStore.Entry> history=new ArrayList<>();
        for(int i=0;i<500;i++)history.add(new BrowserStore.Entry("https://normal-history.example/"+i,"Normal article "+i+" · stored title and metadata",i));
        store.write("history",history);List<Double> visits=new ArrayList<>();
        for(int i=0;i<30;i++){
            String url="https://normal-history.example/"+i;double[] elapsed={0};
            main(()->{long start=SystemClock.elapsedRealtimeNanos();store.visit(url,"Updated normal article");elapsed[0]=(SystemClock.elapsedRealtimeNanos()-start)/1000000.0;});visits.add(elapsed[0]);
        }
        assertEquals("https://normal-history.example/29",new BrowserStore(app).read("history").get(0).url);
        report.put("normalHistorySaveUiMedianMs",median(visits)).put("normalStorageRounds",30);
    }
    private void benchmarkPrivateStorage(JSONObject report)throws Exception{
        assertNull(PrivateProfiles.active);byte[] material=new byte[96];new java.security.SecureRandom().nextBytes(material);
        PrivateProfiles.Session session=new PrivateProfiles.Session(app,PrivateCrypto.id(material),material,new JSONObject().put("schema",1),true);
        PrivateProfiles.active=session;
        try{
            BrowserStore store=new BrowserStore(app,true);List<BrowserStore.Entry> rows=new ArrayList<>(),history=new ArrayList<>();
            for(int i=0;i<20;i++)rows.add(new BrowserStore.Entry("https://private-"+i+".example/page","Private tab "+i,0));
            for(int i=0;i<500;i++)history.add(new BrowserStore.Entry("https://private-history.example/"+i,"Private article "+i+" · stored title and metadata",i));
            store.write("history",history);assertTrue(session.flush());List<Double> selection=new ArrayList<>(),visits=new ArrayList<>();
            for(int i=0;i<30;i++){
                int selected=i%20;String url="https://private-history.example/"+i;double[] elapsed=new double[2];
                main(()->{long start=SystemClock.elapsedRealtimeNanos();store.saveTabs(rows,selected);elapsed[0]=(SystemClock.elapsedRealtimeNanos()-start)/1000000.0;start=SystemClock.elapsedRealtimeNanos();store.visit(url,"Updated private article");elapsed[1]=(SystemClock.elapsedRealtimeNanos()-start)/1000000.0;});selection.add(elapsed[0]);visits.add(elapsed[1]);
            }
            long start=SystemClock.elapsedRealtimeNanos();assertTrue(session.flush());double barrier=(SystemClock.elapsedRealtimeNanos()-start)/1000000.0;
            JSONObject durable=new JSONObject(new String(PrivateCrypto.decrypt(PrivateCrypto.dataKey(material),PrivateCrypto.aad("data",session.id),PrivateProfiles.dataFile(app,session.id).readFully()),StandardCharsets.UTF_8));
            assertEquals(9,durable.getInt("selected"));assertEquals("https://private-history.example/29",new org.json.JSONArray(durable.getString("history")).getJSONObject(0).getString("url"));
            report.put("privateTabSaveUiMedianMs",median(selection)).put("privateHistorySaveUiMedianMs",median(visits)).put("privatePersistenceBarrierMs",barrier).put("privateStorageRounds",30);
        }finally{session.close();PrivateProfiles.active=null;PrivateProfiles.dataFile(app,session.id).delete();}
    }
    private void benchmarkPrivateArchive(JSONObject report)throws Exception{
        java.io.File root=java.nio.file.Files.createTempDirectory(app.getCacheDir().toPath(),"private-open-benchmark").toFile();
        byte[] salt=new byte[32],material=null;java.util.Arrays.fill(salt,(byte)7);char[] password="Benchmark-only-password-42".toCharArray();
        try{
            List<Double> passwords=new ArrayList<>(),saves=new ArrayList<>(),restores=new ArrayList<>();
            for(int round=0;round<3;round++){long start=SystemClock.elapsedRealtimeNanos();byte[] next=PrivateCrypto.derive(password,salt);passwords.add((SystemClock.elapsedRealtimeNanos()-start)/1000000.0);if(material==null)material=next;else{assertArrayEquals(material,next);java.util.Arrays.fill(next,(byte)0);}}
            String id=PrivateCrypto.id(material);java.io.File source=new java.io.File(root,"source"),cookies=new java.io.File(source,"Default/Cookies");assertTrue(cookies.getParentFile().mkdirs());byte[] data=new byte[512*1024];new java.util.Random(42).nextBytes(data);java.nio.file.Files.write(cookies.toPath(),data);
            byte[] row=java.util.Arrays.copyOf("fixture website storage row;".repeat(40).getBytes(StandardCharsets.UTF_8),1024);java.io.File storage=new java.io.File(source,"Default/Local Storage");assertTrue(storage.mkdirs());for(int i=0;i<64;i++)java.nio.file.Files.write(new java.io.File(storage,"entry-"+i).toPath(),row);
            android.util.AtomicFile archive=new android.util.AtomicFile(new java.io.File(root,"profile.web"));long bytes=0;
            for(int round=0;round<4;round++){
                long start=SystemClock.elapsedRealtimeNanos();PrivateArchive.save(source,archive,material,id);double save=(SystemClock.elapsedRealtimeNanos()-start)/1000000.0;
                java.io.File restored=new java.io.File(root,"restored-"+round);start=SystemClock.elapsedRealtimeNanos();PrivateArchive.restore(archive,new java.io.File(root,"staging-"+round),restored,material,id);double restore=(SystemClock.elapsedRealtimeNanos()-start)/1000000.0;
                assertArrayEquals(data,java.nio.file.Files.readAllBytes(new java.io.File(restored,"Default/Cookies").toPath()));for(int i=0;i<64;i++)assertArrayEquals(row,java.nio.file.Files.readAllBytes(new java.io.File(restored,"Default/Local Storage/entry-"+i).toPath()));
                PrivateSession.remove(restored);bytes=archive.getBaseFile().length();if(round>0){saves.add(save);restores.add(restore);}
            }
            class Sink extends java.io.OutputStream {int singles,blocks;public void write(int b){singles++;}public void write(byte[] b,int offset,int length){blocks++;}}
            Sink sink=new Sink();new java.io.FilterOutputStream(sink){@Override public void close()throws java.io.IOException{flush();}}.write(new byte[65536]);
            report.put("privatePasswordDeriveMedianMs",median(passwords)).put("privateArchiveSaveMedianMs",median(saves)).put("privateArchiveRestoreMedianMs",median(restores)).put("privateArchiveBytes",bytes).put("privateArchiveInputBytes",data.length+64*row.length).put("privateArchiveFiles",65).put("legacyFilterSingleWritesPer64KiB",sink.singles);
        }finally{java.util.Arrays.fill(password,'\0');if(material!=null)java.util.Arrays.fill(material,(byte)0);PrivateSession.remove(root);}
    }
    private static final class Fixture implements AutoCloseable{
        final ServerSocket server=new ServerSocket(0);final AtomicInteger requests=new AtomicInteger();
        Fixture()throws Exception{Thread accept=new Thread(()->{while(!server.isClosed())try{Socket socket=server.accept();Thread worker=new Thread(()->serve(socket));worker.setDaemon(true);worker.start();}catch(Exception ignored){}});accept.setDaemon(true);accept.start();}
        String base(){return "http://127.0.0.1:"+server.getLocalPort();}
        void serve(Socket socket){try(Socket close=socket){socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=in.readLine(),line;if(first==null)return;while((line=in.readLine())!=null&&!line.isEmpty()){}if(first.contains(" /old ")||first.contains(" /incoming "))requests.incrementAndGet();StringBuilder html=new StringBuilder("<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><title>Performance fixture</title><h1>Article</h1>");for(int i=0;i<2000;i++)html.append("<span>Article text </span>");byte[] body=html.toString().getBytes(StandardCharsets.UTF_8);socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();}catch(Exception ignored){}}
        @Override public void close()throws Exception{server.close();}
    }
}
