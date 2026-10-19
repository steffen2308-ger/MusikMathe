package de.steffen.musikmathe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AddressSuggestionsDeviceTest {
    private final Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private void main(Runnable run){InstrumentationRegistry.getInstrumentation().runOnMainSync(run);}
    private Activity launch()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");app.getSharedPreferences("browser",0).edit().clear().commit();
        return InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private UiObject2 field(){UiObject2 field=device.wait(Until.findObject(By.desc("Adresse oder Suchbegriff")),8000);assertNotNull("Address field",field);return field;}
    private void type(String input){UiObject2 field=field();field.click();field.setText(input);}
    private void click(String value){UiObject2 item=device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(value),Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE))),8000);assertNotNull("Expected control: "+value,item);item.click();}
    private static WebView web(View view){if(view instanceof WebView)return (WebView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){WebView result=web(group.getChildAt(i));if(result!=null)return result;}}return null;}
    private String url(Activity activity){AtomicReference<String> result=new AtomicReference<>();main(()->result.set(web(activity.getWindow().getDecorView()).getUrl()));return result.get();}
    private void await(String reason,BooleanSupplier condition)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);while(System.nanoTime()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}fail(reason);}
    private void invoke(Activity activity,String name){main(()->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}catch(Exception error){throw new RuntimeException(error);}});}
    @Test public void filtersWithoutLoadingAndOpensExactUrlByTouchOrKeyboard()throws Exception{
        Activity activity=launch();BrowserStore store=new BrowserStore(app);List<String> requests=Collections.synchronizedList(new ArrayList<>());
        try(ServerSocket server=new ServerSocket(0)){
            Thread responses=new Thread(()->{while(!server.isClosed())try(Socket socket=server.accept()){
                socket.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));String first=in.readLine(),line;if(first==null)continue;while((line=in.readLine())!=null&&!line.isEmpty()){}
                requests.add(first.split(" ")[1]);byte[] html="<html><meta charset='utf-8'><title>Meine Matheübungen</title><h1>Selected history page</h1></html>".getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+html.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(html);
            }catch(Exception ignored){}});responses.setDaemon(true);responses.start();
            String base="http://127.0.0.1:"+server.getLocalPort(),target=base+"/Case/Path?x=1&y=2#part";
            store.visit(base+"/old","Ältere Matheübungen");store.visit(target,"Meine Matheübungen");store.visit(base+"/other","Andere Seite");
            type("MaThE");assertTrue("History title suggestion",device.wait(Until.hasObject(By.text("Meine Matheübungen")),6000));assertEquals("MaThE",field().getText());
            type("kein-treffer-xyz");assertTrue("Old matches disappear",device.wait(Until.gone(By.text("Meine Matheübungen")),6000));
            type("mathe üb");assertTrue("Multiple search terms",device.wait(Until.hasObject(By.text("Meine Matheübungen")),6000));assertTrue("Typing must not make HTTP requests",requests.isEmpty());assertEquals(Address.HOME,url(activity));
            click("Meine Matheübungen");await("Tapped suggestion must preserve query, path case and fragment",()->target.equals(url(activity))&&requests.contains("/Case/Path?x=1&y=2"));
            assertTrue("Page really loaded",device.wait(Until.hasObject(By.text("Selected history page")),6000));
            UiObject2 home=device.wait(Until.findObject(By.desc("Startseite")),6000);assertNotNull(home);home.click();await("Home before keyboard navigation",()->Address.HOME.equals(url(activity)));
            type("Case/Path");assertTrue("URL substring suggestion",device.wait(Until.hasObject(By.text(target)),6000));device.pressDPadDown();device.pressEnter();await("Keyboard-selected history entry; current="+url(activity),()->target.equals(url(activity)));
            String typed=base+"/Case";type(typed);assertTrue("Prefix suggestion is visible",device.wait(Until.hasObject(By.text(target)),6000));device.pressEnter();await("Enter without explicit selection must use typed URL",()->typed.equals(url(activity))&&requests.contains("/Case"));
            await("Typed page finished recording its visit",()->store.read("history").stream().anyMatch(row->row.url.equals(typed)));
            invoke(activity,"clearData");click("Löschen");await("History must be deleted",()->store.read("history").isEmpty());type("mathe");assertTrue("Deleted history must not reappear",device.wait(Until.gone(By.text("Meine Matheübungen")),6000));
            type("");assertFalse("Empty input has no history dropdown",device.hasObject(By.text("Ältere Matheübungen")));
        }finally{main(activity::finish);}
    }
    @Test public void privateAddressFieldNeverSuggestsNormalHistory()throws Exception{
        Activity activity=launch();BrowserStore store=new BrowserStore(app);store.visit("https://normal-history.example/","Normaler Verlauf Schutztest");
        try{
            type("schutztest");assertTrue("Normal history available",device.wait(Until.hasObject(By.text("Normaler Verlauf Schutztest")),6000));
            app.startActivity(new Intent(app,PrivateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
            assertTrue("Private process visible",device.wait(Until.hasObject(By.descStartsWith("Privater Tab")),12000));
            type("schutztest");assertTrue("Private tab must not expose normal title",device.wait(Until.gone(By.text("Normaler Verlauf Schutztest")),6000));
            assertFalse("Private tab must not expose normal URL",device.hasObject(By.text("https://normal-history.example/")));
            UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),6000);assertNotNull(menu);menu.click();new UiScrollable(new UiSelector().classNameMatches(".*ListView")).scrollIntoView(new UiSelector().text("Alle privaten Tabs schließen"));click("Alle privaten Tabs schließen");
            assertTrue("Back in normal process",device.wait(Until.gone(By.descStartsWith("Privater Tab")),10000));assertEquals(1,store.read("history").size());
            type("schutztest");assertTrue("Normal history preserved",device.wait(Until.hasObject(By.text("Normaler Verlauf Schutztest")),6000));
        }finally{main(activity::finish);}
    }
}
