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
    @Test public void completedNavigationCountsOnceEvenWithRepeatedFinishCallbacks()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        try{
            WebView view=web(c.get());android.webkit.WebViewClient client=view.getWebViewClient();
            client.onPageStarted(view,"https://visits.example/",null);client.onPageFinished(view,"https://visits.example/");client.onPageFinished(view,"https://visits.example/");
            BrowserStore store=new BrowserStore(c.get());assertEquals(1,store.read("history").get(0).visits);
            client.onPageStarted(view,"https://visits.example/",null);client.onPageFinished(view,"https://visits.example/");assertEquals(2,store.read("history").get(0).visits);
        }finally{c.pause().stop().destroy();}
    }
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
    static final class WebsiteRequest extends android.webkit.PermissionRequest {
        boolean denied; String[] granted;
        private final String site;
        private final String[] resources;
        WebsiteRequest(String site,String...resources){this.site=site;this.resources=resources.length==0?new String[]{RESOURCE_AUDIO_CAPTURE}:resources;}
        @Override public Uri getOrigin(){return Uri.parse(site);}
        @Override public String[] getResources(){return resources;}
        @Override public void grant(String[] resources){granted=resources;}
        @Override public void deny(){denied=true;}
    }
    @Test public void websiteOnlyReceivesRequestedPermissions()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        invoke(a,"navigate",new Class<?>[]{String.class},"https://example.com/page");
        WebsiteRequest request=new WebsiteRequest("https://example.com/");
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO);
        invoke(a,"requestWebsitePermission",new Class<?>[]{MainActivity.Tab.class,android.webkit.PermissionRequest.class},tabs(a).get(0),request);
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        a.onRequestPermissionsResult(11,new String[]{android.Manifest.permission.RECORD_AUDIO},new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
        assertArrayEquals(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE},request.granted);assertFalse(request.denied);c.pause().stop().destroy();
    }
    @Test public void switchingTabsCancelsWebsitePermissionAndHttpIsRejected()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        invoke(a,"navigate",new Class<?>[]{String.class},"https://example.com");WebsiteRequest request=new WebsiteRequest("https://example.com");
        invoke(a,"requestWebsitePermission",new Class<?>[]{MainActivity.Tab.class,android.webkit.PermissionRequest.class},tabs(a).get(0),request);
        invoke(a,"newTab",new Class<?>[]{String.class,boolean.class},Address.HOME,true);assertTrue(request.denied);assertNull(request.granted);
        WebsiteRequest insecure=new WebsiteRequest("http://example.com");
        invoke(a,"requestWebsitePermission",new Class<?>[]{MainActivity.Tab.class,android.webkit.PermissionRequest.class},tabs(a).get(1),insecure);
        assertTrue(insecure.denied);assertNull(insecure.granted);c.pause().stop().destroy();
    }
    private WebsiteRequest protectedRequest(String site){return new WebsiteRequest(site,android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID);}
    private void request(MainActivity activity,MainActivity.Tab tab,WebsiteRequest request)throws Exception{
        invoke(activity,"requestWebsitePermission",new Class<?>[]{MainActivity.Tab.class,android.webkit.PermissionRequest.class},tab,request);
    }
    private void choose(android.app.AlertDialog dialog,int button){
        dialog.getButton(button).performClick();
        // AlertDialog delivers button callbacks through its main-thread Handler.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }
    @Test public void protectedMediaNeedsConsentAndGrantsWithoutCameraOrMicrophonePermission()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        try{
            invoke(a,"navigate",new Class<?>[]{String.class},"https://www.netflix.com/watch/1");WebsiteRequest request=protectedRequest("https://www.netflix.com/");
            request(a,tabs(a).get(0),request);assertNull(request.granted);assertFalse(request.denied);
            android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Geschützte Inhalte",Shadows.shadowOf(dialog).getTitle());assertTrue(Shadows.shadowOf(dialog).getMessage().toString().contains("www.netflix.com"));
            assertNull(Shadows.shadowOf(a).getLastRequestedPermission());
            choose(dialog,android.app.AlertDialog.BUTTON_POSITIVE);
            assertArrayEquals(new String[]{android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID},request.granted);assertFalse(request.denied);
            assertNull(Shadows.shadowOf(a).getLastRequestedPermission());
            invoke(a,"navigate",new Class<?>[]{String.class},"https://other.example/");assertFalse("Completed grant must not be denied later",request.denied);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void protectedMediaCanBeDeniedAndPendingConsentIsCancelledOnNavigation()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        try{
            invoke(a,"navigate",new Class<?>[]{String.class},"https://www.netflix.com/watch/1");WebsiteRequest denied=protectedRequest("https://www.netflix.com");request(a,tabs(a).get(0),denied);
            choose(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(),android.app.AlertDialog.BUTTON_NEGATIVE);assertTrue(denied.denied);assertNull(denied.granted);
            WebsiteRequest stale=protectedRequest("https://www.netflix.com");request(a,tabs(a).get(0),stale);android.app.AlertDialog old=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            invoke(a,"navigate",new Class<?>[]{String.class},"https://other.example/");choose(old,android.app.AlertDialog.BUTTON_POSITIVE);assertTrue(stale.denied);assertNull(stale.granted);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void protectedMediaRejectsHttpOtherOriginsInactiveTabsAndUnknownResources()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        try{
            invoke(a,"navigate",new Class<?>[]{String.class},"https://www.netflix.com/watch/1");MainActivity.Tab first=tabs(a).get(0);
            for(String origin:new String[]{"http://www.netflix.com","https://evil.example","https://www.netflix.com:444"}){
                WebsiteRequest blocked=protectedRequest(origin);request(a,first,blocked);assertTrue(origin,blocked.denied);assertNull(blocked.granted);
            }
            WebsiteRequest unknown=new WebsiteRequest("https://www.netflix.com",android.webkit.PermissionRequest.RESOURCE_MIDI_SYSEX,"future-resource");request(a,first,unknown);assertTrue(unknown.denied);assertNull(unknown.granted);
            invoke(a,"newTab",new Class<?>[]{String.class,boolean.class},Address.HOME,true);WebsiteRequest inactive=protectedRequest("https://www.netflix.com");request(a,first,inactive);assertTrue(inactive.denied);assertNull(inactive.granted);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void mixedRequestNeverGrantsUnknownOrDeniedCameraResources()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        try{
            invoke(a,"navigate",new Class<?>[]{String.class},"https://media.example/");
            Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.CAMERA,android.Manifest.permission.RECORD_AUDIO);
            WebsiteRequest mixed=new WebsiteRequest("https://media.example",android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE,"future-resource");request(a,tabs(a).get(0),mixed);
            choose(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(),android.app.AlertDialog.BUTTON_POSITIVE);
            assertNull(mixed.granted);assertArrayEquals(new String[]{android.Manifest.permission.CAMERA},Shadows.shadowOf(a).getLastRequestedPermission().requestedPermissions);
            a.onRequestPermissionsResult(11,new String[]{android.Manifest.permission.CAMERA},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
            assertArrayEquals(new String[]{android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID},mixed.granted);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void lateCancellationOfOldRequestDoesNotDismissNewProtectedMediaPrompt()throws Exception{
        clear();ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();MainActivity a=c.get();
        try{
            invoke(a,"navigate",new Class<?>[]{String.class},"https://media.example/");MainActivity.Tab tab=tabs(a).get(0);
            WebsiteRequest old=protectedRequest("https://media.example"),fresh=protectedRequest("https://media.example");request(a,tab,old);request(a,tab,fresh);assertTrue(old.denied);
            web(a).getWebChromeClient().onPermissionRequestCanceled(old);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(fresh.denied);choose(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(),android.app.AlertDialog.BUTTON_POSITIVE);assertNotNull(fresh.granted);
        }finally{c.pause().stop().destroy();}
    }
    @Test public void privateTabsAlsoRequireTheirOwnProtectedMediaConsent()throws Exception{
        clear();BrowserApplication.privateReady=true;ActivityController<PrivateActivity> c=Robolectric.buildActivity(PrivateActivity.class).setup();MainActivity a=c.get();
        try{
            invoke(a,"navigate",new Class<?>[]{String.class},"https://media.example/");WebsiteRequest request=protectedRequest("https://media.example");request(a,tabs(a).get(0),request);assertNull(request.granted);
            choose(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog(),android.app.AlertDialog.BUTTON_POSITIVE);assertNotNull(request.granted);
        }finally{c.pause().stop().destroy();BrowserApplication.privateReady=false;}
    }
}
