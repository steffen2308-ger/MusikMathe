package de.steffen.musikmathe;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Bundled, immutable code only. Page data and credentials never enter this process cache. */
final class BrowserScripts {
    private static final ConcurrentHashMap<String,String> CODE=new ConcurrentHashMap<>();
    private BrowserScripts(){}
    static String read(Context context,String name,String fallback){
        String cached=CODE.get(name);if(cached!=null)return cached;
        try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            String value=new String(out.toByteArray(),StandardCharsets.UTF_8);String previous=CODE.putIfAbsent(name,value);return previous==null?value:previous;
        }catch(IOException ignored){return fallback;}
    }
}
