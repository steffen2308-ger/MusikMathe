package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
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

/** Actual touch dispatch into WebView, including page controls and the native immersive UI. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=26)
public class BrowserFullscreenTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private String stage="initial";
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
        return (MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private WebView load(MainActivity activity)throws Exception{
        WebView[] holder={null};instrumentation.runOnMainSync(()->{
            try{
                holder[0]=(WebView)invoke(activity,"activeView",new Class<?>[0]);
                String html="<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{margin:0;min-height:300vh}#blank{height:210px;background:#eef}button,input,a,p{display:block;height:44px;margin:4px;width:200px}#text{font-size:16px}</style></head><body><div id='blank'></div><button id='button' onclick='window.clicks++'>Button</button><input id='input'><a id='link' href='#destination'>Link</a><p id='text'>Selectable text</p><div id='destination'></div><script>window.clicks=0;window.fixtureReady=true;</script></body></html>";
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
        JSONArray fraction=new JSONArray(js(web,"JSON.stringify((function(){var e=document.getElementById('"+id+"'),r=e.getBoundingClientRect();if(e.id==='text'){var range=document.createRange();range.selectNodeContents(e);r=range.getBoundingClientRect();}return [(r.left+Math.min(r.width/2,30))/innerWidth,(r.top+r.height/2)/innerHeight];})())"));
        float[] point={0,0};instrumentation.runOnMainSync(()->{int[] location=new int[2];web.getLocationOnScreen(location);point[0]=location[0]+web.getWidth()*(float)fraction.optDouble(0);point[1]=location[1]+web.getHeight()*(float)fraction.optDouble(1);});return point;
    }
    private void touch(long down,int action,float x,float y){
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);
        event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try{assertTrue("Touch injection at "+x+","+y,instrumentation.getUiAutomation().injectInputEvent(event,true));}finally{event.recycle();}
    }
    private void doubleTap(WebView web,String id)throws Exception{
        stage="double tap on "+id;
        Thread.sleep(400);float[] p=point(web,id);
        for(int i=0;i<2;i++){long down=SystemClock.uptimeMillis();touch(down,MotionEvent.ACTION_DOWN,p[0],p[1]);Thread.sleep(25);touch(down,MotionEvent.ACTION_UP,p[0],p[1]);if(i==0)Thread.sleep(100);}
    }
    private boolean fullscreen(MainActivity activity)throws Exception{
        boolean[] value={false};instrumentation.runOnMainSync(()->{try{value[0]=(boolean)field(activity,"browserFullscreen");}catch(Exception e){throw new RuntimeException(e);}});return value[0];
    }
    private void expectFullscreen(MainActivity activity,boolean expected)throws Exception{
        long deadline=SystemClock.uptimeMillis()+10000;
        while(fullscreen(activity)!=expected&&SystemClock.uptimeMillis()<deadline)Thread.sleep(50);
        assertEquals("Browser fullscreen after "+stage,expected,fullscreen(activity));
        instrumentation.runOnMainSync(()->{try{
            assertEquals(expected?View.GONE:View.VISIBLE,((View)field(activity,"top")).getVisibility());
            assertEquals(expected?View.GONE:View.VISIBLE,((View)field(activity,"bottom")).getVisibility());
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
            assertEquals(1,hidden[0]);expectFullscreen(activity,true);
            js(web,"history.pushState({},'', '#keep-history');location.href");
            expect(web,"location.hash","#keep-history");
            String url=js(web,"location.href");
            instrumentation.runOnMainSync(activity::onBackPressed);expectFullscreen(activity,false);
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
}
