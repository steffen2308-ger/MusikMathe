package de.steffen.musikmathe;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.webkit.WebViewFeature;
import org.junit.Test;
import org.junit.Assume;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PasswordFeatureTest {
    private Context app(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void main(Runnable run){InstrumentationRegistry.getInstrumentation().runOnMainSync(run);}
    private static WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){WebView w=find(g.getChildAt(i));if(w!=null)return w;}}return null;}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();main(()->web.evaluateJavascript(script,value->{result.set(value);done.countDown();}));assertTrue(done.await(8,TimeUnit.SECONDS));return result.get();}
    private Activity launch()throws Exception{UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");return InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private WebView view(Activity a){AtomicReference<WebView> web=new AtomicReference<>();main(()->web.set(find(a.getWindow().getDecorView())));assertNotNull(web.get());return web.get();}
    private void page(WebView web,String host)throws Exception{
        String stamp=java.util.UUID.randomUUID().toString();
        main(()->web.loadDataWithBaseURL("https://"+host+"/login","<html><title>"+stamp+"</title><meta name='viewport' content='width=device-width, initial-scale=1'><h1>Login fixture</h1><form onsubmit='event.preventDefault()'><input id='username' name='username' autocomplete='username'><input id='password' type='password' autocomplete='current-password'><button type='submit'>Anmelden</button></form></html>","text/html","UTF-8","https://"+host+"/login"));
        long deadline=System.currentTimeMillis()+10000;boolean ready=false;while(System.currentTimeMillis()<deadline){if("true".equals(js(web,"!!window.__mmPasswords && document.title === "+org.json.JSONObject.quote(stamp)))){ready=true;break;}Thread.sleep(150);}AtomicReference<String> nativeUrl=new AtomicReference<>();main(()->nativeUrl.set(web.getUrl()));assertTrue("Password form hooks missing; native URL="+nativeUrl.get()+"; "+js(web,"JSON.stringify({url:location.href,origin:location.origin,title:document.title,hook:typeof window.__mmPasswords,ready:document.readyState})"),ready);
    }
    private void click(String text){UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());UiObject2 item=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(text),java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE))),6000);java.util.List<String> buttons=new java.util.ArrayList<>();if(item==null)for(UiObject2 button:device.findObjects(By.clazz("android.widget.Button")))buttons.add(button.getText());assertNotNull("Expected control: "+text+"; visible buttons: "+buttons,item);item.click();device.waitForIdle();}
    private void option(String action){UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),6000);assertNotNull(menu);menu.click();click("Passwörter");click(action);}
    private void awaitSaved(Activity activity,PasswordVault vault)throws Exception{
        long end=System.currentTimeMillis()+10000;while(vault.all().isEmpty()&&System.currentTimeMillis()<end)Thread.sleep(100);
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("passwords");field.setAccessible(true);PasswordManager manager=(PasswordManager)field.get(activity);
        assertFalse("Saved login missing; error="+manager.lastFailure+"; activity vault rows="+new PasswordVault(activity,false).all().size(),vault.all().isEmpty());
    }
    @Test public void realKeystorePersistsCiphertextAndRejectsOtherOriginsAndPrivateAccess()throws Exception{
        PasswordVault vault=new PasswordVault(app(),false);vault.clear();
        try{
            vault.save("https://login.example.test/login","dummy-user","Dummy-test-password-42");
            File file=new File(app().getNoBackupFilesDir(),PasswordVault.FILE);byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());String stored=new String(bytes,StandardCharsets.ISO_8859_1);
            assertFalse(stored.contains("Dummy-test-password-42"));assertFalse(stored.contains("dummy-user"));assertFalse(stored.contains("login.example.test"));
            PasswordVault reopened=new PasswordVault(app(),false);assertEquals("Dummy-test-password-42",reopened.forSite("https://login.example.test/other").get(0).password);
            assertTrue(reopened.forSite("https://other.example.test").isEmpty());assertTrue(reopened.forSite("http://login.example.test").isEmpty());assertTrue(reopened.forSite("https://login.example.test:8443").isEmpty());
            try{new PasswordVault(app(),true);fail("Private context must reject vault access");}catch(SecurityException expected){}
            reopened.save("https://login.example.test","dummy-user","Updated-dummy-password");assertEquals(1,reopened.all().size());assertEquals("Updated-dummy-password",reopened.all().get(0).password);
            reopened.remove(reopened.all().get(0).id);assertTrue(reopened.all().isEmpty());assertFalse(file.exists());
        }finally{vault.clear();}
    }
    @Test public void nativeMenuSavesFillsAndDoesNotFillAfterCrossOriginNavigation()throws Exception{
        PasswordVault vault=new PasswordVault(app(),false);vault.clear();Activity activity=launch();WebView web=view(activity);
        try{
            page(web,"login.example.test");js(web,"document.getElementById('username').value='dummy-user';document.getElementById('password').value='Dummy-42-password';true");
            option("Anmeldung dieser Seite speichern");click("Speichern");UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle();awaitSaved(activity,vault);assertEquals(1,vault.all().size());
            page(web,"login.example.test");option("Zugangsdaten ausfüllen");click("dummy-user");
            assertEquals("\"dummy-user\"",js(web,"document.getElementById('username').value"));assertEquals("\"Dummy-42-password\"",js(web,"document.getElementById('password').value"));
            option("Zugangsdaten ausfüllen");page(web,"other.example.test");click("dummy-user");
            assertEquals("\"\"",js(web,"document.getElementById('password').value"));
        }finally{main(activity::finish);vault.clear();}
    }
    @Test public void formSubmissionOffersNativeSaveOnSupportedWebViews()throws Exception{
        PasswordVault vault=new PasswordVault(app(),false);vault.clear();Activity activity=launch();WebView web=view(activity);
        try{
            boolean supported=WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER);
            if(Build.VERSION.SDK_INT>=35)assertTrue("Modern WebView must support scoped messages",supported);
            Assume.assumeTrue("Older WebView uses explicit save menu",supported);
            app().getSharedPreferences("password-options",0).edit().putBoolean("ask",true).commit();
            page(web,"automatic.example.test");js(web,"window.musikPasswords.postMessage(JSON.stringify({action:'save',token:'wrong-token',username:'forged',password:'forged'}));true");assertFalse(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).wait(Until.hasObject(By.text("Passwort merken?")),500));js(web,"document.getElementById('username').value='automatic-user';document.getElementById('password').value='Automatic-dummy-password';document.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");
            click("Speichern");UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle();awaitSaved(activity,vault);assertEquals("Automatic-dummy-password",vault.forSite("https://automatic.example.test").get(0).password);
        }finally{main(activity::finish);vault.clear();}
    }
}
