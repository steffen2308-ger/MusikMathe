package de.steffen.musikmathe;

import android.net.Uri;
import android.webkit.CookieManager;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Recomputes cookies and strips origin-bound credentials at every redirect and segment. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
final class VideoHttpSource extends BaseDataSource {
    private final String sourceOrigin;
    private final Map<String,String> headers;
    private HttpURLConnection connection;
    private InputStream input;
    private Uri current;
    private boolean started;
    private long remaining;
    VideoHttpSource(String source,Map<String,String> headers){super(true);sourceOrigin=VideoCatalog.origin(source);this.headers=VideoCatalog.safeHeaders(headers);}
    static boolean originBound(String header){return "Authorization".equalsIgnoreCase(header)||"Origin".equalsIgnoreCase(header);}
    @Override public long open(DataSpec spec)throws IOException{
        transferInitializing(spec);String url=spec.uri.toString();
        if(spec.httpMethod!=DataSpec.HTTP_METHOD_GET)throw new IOException("Nur GET-Videostreams werden unterstützt.");
        try{
            for(int redirects=0;redirects<9;redirects++){
                if(!Address.isWeb(url))throw new IOException("Keine HTTP-Videoquelle.");
                current=Uri.parse(url);connection=(HttpURLConnection)new URL(url).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept-Encoding","identity");connection.setRequestProperty("User-Agent","MusikMathe/0.3");
                for(Map.Entry<String,String> row:headers.entrySet())if(!originBound(row.getKey())||sourceOrigin.equals(VideoCatalog.origin(url))){
                    String value=row.getValue();
                    if("Referer".equalsIgnoreCase(row.getKey())&&!sourceOrigin.equals(VideoCatalog.origin(url))&&Address.isWeb(value))value=Uri.parse(value).buildUpon().path("/").clearQuery().fragment(null).build().toString();
                    connection.setRequestProperty(row.getKey(),value);
                }
                String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)connection.setRequestProperty("Cookie",cookie);
                if(spec.position>0||spec.length!=C.LENGTH_UNSET)connection.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":spec.position+spec.length-1));
                int status=connection.getResponseCode();
                for(Map.Entry<String,List<String>> row:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(row.getKey()))for(String value:row.getValue())CookieManager.getInstance().setCookie(url,value);
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Weiterleitung ohne Ziel.");String next=new URL(new URL(url),location).toString();
                    if(url.startsWith("https://")&&next.startsWith("http://"))throw new IOException("Unsichere Video-Weiterleitung abgelehnt.");connection.disconnect();connection=null;url=next;continue;
                }
                if(status<200||status>=300)throw new IOException("Videoquelle antwortet mit HTTP "+status+". Die Adresse ist eventuell abgelaufen.");
                input=connection.getInputStream();long skip=status==200?spec.position:0;
                while(skip>0){long amount=input.skip(skip);if(amount<=0){if(input.read()==-1)throw new IOException("Videodatei ist zu kurz.");amount=1;}skip-=amount;}
                long length=C.LENGTH_UNSET;try{length=Long.parseLong(connection.getHeaderField("Content-Length"));if(status==200)length=Math.max(0,length-spec.position);}catch(Exception ignored){}
                remaining=spec.length!=C.LENGTH_UNSET?spec.length:length;started=true;transferStarted(spec);return remaining;
            }
            throw new IOException("Zu viele Video-Weiterleitungen.");
        }catch(IOException|RuntimeException error){close();throw error;}
    }
    @Override public int read(byte[] buffer,int offset,int length)throws IOException{
        if(length==0)return 0;if(remaining==0)return C.RESULT_END_OF_INPUT;
        int count=input.read(buffer,offset,remaining==C.LENGTH_UNSET?length:(int)Math.min(length,remaining));
        if(count<0){if(remaining>0)throw new java.io.EOFException("Videodownload ist unvollständig.");return C.RESULT_END_OF_INPUT;}
        if(remaining!=C.LENGTH_UNSET)remaining-=count;bytesTransferred(count);return count;
    }
    @Override public Uri getUri(){return current;}
    @Override public Map<String,List<String>> getResponseHeaders(){return connection==null?Collections.emptyMap():connection.getHeaderFields();}
    @Override public void close()throws IOException{
        try{if(input!=null)input.close();}finally{input=null;if(connection!=null)connection.disconnect();connection=null;if(started){started=false;transferEnded();}}
    }
}
