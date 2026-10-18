package de.steffen.musikmathe;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

/** On-demand, anonymous extraction. No browser cookies, signed URLs or results are persisted. */
final class YouTubeDownloadResolver {
    static final String AGENT="Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0";
    static final class Result {
        final String id,title;
        final List<VideoCatalog.Item> items;
        Result(String id,String title,List<VideoCatalog.Item> items){this.id=id;this.title=title;this.items=items;}
    }
    private final OkHttpClient http=new OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).followSslRedirects(false).build();
    private volatile boolean cancelled;
    private final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);
    void cancel(){cancelled=true;http.dispatcher().cancelAll();}
    static String errorMessage(Throwable error){
        String name=error.getClass().getSimpleName();
        if(name.contains("NotBot")||name.contains("ReCaptcha")||String.valueOf(error.getMessage()).contains("HTTP 429"))return "YouTube blockiert die Quellenabfrage mit einer Sicherheitsprüfung. Versuche es später erneut oder über eine andere Internetverbindung.";
        if(name.contains("AgeRestricted")||name.contains("PrivateContent")||name.contains("PaidContent"))return "Dieses Video verlangt eine Anmeldung, Altersfreigabe oder Bezahlung. Der anonyme Download ist nicht verfügbar.";
        if(name.contains("Geographic"))return "Dieses Video ist für die aktuelle Internetverbindung regional gesperrt.";
        if(name.contains("ContentNotAvailable"))return "YouTube stellt dieses Video derzeit nicht für den Download bereit.";
        if(error instanceof IOException)return "Die YouTube-Downloadquelle ist nicht erreichbar oder wurde abgelehnt. Prüfe die Verbindung und versuche es erneut.";
        return "Die YouTube-Downloadquelle konnte nicht aufgelöst werden. YouTube hat möglicherweise seine Schnittstelle geändert.";
    }
    Result resolve(String page)throws Exception{
        String id=YouTubeSources.videoId(page);if(id.isEmpty())throw new IllegalArgumentException("Kein YouTube-Link");
        // NewPipe maintains process-global client state; callers use a single background worker.
        synchronized(YouTubeDownloadResolver.class){
            check();NewPipe.init(new Transport(),new Localization("de","DE"));
            StreamExtractor extractor=ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v="+id);extractor.fetchPage();check();
            String title=extractor.getName();Map<String,String> headers=new LinkedHashMap<>();headers.put("User-Agent",AGENT);headers.put("Referer","https://www.youtube.com/");
            List<VideoCatalog.Item> items=new ArrayList<>();
            String hls=extractor.getHlsUrl();
            if(usable(hls,true))items.add(new VideoCatalog.Item(hls,"HLS",headers,"",id,"YouTube · automatische Qualität · Bild und Ton\n"+title));
            // An accessible HLS stream is complete and avoids offering stale adaptive URLs.
            if(!items.isEmpty())return new Result(id,title,items);
            List<VideoStream> combined=new ArrayList<>(extractor.getVideoStreams());combined.sort(Comparator.comparingInt(VideoStream::getHeight).reversed());
            for(VideoStream v:combined){check();if(v.isUrl()&&v.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP&&usable(v.getContent(),false)){
                items.add(new VideoCatalog.Item(v.getContent(),format(v),headers,"",id,"YouTube · "+v.getResolution()+" · Bild und Ton\n"+title));break;
            }}
            if(!items.isEmpty())return new Result(id,title,items);
            List<AudioStream> audios=new ArrayList<>(extractor.getAudioStreams());audios.sort((a,b)->{
                boolean am=a.getFormat()!=null&&a.getFormat().getMimeType().contains("mp4"),bm=b.getFormat()!=null&&b.getFormat().getMimeType().contains("mp4");return am!=bm?(am?-1:1):Integer.compare(b.getAverageBitrate(),a.getAverageBitrate());});
            String audio="";for(AudioStream a:audios){check();if(a.isUrl()&&a.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP&&usable(a.getContent(),false)){audio=a.getContent();break;}}
            if(!audio.isEmpty()){
                List<VideoStream> videos=new ArrayList<>(extractor.getVideoOnlyStreams());videos.sort((a,b)->{
                    boolean aa=a.getHeight()<=1080,bb=b.getHeight()<=1080;return aa!=bb?(aa?-1:1):Integer.compare(b.getHeight(),a.getHeight());});
                for(VideoStream v:videos){check();if(v.isUrl()&&v.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP&&usable(v.getContent(),false)){
                    items.add(new VideoCatalog.Item(v.getContent(),format(v),headers,audio,id,"YouTube · "+v.getResolution()+" · Bild und Ton\n"+title));break;
                }}
            }
            if(items.isEmpty())throw new IOException("Keine erreichbare Videoquelle");return new Result(id,title,items);
        }
    }
    private static String format(VideoStream stream){return stream.getFormat()!=null&&stream.getFormat().getMimeType().contains("webm")?"WebM":"MP4";}
    private static byte[] read(java.io.InputStream input,int limit)throws IOException{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while(out.size()<limit&&(n=input.read(buffer,0,Math.min(buffer.length,limit-out.size())))!=-1)out.write(buffer,0,n);return out.toByteArray();}
    private void check()throws IOException{if(cancelled||Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("Quellensuche beendet");}
    private boolean usable(String url,boolean manifest)throws IOException{
        check();if(url==null||!url.startsWith("https://"))return false;
        okhttp3.Request request=new okhttp3.Request.Builder().url(url).header("User-Agent",AGENT).header("Referer","https://www.youtube.com/").header("Range","bytes=0-1023").build();
        try(okhttp3.Response response=http.newCall(request).execute()){
            if(!response.isSuccessful()||response.body()==null)return false;
            byte[] prefix=read(response.body().byteStream(),1024);
            if(manifest)return new String(prefix,StandardCharsets.UTF_8).trim().startsWith("#EXTM3U");
            String mime=response.header("Content-Type","");return prefix.length>32&&(mime.startsWith("video/")||mime.startsWith("audio/")||mime.contains("octet-stream"));
        }catch(IOException error){check();return false;}
    }
    private final class Transport extends Downloader {
        @Override public Response execute(Request request)throws IOException{
            check();if(!request.url().startsWith("https://"))throw new IOException("Nur HTTPS-Quellen");
            okhttp3.Request.Builder builder=new okhttp3.Request.Builder().url(request.url()).header("User-Agent",AGENT);
            for(Map.Entry<String,List<String>> header:request.headers().entrySet()){
                if(header.getKey().equalsIgnoreCase("Accept-Encoding"))continue;
                builder.removeHeader(header.getKey());for(String value:header.getValue())builder.addHeader(header.getKey(),value);
            }
            byte[] bytes=request.dataToSend();RequestBody body=bytes==null?null:RequestBody.create(null,bytes);
            builder.method(request.httpMethod(),body);
            try(okhttp3.Response response=http.newCall(builder.build()).execute()){
                ResponseBody content=response.body();String text=content==null?"":new String(read(content.byteStream(),8*1024*1024),StandardCharsets.UTF_8);
                if(response.code()==429)throw new IOException("YouTube HTTP 429");
                return new Response(response.code(),response.message(),response.headers().toMultimap(),text,response.request().url().toString());
            }
        }
    }
}
