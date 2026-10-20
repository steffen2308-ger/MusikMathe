package de.steffen.musikmathe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import androidx.webkit.OutcomeReceiverCompat;
import androidx.webkit.PrefetchException;
import androidx.webkit.Profile;
import androidx.webkit.SpeculativeLoadingConfig;
import androidx.webkit.SpeculativeLoadingParameters;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Native HTML prefetch, never hidden-page execution or a second HTTP/cookie implementation. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
final class LinkPreloader {
    interface Backend {
        boolean supported();
        PreloadQueue.Cancel fetch(WebView view,String url,java.util.function.Consumer<Boolean> done);
        void clear();
    }
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BooleanSupplier enabled,wifi;
    private final Predicate<String> blocked;
    private final Backend backend;
    private final PreloadQueue queue;
    private WebView view;
    private String page="",script;
    private int generation;
    private boolean scanning,destroyed,watching;
    private ConnectivityManager manager;
    private final Runnable scanAgain=this::scan;
    private final ConnectivityManager.NetworkCallback networks=new ConnectivityManager.NetworkCallback(){
        @Override public void onAvailable(Network network){changed();}
        @Override public void onLost(Network network){changed();}
        @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities caps){changed();}
        private void changed(){handler.post(()->{if(!destroyed)recheck();});}
    };
    LinkPreloader(Context context,BooleanSupplier enabled,Predicate<String> blocked){this(context,enabled,blocked,()->PreloadWifi.allowed(context),new NativeBackend());}
    LinkPreloader(Context context,BooleanSupplier enabled,Predicate<String> blocked,BooleanSupplier wifi,Backend backend){
        this.context=context;this.enabled=enabled;this.blocked=blocked;this.wifi=wifi;this.backend=backend;
        queue=new PreloadQueue((url,done)->{
            if(!allowed()||view==null){handler.post(()->{recheck();done.accept(false);});return ()->{};}
            return backend.fetch(view,url,done);
        });
    }
    void page(WebView next,String url){
        if(destroyed)return;
        if(view!=next||!page.equals(url)){navigation(null);view=next;page=url==null?"":url;}
        recheck();
    }
    private boolean allowed(){return !destroyed&&view!=null&&enabled.getAsBoolean()&&wifi.getAsBoolean()&&backend.supported()&&(Address.isWeb(page)||Address.HOME.equals(page));}
    private void watch(){
        if(watching)return;
        try{manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);if(manager==null)return;
            if(Build.VERSION.SDK_INT>=24)manager.registerDefaultNetworkCallback(networks);else manager.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),networks);
            watching=true;
        }catch(RuntimeException unavailable){manager=null;}
    }
    void recheck(){
        handler.removeCallbacks(scanAgain);
        if(view==null)return;
        if(enabled.getAsBoolean()&&backend.supported())watch();else unwatch();
        if(!allowed()){generation++;scanning=false;queue.pause();return;}
        queue.enable();scan();
    }
    private void scan(){
        if(!allowed()||scanning)return;
        if(!page.equals(view.getUrl())){navigation(null);return;}
        if(script==null)try(java.io.InputStream input=context.getAssets().open("preload-links.js");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);script=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }catch(java.io.IOException unavailable){return;}
        scanning=true;int epoch=generation;String source=page;WebView owner=view;
        owner.evaluateJavascript(script,value->{
            if(destroyed||epoch!=generation)return;scanning=false;
            if(!allowed()||view!=owner||!source.equals(page)||!source.equals(owner.getUrl()))return;
            try{
                JSONObject result=new JSONObject(String.valueOf(new JSONTokener(value).nextValue()));
                if(!source.equals(result.optString("page")))return;
                JSONArray links=result.optJSONArray("links");ArrayList<String> candidates=new ArrayList<>();
                if(links!=null)for(int i=0;i<links.length();i++){JSONObject link=links.optJSONObject(i);if(link==null)continue;String url=PreloadLinks.eligible(source,link.optString("url"),link.optString("label"),link.optBoolean("download"),link.optString("rel"));if(!url.isEmpty()&&!blocked.test(url))candidates.add(url);}
                queue.add(candidates);
            }catch(Exception ignored){}
            if(allowed())handler.postDelayed(scanAgain,10000);
        });
    }
    void navigation(String adopted){
        generation++;handler.removeCallbacks(scanAgain);scanning=false;if(adopted!=null&&adopted.contains("#"))adopted=adopted.substring(0,adopted.indexOf('#'));queue.reset(adopted);view=null;page="";unwatch();
    }
    void clear(){navigation(null);backend.clear();}
    void pause(){navigation(null);}
    void destroy(){destroyed=true;clear();}
    private void unwatch(){if(watching&&manager!=null)try{manager.unregisterNetworkCallback(networks);}catch(RuntimeException ignored){}watching=false;manager=null;}
    String status(){
        if(!enabled.getAsBoolean())return "Ausgeschaltet";
        if(!backend.supported())return "Diese WebView unterstützt das Vorladen noch nicht. Bitte Android System WebView bzw. Chrome aktualisieren.";
        if(!wifi.getAsBoolean())return "Wartet auf WLAN ohne Datenbegrenzung";
        if(view==null)return "Wartet auf eine geöffnete Seite";
        return queue.completed+" vorgeladen · "+(queue.waiting()+queue.active())+" ausstehend";
    }
    @android.annotation.SuppressLint({"UnsafeOptInUsageError","RequiresFeature"})
    static final class NativeBackend implements Backend {
        private final Handler handler=new Handler(Looper.getMainLooper());
        private final LinkedHashMap<String,Profile> cached=new LinkedHashMap<>();
        // WebKit 1.14 exposes this official feature but omits it from its StringDef.
        @android.annotation.SuppressLint("WrongConstant")
        private boolean configSupported(){return WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING_CONFIG);}
        @Override public boolean supported(){try{return WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)&&WebViewFeature.isFeatureSupported(WebViewFeature.PROFILE_URL_PREFETCH);}catch(RuntimeException|LinkageError unavailable){return false;}}
        @Override public PreloadQueue.Cancel fetch(WebView view,String url,java.util.function.Consumer<Boolean> done){
            CancellationSignal signal=new CancellationSignal();boolean[] finished={false};
            Runnable timeout=()->{if(!finished[0]){finished[0]=true;signal.cancel();done.accept(false);}};handler.postDelayed(timeout,20000);
            java.util.function.Consumer<Boolean> settle=success->{if(!finished[0]){finished[0]=true;handler.removeCallbacks(timeout);done.accept(success);}};
            try{
                Profile profile=WebViewCompat.getProfile(view);
                if(configSupported())profile.setSpeculativeLoadingConfig(new SpeculativeLoadingConfig.Builder().setMaxPrefetches(64).setPrefetchTtlSeconds(120).build());
                cached.put(url,profile);while(cached.size()>PreloadLinks.MAX_LINKS){String old=cached.keySet().iterator().next();Profile previous=cached.remove(old);clear(previous,old);}
                SpeculativeLoadingParameters parameters=new SpeculativeLoadingParameters.Builder().addAdditionalHeader("User-Agent",view.getSettings().getUserAgentString()).setJavaScriptEnabled(view.getSettings().getJavaScriptEnabled()).build();
                profile.prefetchUrlAsync(url,signal,handler::post,parameters,new OutcomeReceiverCompat<Void,PrefetchException>(){
                    @Override public void onResult(Void result){settle.accept(true);}
                    @Override public void onError(PrefetchException error){settle.accept(false);}
                });
            }catch(RuntimeException|LinkageError error){settle.accept(false);}
            return new PreloadQueue.Cancel(){
                @Override public void cancel(){if(!finished[0]){finished[0]=true;handler.removeCallbacks(timeout);signal.cancel();}}
                @Override public void adopt(){finished[0]=true;handler.removeCallbacks(timeout);}
            };
        }
        private void clear(Profile profile,String url){try{profile.clearPrefetchAsync(url,handler::post,new OutcomeReceiverCompat<Void,PrefetchException>(){@Override public void onResult(Void result){}@Override public void onError(PrefetchException error){}});}catch(RuntimeException|LinkageError ignored){}}
        @Override public void clear(){for(Map.Entry<String,Profile> row:cached.entrySet())clear(row.getValue(),row.getKey());cached.clear();}
    }
}
