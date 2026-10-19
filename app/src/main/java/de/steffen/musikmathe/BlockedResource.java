package de.steffen.musikmathe;

import android.webkit.WebResourceResponse;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collections;

/** Abort the intercepted resource stream instead of reporting an empty successful HTTP response. */
final class BlockedResource {
    private BlockedResource() { }
    static WebResourceResponse response(){
        InputStream denied=new InputStream(){
            @Override public int available()throws IOException{throw new IOException("Blocked by BestBrowser");}
            @Override public int read()throws IOException{throw new IOException("Blocked by BestBrowser");}
            @Override public int read(byte[] buffer,int offset,int length)throws IOException{throw new IOException("Blocked by BestBrowser");}
        };
        return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",Collections.singletonMap("Cache-Control","no-store"),denied);
    }
}
