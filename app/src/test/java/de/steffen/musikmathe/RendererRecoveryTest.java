package de.steffen.musikmathe;

import android.app.AlertDialog;
import android.os.Looper;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class RendererRecoveryTest {
    private Object call(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(activity,args);}
    private WebView web(MainActivity activity)throws Exception{return (WebView)call(activity,"activeView",new Class<?>[0]);}
    private RenderProcessGoneDetail ended(){return new RenderProcessGoneDetail(){@Override public boolean didCrash(){return true;}@Override public int rendererPriorityAtExit(){return 0;}};}
    @Test public void crashedPageWaitsForUserAndAddressBarCanRecoverWithoutReloadingTheBrokenUrl()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("browser",0).edit().clear().commit();ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();MainActivity activity=controller.get();
        try{
            call(activity,"navigate",new Class<?>[]{String.class},"https://vimeo.com/showcase/2600007");WebView original=web(activity);android.webkit.WebViewClient client=original.getWebViewClient();assertTrue(client.onRenderProcessGone(original,ended()));Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull("Crashes must never create a reload loop",web(activity));
            call(activity,"navigate",new Class<?>[]{String.class},"https://safe.example/");WebView recovered=web(activity);assertNotNull(recovered);assertNotSame(original,recovered);assertEquals("https://safe.example/",Shadows.shadowOf(recovered).getLastLoadedUrl());
            assertTrue(client.onRenderProcessGone(original,ended()));assertSame("A stale renderer callback cannot destroy the replacement",recovered,web(activity));
        }finally{controller.pause().stop().destroy();}
    }
    @Test public void responsiveOrBackgroundPagesCannotLeaveAStaleNativeHangDialog()throws Exception{
        ActivityController<android.app.Activity> controller=Robolectric.buildActivity(android.app.Activity.class).setup();WebView a=new WebView(controller.get()),b=new WebView(controller.get());AtomicReference<WebView> visible=new AtomicReference<>(a);RendererRecovery recovery=new RendererRecovery(controller.get(),w->visible.get()==w);
        try{
            recovery.unresponsive(b,null);assertNull(ShadowAlertDialog.getLatestAlertDialog());recovery.unresponsive(a,null);AlertDialog first=ShadowAlertDialog.getLatestAlertDialog();assertTrue(first.isShowing());assertFalse("Unavailable renderer termination must not be offered",first.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            recovery.responsive(b);assertTrue(first.isShowing());recovery.responsive(a);assertFalse(first.isShowing());
            recovery.unresponsive(a,null);AlertDialog second=ShadowAlertDialog.getLatestAlertDialog();assertTrue(second.isShowing());recovery.clear();assertFalse(second.isShowing());visible.set(b);recovery.unresponsive(a,null);assertSame(second,ShadowAlertDialog.getLatestAlertDialog());
        }finally{recovery.clear();a.destroy();b.destroy();controller.pause().stop().destroy();}
    }
}
