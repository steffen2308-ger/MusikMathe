package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.DisplayCutout;
import android.view.WindowManager;
import androidx.test.uiautomator.UiDevice;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real MotionEvents through the Activity and WebView, including page controls and immersive UI. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=26)
public class BrowserFullscreenTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private MainActivity currentActivity;
    private String stage="initial";
    private int doubleTaps;
    private Object field(MainActivity activity,String name)throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    private Object invoke(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);
    }
    private String js(WebView web,String expression)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] answer={null};
        instrumentation.runOnMainSync(()->web.evaluateJavascript(expression,value->{answer[0]=value;done.countDown();}));
        assertTrue("WebView callback",done.await(10,TimeUnit.SECONDS));return new JSONArray("["+answer[0]+"]").optString(0);
    }
    private void expect(WebView web,String expression,String expected)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;String actual;
        do{actual=js(web,expression);if(expected.equals(actual))return;Thread.sleep(50);}while(SystemClock.uptimeMillis()<deadline);
        assertEquals(expression,expected,actual);
    }
    private MainActivity start() {
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();
        currentActivity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        return currentActivity;
    }
    private WebView load(MainActivity activity)throws Exception{
        WebView[] holder={null};instrumentation.runOnMainSync(()->{
            try{
                holder[0]=(WebView)invoke(activity,"activeView",new Class<?>[0]);
                String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;min-height:300vh}#blank{height:210px;background:#eef}button,input,a,p{display:block;height:44px;margin:4px;width:200px}#text{font-size:16px}</style></head><body><div id='blank'></div><button id='button' onclick='window.clicks++'>Button</button><input id='input' inputmode='none'><a id='link' href='#destination'>Link</a><p id='text'>Selectable text</p><div id='destination'></div><script>window.clicks=0;window.fixtureReady=true;</script></body></html>";
                WebViewClient original=holder[0].getWebViewClient();
                holder[0].setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                        if(request.isForMainFrame()&&request.getUrl().toString().startsWith("https://fullscreen.example/"))return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                        return original.shouldInterceptRequest(view,request);
                    }
                    @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){original.onPageStarted(view,url,icon);}
                    @Override public void onPageFinished(WebView view,String url){original.onPageFinished(view,url);}
                    @Override public void doUpdateVisitedHistory(WebView view,String url,boolean reload){original.doUpdateVisitedHistory(view,url,reload);}
                    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return original.shouldOverrideUrlLoading(view,request);}
                });
                holder[0].loadUrl("https://fullscreen.example/");
            }catch(Exception error){throw new RuntimeException(error);}
        });
        expect(holder[0],"String(window.fixtureReady)","true");Thread.sleep(250);return holder[0];
    }
    private float[] point(WebView web,String id)throws Exception{
        JSONArray fraction=new JSONArray(js(web,"JSON.stringify((function(){var e=document.getElementById('"+id+"'),r=e.getBoundingClientRect();if(e.id==='text'){var range=document.createRange();range.selectNodeContents(e);r=range.getBoundingClientRect();}var v=window.visualViewport||{width:innerWidth,height:innerHeight,offsetLeft:0,offsetTop:0};return [(r.left+Math.min(r.width/2,30)-v.offsetLeft)/v.width,(r.top+r.height/2-v.offsetTop)/v.height];})())"));
        float[] point={0,0};instrumentation.runOnMainSync(()->{int[] location=new int[2];web.getLocationOnScreen(location);point[0]=location[0]+web.getWidth()*(float)fraction.optDouble(0);point[1]=location[1]+web.getHeight()*(float)fraction.optDouble(1);});return point;
    }
    private void touch(long down,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        // UiAutomation waits for system-bar animations between injected events on modern Android,
        // stretching a double tap beyond Android's timeout. Dispatch into the app's real view tree
        // so timestamps and cadence match a finger gesture while still exercising WebView and WM.
        try{instrumentation.runOnMainSync(()->{
            int[] origin=new int[2];currentActivity.getWindow().getDecorView().getLocationOnScreen(origin);
            event.offsetLocation(-origin[0],-origin[1]);
            assertTrue("Touch dispatch at "+x+","+y,currentActivity.dispatchTouchEvent(event));
        });}finally{event.recycle();}
    }
    private void doubleTap(WebView web,String id)throws Exception{
        stage="double tap #"+(++doubleTaps)+" on "+id;
        Thread.sleep(400);float[] p=point(web,id);
        for(int i=0;i<2;i++){long down=SystemClock.uptimeMillis();touch(down,MotionEvent.ACTION_DOWN,p[0],p[1]);Thread.sleep(25);touch(down,MotionEvent.ACTION_UP,p[0],p[1]);if(i==0)Thread.sleep(100);}
    }
    private boolean fullscreen(MainActivity activity)throws Exception{
        boolean[] value={false};instrumentation.runOnMainSync(()->{try{value[0]=(boolean)field(activity,"browserFullscreen");}catch(Exception e){throw new RuntimeException(e);}});return value[0];
    }
    private void expectFullscreen(MainActivity activity,boolean expected)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;
        while(fullscreen(activity)!=expected&&SystemClock.uptimeMillis()<deadline)Thread.sleep(50);
        String[] details={""};instrumentation.runOnMainSync(()->{try{details[0]=FreeSpaceGesture.diagnostics((WebView)invoke(activity,"activeView",new Class<?>[0]))+"; custom="+(field(activity,"customView")!=null)+", destroyed="+field(activity,"destroyed");}catch(Exception error){throw new RuntimeException(error);}});
        assertEquals("Browser fullscreen after "+stage+"; "+details[0],expected,fullscreen(activity));
        instrumentation.runOnMainSync(()->{try{
            assertEquals(expected?View.GONE:View.VISIBLE,((View)field(activity,"top")).getVisibility());
            assertEquals(expected?View.GONE:View.VISIBLE,((View)field(activity,"bottom")).getVisibility());
            assertEquals(expected?View.VISIBLE:View.GONE,((View)field(activity,"fullscreenToolbar")).getVisibility());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @Test public void blankDoubleTapTogglesAndBackRestoresBrowserAfterVideo()throws Exception{
        MainActivity activity=start();
        try{
            WebView web=load(activity);doubleTap(web,"blank");expectFullscreen(activity,true);Thread.sleep(350);
            instrumentation.runOnMainSync(()->{
                if(Build.VERSION.SDK_INT>=30){
                    assertFalse(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
                    assertFalse(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
                }else{
                    int flags=activity.getWindow().getDecorView().getSystemUiVisibility();
                    assertTrue((flags&View.SYSTEM_UI_FLAG_FULLSCREEN)!=0);assertTrue((flags&View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)!=0);
                }
            });
            doubleTap(web,"blank");expectFullscreen(activity,false);
            doubleTap(web,"blank");expectFullscreen(activity,true);
            int[] hidden={0};instrumentation.runOnMainSync(()->{
                web.getWebChromeClient().onShowCustomView(new FrameLayout(activity),()->hidden[0]++);
                web.getWebChromeClient().onHideCustomView();
            });
            stage="return from video fullscreen";
            assertEquals(1,hidden[0]);expectFullscreen(activity,true);
            js(web,"history.pushState({},'', '#keep-history');location.href");
            expect(web,"location.hash","#keep-history");
            String url=js(web,"location.href");
            stage="back after video fullscreen";instrumentation.runOnMainSync(activity::onBackPressed);expectFullscreen(activity,false);
            assertEquals("Back exits fullscreen before navigating",url,js(web,"location.href"));
            Thread.sleep(350);
            instrumentation.runOnMainSync(()->{
                if(Build.VERSION.SDK_INT>=30){
                    assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.statusBars()));
                    assertTrue(activity.getWindow().getDecorView().getRootWindowInsets().isVisible(WindowInsets.Type.navigationBars()));
                }else assertEquals(0,activity.getWindow().getDecorView().getSystemUiVisibility()&(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION));
            });
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
    @Test public void controlsTextScrollingAndLongPressStayUsable()throws Exception{
        MainActivity activity=start();
        try{
            WebView web=load(activity);
            doubleTap(web,"button");expect(web,"String(window.clicks)","2");expectFullscreen(activity,false);
            // Keep the IME from moving the fixture between the two taps; focus is still tested.
            doubleTap(web,"input");expect(web,"document.activeElement.id","input");expectFullscreen(activity,false);
            js(web,"document.activeElement.blur();'ok'");
            instrumentation.runOnMainSync(()->{try{invoke(activity,"hideKeyboard",new Class<?>[0]);}catch(Exception error){throw new RuntimeException(error);}});Thread.sleep(350);
            doubleTap(web,"link");expect(web,"location.hash","#destination");expectFullscreen(activity,false);
            js(web,"document.getElementById('text').scrollIntoView({block:'center'});'ok'");Thread.sleep(150);
            doubleTap(web,"text");Thread.sleep(250);expectFullscreen(activity,false);
            js(web,"window.getSelection().removeAllRanges();window.scrollTo(0,0);'ok'");Thread.sleep(200);
            stage="long press on blank space";float[] p=point(web,"blank");long down=SystemClock.uptimeMillis();touch(down,MotionEvent.ACTION_DOWN,p[0],p[1]);Thread.sleep(650);touch(down,MotionEvent.ACTION_UP,p[0],p[1]);expectFullscreen(activity,false);
            stage="scrolling";
            Thread.sleep(400);down=SystemClock.uptimeMillis();touch(down,MotionEvent.ACTION_DOWN,p[0],p[1]);
            for(int i=1;i<=8;i++){Thread.sleep(20);touch(down,MotionEvent.ACTION_MOVE,p[0],p[1]-i*10);}
            touch(down,MotionEvent.ACTION_UP,p[0],p[1]-80);Thread.sleep(250);
            assertTrue("Native page scrolling remains usable",Integer.parseInt(js(web,"String(Math.round(scrollY))"))>0);expectFullscreen(activity,false);
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
    private void tapControl(MainActivity activity,String name)throws Exception{
        instrumentation.waitForIdleSync();Thread.sleep(200);float[] point={0,0};
        instrumentation.runOnMainSync(()->{try{
            View control=(View)field(activity,name);assertTrue(name+" visible",control.isShown());
            int[] position=new int[2];control.getLocationOnScreen(position);
            point[0]=position[0]+control.getWidth()/2f;point[1]=position[1]+control.getHeight()/2f;
        }catch(Exception error){throw new RuntimeException(error);}});
        long down=SystemClock.uptimeMillis();touch(down,MotionEvent.ACTION_DOWN,point[0],point[1]);Thread.sleep(30);touch(down,MotionEvent.ACTION_UP,point[0],point[1]);
    }
    private void preview(String name){
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(400);
        android.graphics.Bitmap full=instrumentation.getUiAutomation().takeScreenshot();if(full==null)return;
        android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(full,480,Math.round(full.getHeight()*480f/full.getWidth()),true);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();small.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,bytes);
        String encoded=android.util.Base64.encodeToString(bytes.toByteArray(),android.util.Base64.NO_WRAP);int chunks=(encoded.length()+1799)/1800;
        for(int i=0;i<chunks;i++)android.util.Log.i("MenuPreview",name+" "+i+"/"+chunks+" "+encoded.substring(i*1800,Math.min(encoded.length(),(i+1)*1800)));
        if(small!=full)small.recycle();full.recycle();
    }
    @Test public void compactAddressAndAllFiveControlsWorkWithoutLeavingFullscreen()throws Exception{
        MainActivity activity=start();UiDevice device=UiDevice.getInstance(instrumentation);
        try{
            WebView web=load(activity);js(web,"document.getElementById('input').value='keep me';'ok'");
            doubleTap(web,"blank");expectFullscreen(activity,true);expect(web,"document.getElementById('input').value","keep me");
            instrumentation.runOnMainSync(()->{try{
                FullscreenToolbar toolbar=(FullscreenToolbar)field(activity,"fullscreenToolbar");
                assertEquals(5,toolbar.navigation.getChildCount());
                String[] kinds={"home","back","forward","video","menu"};
                for(int i=0;i<kinds.length;i++)assertEquals(kinds[i],((BrowserIcon)toolbar.navigation.getChildAt(i)).kind);
                assertSame(web,invoke(activity,"activeView",new Class<?>[0]));
            }catch(Exception error){throw new RuntimeException(error);}});
            preview("fullscreen-compact");
            tapControl(activity,"homeButton");expect(web,"location.href",Address.HOME);expectFullscreen(activity,true);
            tapControl(activity,"back");expect(web,"location.href","https://fullscreen.example/");expectFullscreen(activity,true);
            tapControl(activity,"forward");expect(web,"location.href",Address.HOME);expectFullscreen(activity,true);
            tapControl(activity,"address");instrumentation.runOnMainSync(()->{try{
                android.widget.EditText address=(android.widget.EditText)field(activity,"address");
                address.setText("https://fullscreen.example/typed");address.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
            }catch(Exception error){throw new RuntimeException(error);}});
            expect(web,"location.href","https://fullscreen.example/typed");expectFullscreen(activity,true);
            expect(web,"String(window.fixtureReady&&document.readyState==='complete')","true");
            tapControl(activity,"videoButton");
            assertTrue("Video dialog title",device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Videos auf dieser Seite")),6000));
            androidx.test.uiautomator.UiObject2 close=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("Schließen",java.util.regex.Pattern.CASE_INSENSITIVE|java.util.regex.Pattern.UNICODE_CASE))),6000);
            assertNotNull("Video dialog opens from compact button",close);close.click();
            tapControl(activity,"menuButton");
            androidx.test.uiautomator.UiObject2 settings=device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Einstellungen")),6000);
            assertNotNull("Menu opens below compact top bar",settings);assertTrue(settings.getVisibleBounds().height()>0);preview("fullscreen-compact-menu");settings.click();
            assertNotNull(device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text("Browserdaten löschen")),6000));
            device.pressBack();expectFullscreen(activity,true);
            doubleTap(web,"blank");expectFullscreen(activity,false);
            instrumentation.runOnMainSync(()->{try{
                assertEquals(7,((android.view.ViewGroup)field(activity,"bottom")).getChildCount());
                assertSame(web,invoke(activity,"activeView",new Class<?>[0]));
            }catch(Exception error){throw new RuntimeException(error);}});
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
    private String displayState(MainActivity activity,View content)throws Exception{
        String[] state={""};instrumentation.runOnMainSync(()->{try{
            Point screen=new Point();activity.getWindowManager().getDefaultDisplay().getRealSize(screen);int[] position=new int[2];content.getLocationOnScreen(position);
            View root=(View)field(activity,"root");WindowInsets insets=root.getRootWindowInsets();DisplayCutout cutout=insets==null?null:insets.getDisplayCutout();
            state[0]="screen="+screen+", content="+new Rect(position[0],position[1],position[0]+content.getWidth(),position[1]+content.getHeight())+", padding="+root.getPaddingLeft()+","+root.getPaddingTop()+","+root.getPaddingRight()+","+root.getPaddingBottom()+", cutout="+cutout+", policy="+activity.getWindow().getAttributes().layoutInDisplayCutoutMode;
        }catch(Exception error){throw new RuntimeException(error);}});return state[0];
    }
    private DisplayCutout expectEntireDisplay(MainActivity activity,View content,boolean landscape)throws Exception{
        long deadline=SystemClock.uptimeMillis()+12000;DisplayCutout[] result={null};boolean[] fills={false};
        do{
            instrumentation.runOnMainSync(()->{try{
                Point screen=new Point();activity.getWindowManager().getDefaultDisplay().getRealSize(screen);int[] position=new int[2];content.getLocationOnScreen(position);
                View root=(View)field(activity,"root");WindowInsets insets=root.getRootWindowInsets();DisplayCutout cutout=insets==null?null:insets.getDisplayCutout();
                boolean present=cutout!=null&&(cutout.getSafeInsetTop()>0||cutout.getSafeInsetLeft()>0||cutout.getSafeInsetRight()>0||cutout.getSafeInsetBottom()>0);
                int[] rootPosition=new int[2];root.getLocationOnScreen(rootPosition);
                boolean browser=content instanceof WebView;
                FullscreenToolbar toolbar=(FullscreenToolbar)field(activity,"fullscreenToolbar");
                boolean fillsContent=position[0]==0&&content.getWidth()==screen.x&&position[1]+content.getHeight()==screen.y;
                boolean controls=true;
                if(browser){
                    int[] toolbarPosition=new int[2];toolbar.getLocationOnScreen(toolbarPosition);
                    controls=toolbarPosition[1]==0&&position[1]==toolbar.getHeight();
                    View[] groups={toolbar.addressSlot,toolbar.navigation};
                    for(View group:groups){
                        int[] location=new int[2];group.getLocationOnScreen(location);Rect area=new Rect(location[0],location[1],location[0]+group.getWidth(),location[1]+group.getHeight());
                        for(Rect camera:cutout==null?java.util.Collections.<Rect>emptyList():cutout.getBoundingRects())controls&=!Rect.intersects(camera,area);
                    }
                    controls&=toolbar.addressSlot.getWidth()>0&&toolbar.navigation.getWidth()>0&&toolbar.navigation.getChildCount()==5;
                }else fillsContent&=position[1]==0;
                fills[0]=present&&(landscape?screen.x>screen.y:screen.y>screen.x)&&rootPosition[0]==0&&rootPosition[1]==0&&root.getWidth()==screen.x&&root.getHeight()==screen.y&&fillsContent&&controls&&root.getPaddingLeft()==0&&root.getPaddingTop()==0&&root.getPaddingRight()==0&&root.getPaddingBottom()==0;
                result[0]=cutout;
            }catch(Exception error){throw new RuntimeException(error);}});
            if(fills[0])return result[0];Thread.sleep(100);
        }while(SystemClock.uptimeMillis()<deadline);
        fail("Content must fill the display around a real emulated cutout: "+displayState(activity,content));return null;
    }
    private void expectProtectedChrome(MainActivity activity,DisplayCutout cutout)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;boolean[] protectedChrome={false};
        do{
            instrumentation.runOnMainSync(()->{try{
                View top=(View)field(activity,"top");int[] position=new int[2];top.getLocationOnScreen(position);Point screen=new Point();activity.getWindowManager().getDefaultDisplay().getRealSize(screen);
                protectedChrome[0]=top.getVisibility()==View.VISIBLE&&position[0]>=cutout.getSafeInsetLeft()&&position[1]>=cutout.getSafeInsetTop()&&position[0]+top.getWidth()<=screen.x-cutout.getSafeInsetRight()&&activity.getWindow().getAttributes().layoutInDisplayCutoutMode==WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            }catch(Exception error){throw new RuntimeException(error);}});
            if(protectedChrome[0])return;Thread.sleep(100);
        }while(SystemClock.uptimeMillis()<deadline);
        fail("Browser controls must avoid the cutout after leaving fullscreen: "+displayState(activity,(View)field(activity,"root")));
    }
    @Test @SdkSuppress(minSdkVersion=28) public void cameraCutoutAreaIsUsedInBrowserAndVideoAndChromeIsRestored()throws Exception{
        // Old emulator System UI can crash after switching the global cutout overlay.
        // Run this probe explicitly after the ordinary suite, in its own instrumentation process.
        org.junit.Assume.assumeTrue("Camera emulation runs in the separate cutout pass", "true".equals(InstrumentationRegistry.getArguments().getString("cutoutProbe")));
        UiDevice device=UiDevice.getInstance(instrumentation);String overlay="com.android.internal.display.cutout.emulation.tall";
        String before=device.executeShellCommand("cmd overlay list --user 0");assertTrue("Emulator must provide a camera-cutout overlay",before.contains(overlay));boolean alreadyEnabled=before.contains("[x] "+overlay);
        MainActivity activity=null;
        try{
            device.setOrientationNatural();device.executeShellCommand("cmd overlay enable --user 0 "+overlay);
            activity=start();MainActivity browser=activity;WebView web=load(browser);
            doubleTap(web,"blank");expectFullscreen(browser,true);DisplayCutout portrait=expectEntireDisplay(browser,web,false);
            assertTrue("Portrait cutout at top",portrait.getSafeInsetTop()>0);
            preview("fullscreen-camera-portrait");
            stage="back with camera cutout";instrumentation.runOnMainSync(browser::onBackPressed);expectFullscreen(browser,false);expectProtectedChrome(browser,portrait);
            FrameLayout video=new FrameLayout(browser);instrumentation.runOnMainSync(()->web.getWebChromeClient().onShowCustomView(video,()->{}));expectEntireDisplay(browser,video,false);
            instrumentation.runOnMainSync(()->web.getWebChromeClient().onHideCustomView());expectProtectedChrome(browser,portrait);
            doubleTap(web,"blank");expectFullscreen(browser,true);device.setOrientationLeft();DisplayCutout side=expectEntireDisplay(browser,web,true);
            assertTrue("Rotated cutout at side",side.getSafeInsetLeft()>0||side.getSafeInsetRight()>0);
            preview("fullscreen-camera-landscape");
            instrumentation.runOnMainSync(browser::onBackPressed);expectFullscreen(browser,false);expectProtectedChrome(browser,side);
        }finally{
            try{
                device.setOrientationNatural();
                if(!alreadyEnabled)device.executeShellCommand("cmd overlay disable --user 0 "+overlay);
                device.unfreezeRotation();
                // Verify the actual overlay state; a cached Resources instance can retain
                // the old overlay assets in this dedicated instrumentation process.
                String restored=device.executeShellCommand("cmd overlay list --user 0");
                assertEquals("Original camera overlay state restored",alreadyEnabled,restored.contains("[x] "+overlay));
                device.waitForIdle();instrumentation.waitForIdleSync();Thread.sleep(500);instrumentation.waitForIdleSync();
            }finally{
                if(activity!=null){MainActivity browser=activity;instrumentation.runOnMainSync(browser::finish);}
                instrumentation.waitForIdleSync();
            }
        }
    }

}
