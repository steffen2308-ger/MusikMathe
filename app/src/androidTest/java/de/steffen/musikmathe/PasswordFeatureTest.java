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
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>();main(()->web.evaluateJavascript(script,value->{result.set(value);done.countDown();}));if(!done.await(8,TimeUnit.SECONDS)){
            AtomicReference<String> state=new AtomicReference<>();main(()->state.set("attached="+web.isAttachedToWindow()+", visible="+web.getVisibility()+", url="+web.getUrl()+", activityDestroyed="+(web.getContext() instanceof Activity&&((Activity)web.getContext()).isDestroyed())));
            fail("WebView script callback timed out: "+state.get());
        }return result.get();}
    private Activity launch()throws Exception{UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");return InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(app(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private WebView view(Activity a){AtomicReference<WebView> web=new AtomicReference<>();main(()->web.set(find(a.getWindow().getDecorView())));assertNotNull(web.get());return web.get();}
    private void page(WebView web,String host)throws Exception{
        String stamp=java.util.UUID.randomUUID().toString(),url="https://"+host+"/login?fixture="+stamp;
        String html="<html><title>"+stamp+"</title><meta name='viewport' content='width=device-width, initial-scale=1'><h1>Login fixture</h1><form onsubmit='event.preventDefault()'><input id='username' name='username' autocomplete='username'><input id='password' type='password' autocomplete='current-password'><button type='submit'>Anmelden</button></form></html>";
        main(()->{
            android.webkit.WebViewClient original=web.getWebViewClient();
            web.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view,android.webkit.WebResourceRequest request){
                    if(request.isForMainFrame()&&url.equals(request.getUrl().toString()))return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                    return original.shouldInterceptRequest(view,request);
                }
                @Override public void onPageStarted(WebView view,String target,android.graphics.Bitmap icon){original.onPageStarted(view,target,icon);}
                @Override public void onPageFinished(WebView view,String target){original.onPageFinished(view,target);}
                @Override public void doUpdateVisitedHistory(WebView view,String target,boolean reload){original.doUpdateVisitedHistory(view,target,reload);}
                @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){return original.shouldOverrideUrlLoading(view,request);}
            });
            web.loadUrl(url);
        });
        long deadline=System.currentTimeMillis()+10000;boolean ready=false;while(System.currentTimeMillis()<deadline){if("true".equals(js(web,"!!window.__mmPasswords && document.title === "+org.json.JSONObject.quote(stamp)))){ready=true;break;}Thread.sleep(150);}
        AtomicReference<String> nativeUrl=new AtomicReference<>();main(()->nativeUrl.set(web.getUrl()));assertTrue("Password form hooks missing; native URL="+nativeUrl.get()+"; "+js(web,"JSON.stringify({url:location.href,origin:location.origin,title:document.title,hook:typeof window.__mmPasswords,ready:document.readyState})"),ready);
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
    @Test public void realTouchStillOffersAutofillAfterFullscreenGesturesAreAttached()throws Exception{
        PasswordVault vault=new PasswordVault(app(),false);vault.clear();Activity activity=launch();WebView web=view(activity);
        try{
            Assume.assumeTrue("Older WebView uses explicit fill menu",WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER));
            vault.save("https://focus.example.test","focus-user","Dummy-focus-password");page(web,"focus.example.test");
            js(web,"document.getElementById('username').focus();true");
            UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            assertFalse("Script focus alone must not open autofill",device.wait(Until.hasObject(By.text("focus-user")),500));
            js(web,"document.getElementById('username').blur();true");
            org.json.JSONArray position=new org.json.JSONArray(new org.json.JSONTokener(js(web,"JSON.stringify((function(){var r=document.getElementById('username').getBoundingClientRect();return [(r.left+r.width/2)/innerWidth,(r.top+r.height/2)/innerHeight];})())")).nextValue().toString());
            int[] point=new int[2];main(()->{int[] location=new int[2];web.getLocationOnScreen(location);point[0]=location[0]+(int)(web.getWidth()*position.optDouble(0));point[1]=location[1]+(int)(web.getHeight()*position.optDouble(1));});
            device.click(point[0],point[1]);click("focus-user");
            long deadline=android.os.SystemClock.uptimeMillis()+10000;String filled;
            do{filled=js(web,"document.getElementById('password').value");if("\"Dummy-focus-password\"".equals(filled))break;Thread.sleep(50);}while(android.os.SystemClock.uptimeMillis()<deadline);
            assertEquals("\"Dummy-focus-password\"",filled);
            assertTrue("Normal browser controls stay visible",device.hasObject(By.desc("Menü")));
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
