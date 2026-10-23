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
    private int dp(int value){return Math.round(value*org.robolectric.RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density);}
    private FullscreenToolbar toolbar(Rect safe,Rect camera,int width){
        FullscreenToolbar toolbar=new FullscreenToolbar(org.robolectric.RuntimeEnvironment.getApplication());
        toolbar.cutout(camera==null?null:new DisplayCutout(safe,Collections.singletonList(camera)));
        toolbar.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        toolbar.layout(0,0,width,toolbar.getMeasuredHeight());return toolbar;
    }
    @Test public void cameraSitsBetweenGroupsWithoutWastingAnotherToolbarRow(){
        Rect camera=new Rect(dp(184),0,dp(216),dp(32));FullscreenToolbar bar=toolbar(new Rect(0,dp(32),0,0),camera,dp(400));
        assertEquals(dp(32),bar.getHeight());assertTrue(bar.addressSlot.getRight()<camera.left);assertTrue(bar.navigation.getLeft()>camera.right);
        assertTrue(bar.addressSlot.getWidth()>dp(150));assertTrue(bar.navigation.getWidth()>dp(150));
    }
    @Test public void wideNotchFallsBackBelowAndSideCameraKeepsControlsOutsideCutout(){
        Rect camera=new Rect(dp(80),0,dp(280),dp(36));FullscreenToolbar wide=toolbar(new Rect(0,dp(36),0,0),camera,dp(360));
        assertEquals(dp(64),wide.getHeight());assertEquals(camera.bottom,wide.addressSlot.getTop());assertEquals(camera.bottom,wide.navigation.getTop());
        FullscreenToolbar side=toolbar(new Rect(dp(32),0,0,0),new Rect(0,dp(160),dp(32),dp(200)),dp(720));
        assertEquals(dp(28),side.getHeight());assertTrue(side.addressSlot.getLeft()>dp(32));assertTrue(side.navigation.getRight()<=dp(720));
        assertEquals(dp(28),toolbar(null,null,dp(360)).getHeight());
    }
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
