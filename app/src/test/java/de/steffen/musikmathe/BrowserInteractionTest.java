package de.steffen.musikmathe;

import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class BrowserInteractionTest {
    private void clear(){RuntimeEnvironment.getApplication().getSharedPreferences("browser",0).edit().clear().commit();}
    private Object invoke(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    @SuppressWarnings("unchecked") private List<MainActivity.Tab> tabs(MainActivity a)throws Exception{Field f=MainActivity.class.getDeclaredField("tabs");f.setAccessible(true);return (List<MainActivity.Tab>)f.get(a);}
    private WebView web(MainActivity a)throws Exception{return (WebView)invoke(a,"activeView",new Class<?>[0]);}
    @Test public void typedNavigationAndTabsSurviveRelaunch()throws Exception{
        clear();ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=controller.get();
        invoke(a,"navigateTyped",new Class<?>[]{String.class},"example.org");assertEquals("https://example.org",Shadows.shadowOf(web(a)).getLastLoadedUrl());
        invoke(a,"newTab",new Class<?>[]{String.class,boolean.class},"https://wikipedia.org",true);assertEquals(2,tabs(a).size());
        invoke(a,"navigateTyped",new Class<?>[]{String.class},"javascript:alert(1)");assertEquals("https://wikipedia.org",Shadows.shadowOf(web(a)).getLastLoadedUrl());
        controller.pause().stop().destroy();ActivityController<MainActivity> next=Robolectric.buildActivity(MainActivity.class).setup();
        assertEquals(2,tabs(next.get()).size());assertEquals("https://wikipedia.org",Shadows.shadowOf(web(next.get())).getLastLoadedUrl());
        invoke(next.get(),"closeTab",new Class<?>[]{int.class},1);assertEquals(1,tabs(next.get()).size());
        invoke(next.get(),"closeTab",new Class<?>[]{int.class},0);assertEquals(Address.HOME,tabs(next.get()).get(0).url);next.pause().stop().destroy();
    }
    @Test public void untrustedLocalAccessIsDisabledAndExternalUrlsOpen()throws Exception{
        clear();Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com"));
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,intent).setup();
        assertEquals("https://example.com",Shadows.shadowOf(web(c.get())).getLastLoadedUrl());WebSettings s=web(c.get()).getSettings();
        assertFalse(s.getAllowFileAccess());assertFalse(s.getAllowContentAccess());assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,s.getMixedContentMode());
        assertTrue(s.getJavaScriptEnabled());assertTrue(s.getDomStorageEnabled());c.pause().stop().destroy();
    }
    @Test public void scriptTriggeredExternalAppsAreBlocked()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        MainActivity.Tab tab=tabs(a).get(0);
        assertEquals(Boolean.TRUE,invoke(a,"routeLink",new Class<?>[]{MainActivity.Tab.class,String.class,boolean.class},tab,"tel:1234",false));
        assertNull(Shadows.shadowOf(a).getNextStartedActivity());
        assertEquals(Boolean.FALSE,invoke(a,"routeLink",new Class<?>[]{MainActivity.Tab.class,String.class,boolean.class},tab,"https://example.com",false));c.pause().stop().destroy();
    }
}
