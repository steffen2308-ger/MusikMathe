package de.steffen.musikmathe;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Supplier;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Keeps the live fullscreen WebView in its own PiP task while the other browser is used. */
final class BrowserPip {
    private static WeakReference<BrowserPip> owner=new WeakReference<>(null);
    static final String VIDEO="(function(){var f=document.fullscreenElement||document.webkitFullscreenElement,v=f&&(f.tagName==='VIDEO'?f:f.querySelector('video'));return v||Array.from(document.querySelectorAll('video')).find(function(e){return !e.paused;})||document.querySelector('video');})()";
    private final Activity activity;
    private final Supplier<WebView> source;
    private final Supplier<View> fullscreen;
    private final Runnable browse;
    private final Runnable restoreFullscreen;
    private final String token=UUID.randomUUID().toString();
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private BrowserIcon button;
    private FrameLayout host;
    private WebView video;
    private boolean pending,entered,playing=true,destroyed,hasVideo;
    private int width=16,height=9;
    private int publishedWidth=-1,publishedHeight=-1;
    private boolean publishedPlaying;
    private Rect sourceBounds;
    private final Runnable poll=new Runnable(){public void run(){if(!inMode()||destroyed)return;readState(()->updateParams());handler.postDelayed(this,1000);}};

    BrowserPip(Activity activity,Supplier<WebView> source,Supplier<View> fullscreen,Runnable browse,Runnable restoreFullscreen){this.activity=activity;this.source=source;this.fullscreen=fullscreen;this.browse=browse;this.restoreFullscreen=restoreFullscreen;}
    static boolean supported(Activity activity){return Build.VERSION.SDK_INT>=26&&!BrowserApplication.privateProcess&&activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);}
    boolean inMode(){return Build.VERSION.SDK_INT>=26&&activity.isInPictureInPictureMode();}
    boolean keepsPlaying(){return pending||inMode();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private void message(String text){if(!destroyed)Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    void showButton(FrameLayout root){
        hideButton();if(!supported(activity))return;host=root;
        if(button==null){
            button=new BrowserIcon(activity,"pip","Video schweben lassen");button.tint=Color.WHITE;button.count=0;
            GradientDrawable background=new GradientDrawable();background.setColor(0xcc182b2b);background.setCornerRadius(dp(12));button.setBackground(background);
            button.setOnClickListener(v->enter());
        }
        host.addView(button);positionButton();button.setVisibility(inMode()?View.GONE:View.VISIBLE);
    }
    void positionButton(){
        if(host==null||button==null||button.getParent()!=host)return;
        int top=dp(12),right=dp(12);WindowInsets insets=host.getRootWindowInsets();
        if(Build.VERSION.SDK_INT>=28&&insets!=null&&insets.getDisplayCutout()!=null){top+=insets.getDisplayCutout().getSafeInsetTop();right+=insets.getDisplayCutout().getSafeInsetRight();}
        android.view.ViewGroup.LayoutParams old=button.getLayoutParams();
        if(old instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams previous=(FrameLayout.LayoutParams)old;if(previous.width==dp(44)&&previous.height==dp(44)&&previous.topMargin==top&&previous.rightMargin==right)return;}
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.RIGHT);params.topMargin=top;params.rightMargin=right;button.setLayoutParams(params);
    }
    void hideButton(){if(button!=null&&button.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)button.getParent()).removeView(button);host=null;}
    @android.annotation.TargetApi(26) void enter(){
        if(!supported(activity)){message("Bild-in-Bild benötigt Android 8 oder neuer und die Unterstützung deines Geräts.");return;}
        BrowserPip existing=owner.get();
        if(existing!=null&&existing!=this&&existing.keepsPlaying()){message("Bitte zuerst das andere schwebende Video schließen oder vergrößern.");return;}
        if(inMode()||pending||destroyed)return;
        if(fullscreen.get()==null){
            BrowserDialogs.builder(activity).setTitle("Schwebendes Video").setMessage("Starte ein Video und schalte es im Videoplayer auf Vollbild. Tippe dann oben rechts auf das kleine Fenstersymbol. Das Video schwebt weiter, und das zweite Browserfenster öffnet sich.\n\nIm kleinen Fenster kannst du pausieren, fortsetzen, vergrößern oder schließen. YouTube und andere Webseiten bestimmen weiterhin, ob ein Video im Browser wiedergegeben werden darf.").setPositiveButton("Verstanden",null).show();return;
        }
        video=source.get();if(video==null)return;
        pending=true;owner=new WeakReference<>(this);
        readState(()->{
            if(destroyed||fullscreen.get()==null||source.get()!=video){pending=false;return;}
            if(!hasVideo){pending=false;owner.clear();message("Kein Video im Vollbild erkannt. Öffne zuerst das Vollbild direkt am Videoplayer.");return;}
            // Calculate the pinned window against normal system-bar insets, rather
            // than placing its controls over the navigation buttons of the other task.
            activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if(Build.VERSION.SDK_INT>=30&&activity.getWindow().getInsetsController()!=null)activity.getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
            handler.postDelayed(()->requestPip(),250);
        });
    }
    @android.annotation.TargetApi(26) private void requestPip(){
            if(destroyed||!pending||activity.isFinishing()||fullscreen.get()==null){pending=false;return;}
            try{
                if(activity.enterPictureInPictureMode(params())){
                    handler.post(()->{if(!destroyed&&keepsPlaying())browse.run();});
                }else failed();
            }catch(IllegalStateException|IllegalArgumentException|SecurityException error){failed();}
    }
    private void failed(){pending=false;if(owner.get()==this)owner.clear();restoreFullscreen.run();message("Android konnte das schwebende Fenster nicht öffnen. Prüfe in den App-Einstellungen, ob Bild-in-Bild für BestBrowser erlaubt ist.");}
    private void readState(Runnable done){
        WebView web=video;if(web==null||destroyed)return;
        web.evaluateJavascript("(function(){var v="+VIDEO+";return JSON.stringify(v?{width:v.videoWidth,height:v.videoHeight,playing:!v.paused}:{});})()",value->{
            if(destroyed||video!=web)return;
            try{Object decoded=new JSONTokener(value).nextValue();JSONObject state=new JSONObject(String.valueOf(decoded));hasVideo=state.has("playing");int w=state.optInt("width"),h=state.optInt("height");if(w>0&&h>0){width=w;height=h;}playing=state.optBoolean("playing",playing);}catch(Exception ignored){hasVideo=false;}
            done.run();
        });
    }
    static Rational ratio(int width,int height){
        if(width<=0||height<=0)return new Rational(16,9);
        double value=(double)width/height;if(value<1/2.39)return new Rational(100,239);if(value>2.39)return new Rational(239,100);return new Rational(width,height);
    }
    @android.annotation.TargetApi(26) private RemoteAction action(String action,int icon,String label){
        Intent intent=new Intent(activity,VideoWindowReceiver.class).setData(Uri.parse("bestbrowser://pip/"+token+"/"+action)).putExtra("token",token).putExtra("control",action);
        PendingIntent pending=PendingIntent.getBroadcast(activity,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new RemoteAction(Icon.createWithResource(activity,icon),label,label,pending);
    }
    @android.annotation.TargetApi(26) private PictureInPictureParams params(){
        PictureInPictureParams.Builder builder=new PictureInPictureParams.Builder().setAspectRatio(ratio(width,height)).setActions(Arrays.asList(
            action(playing?"pause":"play",playing?android.R.drawable.ic_media_pause:android.R.drawable.ic_media_play,playing?"Video pausieren":"Video fortsetzen"),
            action("browse",android.R.drawable.ic_menu_view,"Im Browser weitersurfen")));
        if(!inMode()){View view=fullscreen.get();Rect bounds=new Rect();if(view!=null&&view.getGlobalVisibleRect(bounds)&&!bounds.isEmpty())sourceBounds=bounds;}
        if(sourceBounds!=null)builder.setSourceRectHint(sourceBounds);
        if(Build.VERSION.SDK_INT>=31)builder.setSeamlessResizeEnabled(true);
        return builder.build();
    }
    @android.annotation.TargetApi(26) private void updateParams(){
        if(!inMode()||(publishedWidth==width&&publishedHeight==height&&publishedPlaying==playing))return;
        try{activity.setPictureInPictureParams(params());publishedWidth=width;publishedHeight=height;publishedPlaying=playing;}catch(IllegalStateException|IllegalArgumentException ignored){}
    }
    void modeChanged(boolean pip){
        pending=false;handler.removeCallbacks(poll);if(button!=null)button.setVisibility(pip?View.GONE:View.VISIBLE);
        if(pip){entered=true;publishedWidth=-1;owner=new WeakReference<>(this);if(video!=null)video.onResume();handler.post(poll);}
        else{if(owner.get()==this)owner.clear();}
    }
    void resumed(){if(!inMode()){entered=false;pending=false;}}
    void stopped(){
        handler.removeCallbacks(poll);
        if(entered||pending){pause();restoreGesturePolicy();entered=false;pending=false;if(owner.get()==this)owner.clear();}
    }
    private void pause(){if(video!=null){video.evaluateJavascript("(function(){var v="+VIDEO+";if(v)v.pause();})()",null);video.onPause();}}
    static void control(Intent intent){
        BrowserPip current=owner.get();if(current==null||current.destroyed||!current.inMode()||intent==null||!current.token.equals(intent.getStringExtra("token")))return;
        String action=intent.getStringExtra("control");if("browse".equals(action)){current.browse.run();return;}
        android.util.Log.i("VideoWindow","Accepted PiP action: "+action);
        if(!"play".equals(action)&&!"pause".equals(action))return;
        WebView web=current.video;if(web==null)return;
        // A remote play action is an explicit user request; restrict this setting to PiP.
        if("play".equals(action))web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.evaluateJavascript("(function(){var v="+VIDEO+";if(!v)return;"+("pause".equals(action)?"v.pause();":"var p=v.play();if(p&&p.catch)p.catch(function(){});")+"})()",value->current.readState(()->{android.util.Log.i("VideoWindow","PiP action completed: playing="+current.playing);current.updateParams();}));
    }
    void destroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(owner.get()==this)owner.clear();hideButton();video=null;}
    void restoreGesturePolicy(){if(video!=null)video.getSettings().setMediaPlaybackRequiresUserGesture(true);}
}
