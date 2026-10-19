package de.steffen.musikmathe;

import android.app.Instrumentation;
import android.content.Intent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual WebView execution: native network failure, same-origin scripts and live CSS. No external server needed. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion=26)
public class AdBlockingWebViewTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private Object invoke(MainActivity activity,String name,Class<?>[] types,Object...args)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(activity,args);
    }
    private String js(WebView web,String expression)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] answer={null};
        instrumentation.runOnMainSync(()->web.evaluateJavascript(expression,result->{answer[0]=result;done.countDown();}));
        assertTrue("WebView script callback",done.await(10,TimeUnit.SECONDS));
        return new JSONArray("["+answer[0]+"]").optString(0);
    }
    private void expect(WebView web,String expression,String expected)throws Exception{
        long deadline=System.currentTimeMillis()+10000;String actual;
        do{actual=js(web,expression);if(expected.equals(actual))return;Thread.sleep(50);}while(System.currentTimeMillis()<deadline);
        assertEquals(expression,expected,actual);
    }
    private void load(WebView web){
        String html="<html><body><div id='content'>Useful content</div><div id='static' class='adbox banner_ads adsbox'>Advert</div>"
                +"<script src='https://publisher.example/widget/ads.js'></script><script>window.control='waiting';window.adFetch='waiting';"
                +"fetch('https://publisher.example/control',{mode:'no-cors'}).then(function(){window.control='loaded';},function(){window.control='failed';});"
                +"fetch('https://pixel.doubleclick.net/fakepage.html',{method:'HEAD',mode:'no-cors'}).then(function(){window.adFetch='loaded';},function(){window.adFetch='blocked';});"
                +"setTimeout(function(){var ad=document.createElement('div');ad.id='dynamic';ad.className='textads';ad.textContent='Advert';document.body.appendChild(ad);},100);"
                +"</script></body></html>";
        instrumentation.runOnMainSync(()->{web.stopLoading();web.loadDataWithBaseURL("https://adblock.turtlecute.org/",html,"text/html","UTF-8",null);});
    }
    @Test public void blocksFetchScriptsAndDynamicAdsAndHonorsExceptions()throws Exception{
        instrumentation.getTargetContext().getSharedPreferences("browser",0).edit().clear().commit();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        WebView[] holder={null};
        try{
            instrumentation.runOnMainSync(()->{
                try{
                    WebView web=(WebView)invoke(activity,"activeView",new Class<?>[0]);holder[0]=web;
                    WebViewClient real=web.getWebViewClient();
                    web.setWebViewClient(new WebViewClient(){
                        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                            WebResourceResponse denied=real.shouldInterceptRequest(view,request);if(denied!=null)return denied;
                            return new WebResourceResponse("application/javascript","UTF-8",new ByteArrayInputStream("window.fixtureAdRan=true;".getBytes(StandardCharsets.UTF_8)));
                        }
                        @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){real.onPageStarted(view,url,icon);}
                        @Override public void onPageFinished(WebView view,String url){real.onPageFinished(view,url);}
                    });
                }catch(Exception error){throw new RuntimeException(error);}
            });
            WebView web=holder[0];load(web);
            expect(web,"window.control","loaded");
            expect(web,"window.adFetch","blocked");
            expect(web,"String(!!window.fixtureAdRan)","false");
            expect(web,"getComputedStyle(document.getElementById('static')).display","none");
            expect(web,"getComputedStyle(document.getElementById('dynamic')).display","none");
            expect(web,"getComputedStyle(document.getElementById('content')).display","block");
            instrumentation.runOnMainSync(()->{try{invoke(activity,"setAdBlockSiteException",new Class<?>[]{String.class,boolean.class},"adblock.turtlecute.org",true);}catch(Exception error){throw new RuntimeException(error);}});
            load(web);expect(web,"window.adFetch","loaded");expect(web,"String(!!window.fixtureAdRan)","true");
            expect(web,"getComputedStyle(document.getElementById('static')).display","block");
            instrumentation.runOnMainSync(()->{try{invoke(activity,"setAdBlockSiteException",new Class<?>[]{String.class,boolean.class},"adblock.turtlecute.org",false);invoke(activity,"setAdBlockingEnabled",new Class<?>[]{boolean.class},false);}catch(Exception error){throw new RuntimeException(error);}});
            load(web);expect(web,"window.adFetch","loaded");expect(web,"getComputedStyle(document.getElementById('static')).display","block");
        }finally{instrumentation.runOnMainSync(activity::finish);}
    }
}
