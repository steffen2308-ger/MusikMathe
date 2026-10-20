package de.steffen.musikmathe;

import android.content.Context;
import android.view.WindowManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class PrivateBrowserTest {
    @Test public void privateActionsNeverOverwriteNormalTabsOrHistory() {
        Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("browser",0).edit().clear().commit();
        BrowserStore normal=new BrowserStore(context);normal.visit("https://normal.example","Normal");normal.write("tabs",Collections.singletonList(new BrowserStore.Entry("https://normal.example","Normal",0)));normal.selected(3);
        BrowserStore secret=new BrowserStore(context,true);
        secret.visit("https://secret.example","Secret");secret.write("tabs",Collections.singletonList(new BrowserStore.Entry("https://secret.example","Secret",0)));secret.selected(8);secret.engine("bing");secret.toggleBookmark("https://secret.example","Secret");
        assertTrue(secret.read("tabs").isEmpty());assertTrue(secret.read("history").isEmpty());assertEquals("bing",secret.engine());
        BrowserStore restored=new BrowserStore(context);assertEquals("Normal",restored.read("history").get(0).title);assertEquals("https://normal.example",restored.read("tabs").get(0).url);assertEquals(3,restored.selected());assertEquals("google",restored.engine());assertFalse(restored.bookmarked("https://secret.example"));
    }
    @Test public void cleanupDeletesPrivateProfileAndPreservesNormalFiles() throws Exception {
        Context c=RuntimeEnvironment.getApplication();File privateDir=new File(c.getDataDir(),"app_webview_private/Default");assertTrue(privateDir.mkdirs() || privateDir.isDirectory());Files.write(new File(privateDir,"Cookies").toPath(),new byte[]{1});
        File normal=new File(c.getDataDir(),"app_webview/Default");assertTrue(normal.mkdirs() || normal.isDirectory());Files.write(new File(normal,"Cookies").toPath(),new byte[]{2});
        PrivateSession.wipe(c);assertFalse(privateDir.exists());assertArrayEquals(new byte[]{2},Files.readAllBytes(new File(normal,"Cookies").toPath()));
    }
    @Test public void privateActivityUsesBlankSessionAndSecureWindow() throws Exception {
        Context c=RuntimeEnvironment.getApplication();BrowserStore store=new BrowserStore(c);store.write("tabs",Collections.singletonList(new BrowserStore.Entry("https://normal.example","Normal",0)));
        // Robolectric does not start Android's separate process/Application lifecycle here.
        BrowserApplication.privateReady=true;
        ActivityController<PrivateActivity> controller=Robolectric.buildActivity(PrivateActivity.class).setup();
        try {
            PrivateUnitSupport.authenticated(controller.get());
            java.lang.reflect.Field passwords=MainActivity.class.getDeclaredField("passwords");passwords.setAccessible(true);assertNull(passwords.get(controller.get()));
            assertTrue((controller.get().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0);
            new BrowserStore(controller.get(),true).visit("https://own-history.example/","Own private history");
            Method library=MainActivity.class.getDeclaredMethod("showLibrary",String.class,String.class);library.setAccessible(true);library.invoke(controller.get(),"history","Verlauf");
            android.app.AlertDialog history=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue((history.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);history.dismiss();
            Method navigate=MainActivity.class.getDeclaredMethod("navigateTyped",String.class);navigate.setAccessible(true);navigate.invoke(controller.get(),"secret.example");
            controller.pause().stop();assertEquals("https://normal.example",new BrowserStore(c).read("tabs").get(0).url);
        } finally { controller.destroy();BrowserApplication.privateReady=false; }
    }
}
