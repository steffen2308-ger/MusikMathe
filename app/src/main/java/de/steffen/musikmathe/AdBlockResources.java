package de.steffen.musikmathe;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;

/** Data-only filters: no website cookies, no request log and no downloaded executable code. */
final class AdBlockResources {
    static final String UPDATE_URL = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/light-onlydomains.txt";
    static final String CLEANUP_SCRIPT = "if(window.__bestBrowserAdblock)window.__bestBrowserAdblock.stop();";
    private static final int MAX_BYTES = 4*1024*1024;
    private static volatile boolean loaded;
    private static volatile String problem = "";
    private static String scriptTemplate;
    private static String scriptKey,scriptCache;
    private static long scriptGeneration;
    private static final Object HOST_LOCK = new Object();
    private static final Object SCRIPT_LOCK = new Object();

    private AdBlockResources() { }
    private static AtomicFile cache(Context context) {
        return new AtomicFile(new File(context.getNoBackupFilesDir(),"adblock-hosts.txt"));
    }
    static void preload(Context context) {
        if(loaded)return;
        Context app=context.getApplicationContext();
        Thread thread=new Thread(()->{cosmeticScript(app,java.util.Collections.emptySet());ensureLoaded(app);},"adblock-filters");thread.setDaemon(true);thread.start();
    }
    static byte[] readLimited(InputStream source) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=source.read(buffer))!=-1){
            if(out.size()+n>MAX_BYTES)throw new IOException("Filterliste ist zu groß.");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }
    private static Set<String> validated(byte[] data)throws IOException {
        Set<String> result=AdBlocker.readHosts(new InputStreamReader(new ByteArrayInputStream(data),StandardCharsets.UTF_8));
        if(result.size()<10000)throw new IOException("Die Filterliste ist unvollständig.");
        return result;
    }
    static void ensureLoaded(Context context) {
        if(loaded)return;
        synchronized(HOST_LOCK){
            if(loaded)return;
            try(InputStream in=cache(context).openRead()){
                AdBlocker.useHosts(validated(readLimited(in)));problem="";loaded=true;return;
            }catch(IOException ignored){ /* An invalid cache never replaces the bundled list. */ }
            try(InputStream in=context.getAssets().open("adblock_hosts.txt")){
                AdBlocker.useHosts(validated(readLimited(in)));problem="";
            }catch(IOException error){problem="Die große Filterliste konnte nicht geladen werden; nur der Basisfilter ist aktiv.";}
            loaded=true;
        }
    }
    static String status(){
        return !problem.isEmpty()?problem:loaded?java.text.NumberFormat.getIntegerInstance().format(AdBlocker.hostCount())+" Serverregeln geladen":"Filter werden geladen …";
    }
    static String cosmeticScript(Context context,Set<String> exceptions) {
        synchronized(SCRIPT_LOCK){
            if(scriptTemplate==null){
                try(InputStream js=context.getAssets().open("adblock.js");InputStream data=context.getAssets().open("adblock_cosmetic.json")){
                    scriptTemplate=new String(readLimited(js),StandardCharsets.UTF_8).replace("'__FILTER_DATA__'",new String(readLimited(data),StandardCharsets.UTF_8));
                }catch(IOException error){return CLEANUP_SCRIPT;}
            }
            ArrayList<String> sorted=new ArrayList<>(exceptions);java.util.Collections.sort(sorted);
            String key=new JSONArray(sorted).toString();
            // Bundled CSS rules cannot change during this process; only the exception list varies.
            // The page-visible cache marker contains no exception domains or persistent identifier.
            if(!key.equals(scriptKey)){scriptCache=scriptTemplate.replace("'__SITE_EXCEPTIONS__'",key).replace("'__CONFIG_KEY__'",org.json.JSONObject.quote("cosmetic-v2:"+(++scriptGeneration)));scriptKey=key;}
            return scriptCache;
        }
    }
    static void installUpdate(Context context,byte[] data)throws IOException {
        Set<String> next=validated(data);
        synchronized(HOST_LOCK){
            AtomicFile target=cache(context);FileOutputStream output=null;
            try{
                output=target.startWrite();output.write(data);target.finishWrite(output);
            }catch(IOException error){if(output!=null)target.failWrite(output);throw error;}
            AdBlocker.useHosts(next);problem="";loaded=true;
        }
    }
    static void update(Context context)throws IOException {
        OkHttpClient client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
                .callTimeout(40,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
        Request request=new Request.Builder().url(UPDATE_URL).header("User-Agent","BestBrowser/0.4.6 FilterUpdate").build();
        try(Response response=client.newCall(request).execute()){
            if(response.code()!=200||response.body()==null)throw new IOException("Filterserver ist nicht erreichbar.");
            if(response.body().contentLength()>MAX_BYTES)throw new IOException("Filterliste ist zu groß.");
            installUpdate(context,readLimited(response.body().byteStream()));
        }
    }
}
