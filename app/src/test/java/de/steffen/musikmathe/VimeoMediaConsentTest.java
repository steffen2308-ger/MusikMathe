package de.steffen.musikmathe;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Looper;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class VimeoMediaConsentTest {
    private static final String PAGE="https://vimeo.com/showcase/2600007";
    private static final class Request extends PermissionRequest {
        final String origin;final String[] resources;int denied;String[] granted;
        Request(){this("https://player.vimeo.com/",RESOURCE_PROTECTED_MEDIA_ID);}
        Request(String origin,String...resources){this.origin=origin;this.resources=resources;}
        @Override public Uri getOrigin(){return Uri.parse(origin);}
        @Override public String[] getResources(){return resources;}
        @Override public void grant(String[] resources){granted=resources;}
        @Override public void deny(){denied++;}
    }
    private static final class DeferredView extends WebView {
        ValueCallback<String> callback;int scans;
        DeferredView(Activity activity){super(activity);}
        @Override public void evaluateJavascript(String script,ValueCallback<String> callback){this.callback=callback;scans++;}
        void answer(boolean found){ValueCallback<String> value=callback;callback=null;value.onReceiveValue(String.valueOf(found));}
    }
    @Test public void oneVerifiedDocumentRequiresOneExplicitDecisionForMultiplePlayers(){
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();DeferredView web=new DeferredView(controller.get());VimeoMediaConsent consent=new VimeoMediaConsent(controller.get());
        try{
            Request first=new Request(),second=new Request();consent.request(web,PAGE,first,()->true);consent.request(web,PAGE,second,()->true);assertNull(first.granted);assertNull(second.granted);assertEquals(1,web.scans);assertNull(ShadowAlertDialog.getLatestAlertDialog());
            web.answer(true);AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertArrayEquals(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID},first.granted);assertArrayEquals(first.granted,second.granted);assertEquals(0,first.denied);assertEquals(0,second.denied);
            Request next=new Request();consent.request(web,PAGE,next,()->true);assertArrayEquals(first.granted,next.granted);assertEquals(1,web.scans);assertFalse(dialog.isShowing());
            consent.navigation(web);Request other=new Request();consent.request(web,PAGE,other,()->true);assertNull(other.granted);assertEquals(2,web.scans);web.answer(true);assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
        }finally{consent.reset();web.destroy();controller.pause().stop().destroy();}
    }
    @Test public void missingFramesTimeoutCancellationAndStaleConsentNeverGrant(){
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();DeferredView web=new DeferredView(controller.get());VimeoMediaConsent consent=new VimeoMediaConsent(controller.get());AtomicBoolean current=new AtomicBoolean(true);
        try{
            Request absent=new Request();consent.request(web,PAGE,absent,current::get);web.answer(false);assertEquals(1,absent.denied);assertNull(absent.granted);
            Request timeout=new Request();consent.request(web,PAGE,timeout,current::get);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11));assertEquals(1,timeout.denied);web.answer(true);assertNull(timeout.granted);
            Request gone=new Request();consent.request(web,PAGE,gone,current::get);consent.cancelled(gone);web.answer(true);assertEquals("Native cancellation is already settled by WebView",0,gone.denied);assertNull(gone.granted);
            Request stale=new Request();consent.request(web,PAGE,stale,current::get);web.answer(true);current.set(false);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(stale.granted);assertEquals(1,stale.denied);
        }finally{consent.reset();web.destroy();controller.pause().stop().destroy();}
    }
    @Test public void onlyHttpsVimeoShowcasesAndExactlyProtectedMediaAreEligible(){
        for(String page:new String[]{"http://vimeo.com/showcase/1","https://vimeo.com.evil.test/showcase/1","https://other.test/showcase/1","https://user@vimeo.com/showcase/1","https://vimeo.com:444/showcase/1","https://vimeo.com/76979871"})assertFalse(page,VimeoMediaConsent.eligible(page,new Request()));
        for(String origin:new String[]{"http://player.vimeo.com/","https://player.vimeo.com.evil.test/","https://user@player.vimeo.com/","https://player.vimeo.com:444/"})assertFalse(origin,VimeoMediaConsent.eligible(PAGE,new Request(origin,PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)));
        assertFalse(VimeoMediaConsent.eligible(PAGE,new Request("https://player.vimeo.com/",PermissionRequest.RESOURCE_AUDIO_CAPTURE,PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)));
        assertTrue(VimeoMediaConsent.eligible(PAGE,new Request()));assertTrue(VimeoMediaConsent.eligible("https://www.vimeo.com/album/123/video/456",new Request()));
    }
    @Test public void denialAppliesToFurtherPlayersUntilTheDocumentChanges(){
        ActivityController<Activity> controller=Robolectric.buildActivity(Activity.class).setup();DeferredView web=new DeferredView(controller.get());VimeoMediaConsent consent=new VimeoMediaConsent(controller.get());
        try{Request a=new Request();consent.request(web,PAGE,a,()->true);web.answer(true);ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,a.denied);Request b=new Request();consent.request(web,PAGE,b,()->true);assertEquals(1,b.denied);assertNull(b.granted);assertEquals(1,web.scans);}
        finally{consent.reset();web.destroy();controller.pause().stop().destroy();}
    }
}
