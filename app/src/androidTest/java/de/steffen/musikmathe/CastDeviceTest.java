package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real native controls and the no-Play-services fallback; no receiver or Netflix playback claim. */
@RunWith(AndroidJUnit4.class)
public class CastDeviceTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(instrumentation);
    private void main(Runnable action){instrumentation.runOnMainSync(action);}
    private Object invoke(MainActivity activity,String name,Class<?>[] types,Object...args){try{Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    private UiObject2 text(String label){UiObject2 item=device.wait(Until.findObject(By.text(label)),8000);assertNotNull(label,item);return item;}
    private MainActivity launch()throws Exception{
        device.wakeUp();device.executeShellCommand("wm dismiss-keyguard");instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();
        return (MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void preview(){
        Bitmap full=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(full);Bitmap small=Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,85,bytes);
        String encoded=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);int count=(encoded.length()+1799)/1800;
        for(int i=0;i<count;i++)Log.i("MenuPreview","cast "+i+"/"+count+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    @Test public void menuAndVideoActionsHandleMissingPlayServicesWithoutBreakingBrowser()throws Exception{
        assumeTrue("This device fixture deliberately has no Google Play services",GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(instrumentation.getTargetContext())!=ConnectionResult.SUCCESS);
        MainActivity activity=launch();
        try{
            Field field=MainActivity.class.getDeclaredField("browserCast");field.setAccessible(true);main(()->{try{assertNull("Cast is not initialized at browser startup",field.get(activity));}catch(IllegalAccessException e){throw new RuntimeException(e);}});
            UiObject2 menu=device.wait(Until.findObject(By.desc("Menü")),8000);assertNotNull(menu);menu.click();
            assertTrue(new UiScrollable(new UiSelector().className("android.widget.ListView")).scrollIntoView(new UiSelector().text("Auf Fernseher übertragen")));text("Auf Fernseher übertragen").click();
            text("Auf Fernseher übertragen");assertTrue(device.wait(Until.hasObject(By.textContains("Google Play-Dienste")),8000));
            assertFalse(text("Gerät auswählen").isEnabled());assertFalse(text("Video auswählen").isEnabled());assertFalse(text("Wiedergabe fortsetzen").isEnabled());assertFalse(text("Verbindung trennen").isEnabled());preview();
            text("Schließen").click();assertTrue(device.wait(Until.gone(By.text("Auf Fernseher übertragen")),8000));
            main(()->invoke(activity,"videoOptions",new Class<?>[]{VideoCatalog.Item.class},new VideoCatalog.Item("https://cdn.example/movie.mp4","MP4",Map.of())));
            text("Auf Fernseher übertragen").click();text("Auf Fernseher übertragen");assertFalse(text("Gerät auswählen").isEnabled());text("Schließen").click();
            main(()->{WebView web=(WebView)invoke(activity,"activeView",new Class<?>[0]);assertNotNull(web);assertTrue(web.getSettings().getJavaScriptEnabled());});
            assertNotNull(device.wait(Until.findObject(By.desc("Menü")),8000));
        }finally{main(activity::finish);}
    }
}
