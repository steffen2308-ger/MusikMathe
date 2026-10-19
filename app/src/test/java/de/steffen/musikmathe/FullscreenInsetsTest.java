package de.steffen.musikmathe;

import android.graphics.Insets;
import android.graphics.Rect;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=30)
public class FullscreenInsetsTest {
    private Object field(MainActivity activity,String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
    private void fullscreen(MainActivity activity,boolean value)throws Exception{Method method=MainActivity.class.getDeclaredMethod("setBrowserFullscreen",boolean.class);method.setAccessible(true);method.invoke(activity,value);}
    @Test public void removesOnlyCameraAndSystemBarPaddingAndRestoresItAfterVideo()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        try{
            MainActivity activity=controller.get();View root=(View)field(activity,"root");
            DisplayCutout cutout=new DisplayCutout(new Rect(0,90,0,0),Collections.singletonList(new Rect(100,0,200,90)));
            WindowInsets safe=new WindowInsets.Builder().setDisplayCutout(cutout).setInsets(WindowInsets.Type.statusBars(),Insets.of(0,24,0,0)).setInsets(WindowInsets.Type.navigationBars(),Insets.of(0,0,0,48)).build();
            root.dispatchApplyWindowInsets(safe);assertEquals(90,root.getPaddingTop());assertEquals(48,root.getPaddingBottom());
            fullscreen(activity,true);root.dispatchApplyWindowInsets(safe);
            assertEquals(0,root.getPaddingTop());assertEquals(0,root.getPaddingBottom());assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
            WindowInsets typing=new WindowInsets.Builder(safe).setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,300)).setVisible(WindowInsets.Type.ime(),true).build();
            root.dispatchApplyWindowInsets(typing);assertEquals(0,root.getPaddingTop());assertEquals(300,root.getPaddingBottom());
            Method active=MainActivity.class.getDeclaredMethod("activeView");active.setAccessible(true);WebView web=(WebView)active.invoke(activity);
            web.getWebChromeClient().onShowCustomView(new FrameLayout(activity),()->{});fullscreen(activity,false);root.dispatchApplyWindowInsets(safe);
            assertEquals(0,root.getPaddingTop());assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
            web.getWebChromeClient().onHideCustomView();root.dispatchApplyWindowInsets(safe);
            assertEquals(90,root.getPaddingTop());assertEquals(48,root.getPaddingBottom());assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
        }finally{controller.pause().stop().destroy();}
    }
    @Test @Config(sdk=28) public void androidNineAllowsShortEdgeCutoutsOnlyDuringFullscreen()throws Exception{
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        try{
            MainActivity activity=controller.get();fullscreen(activity,true);
            assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
            fullscreen(activity,false);assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
        }finally{controller.pause().stop().destroy();}
    }
}
