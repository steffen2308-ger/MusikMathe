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
    private final Map<String,Long> sizes=new LinkedHashMap<>();
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
            if(!items.isEmpty())return described(extractor,id,title,items);
            List<VideoStream> combined=new ArrayList<>(extractor.getVideoStreams());combined.sort(Comparator.comparingInt(VideoStream::getHeight).reversed());
            for(VideoStream v:combined){check();if(v.isUrl()&&v.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP&&usable(v.getContent(),false)){
                VideoCatalog.Item item=new VideoCatalog.Item(v.getContent(),format(v),headers,"",id,"YouTube · "+v.getResolution()+" · Bild und Ton\n"+title);videoInfo(item,v);items.add(item);break;
            }}
            if(!items.isEmpty())return described(extractor,id,title,items);
            List<AudioStream> audios=new ArrayList<>(extractor.getAudioStreams());audios.sort((a,b)->{
                boolean am=a.getFormat()!=null&&a.getFormat().getMimeType().contains("mp4"),bm=b.getFormat()!=null&&b.getFormat().getMimeType().contains("mp4");return am!=bm?(am?-1:1):Integer.compare(b.getAverageBitrate(),a.getAverageBitrate());});
            AudioStream chosenAudio=null;String audio="";for(AudioStream a:audios){check();if(a.isUrl()&&a.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP&&usable(a.getContent(),false)){audio=a.getContent();chosenAudio=a;break;}}
            if(!audio.isEmpty()){
                List<VideoStream> videos=new ArrayList<>(extractor.getVideoOnlyStreams());videos.sort((a,b)->{
                    boolean aa=a.getHeight()<=1080,bb=b.getHeight()<=1080;return aa!=bb?(aa?-1:1):Integer.compare(b.getHeight(),a.getHeight());});
                for(VideoStream v:videos){check();if(v.isUrl()&&v.getDeliveryMethod()==DeliveryMethod.PROGRESSIVE_HTTP&&usable(v.getContent(),false)){
                    VideoCatalog.Item item=new VideoCatalog.Item(v.getContent(),format(v),headers,audio,id,"YouTube · "+v.getResolution()+" · Bild und Ton\n"+title);videoInfo(item,v);audioInfo(item,chosenAudio);items.add(item);break;
                }}
            }
            if(items.isEmpty())throw new IOException("Keine erreichbare Videoquelle");return described(extractor,id,title,items);
        }
    }
    private interface Fact {String get()throws Exception;}
    private static void fact(VideoMetadata info,String key,Fact fact){try{info.put(key,fact.get());}catch(Exception unavailable){/* Optional metadata never prevents an available download. */}}
    private Result described(StreamExtractor extractor,String id,String title,List<VideoCatalog.Item> items){
        VideoMetadata common=new VideoMetadata();
        try{common.duration(extractor.getLength());String kind=extractor.getStreamType().name();if(kind.equals("LIVE_STREAM")||kind.equals("AUDIO_LIVE_STREAM"))common.put("Übertragung","Livestream");}catch(Exception ignored){}
        fact(common,"Autor / Kanal",extractor::getUploaderName);fact(common,"Veröffentlichung",extractor::getTextualUploadDate);
        fact(common,"Beschreibung",()->android.text.Html.fromHtml(extractor.getDescription().getContent()).toString());
        fact(common,"Kategorie",extractor::getCategory);fact(common,"Lizenz",extractor::getLicence);
        fact(common,"Sprache",()->extractor.getLanguageInfo()==null?"":extractor.getLanguageInfo().toLanguageTag());
        fact(common,"Schlagwörter",()->String.join(", ",extractor.getTags()));
        fact(common,"Aufrufe",()->extractor.getViewCount()>=0?Long.toString(extractor.getViewCount()):"");
        fact(common,"Gefällt mir",()->extractor.getLikeCount()>=0?Long.toString(extractor.getLikeCount()):"");
        fact(common,"Untertitel",()->{List<String> labels=new ArrayList<>();for(org.schabi.newpipe.extractor.stream.SubtitlesStream subtitle:extractor.getSubtitlesDefault())labels.add(subtitle.getDisplayLanguageName()+" · "+subtitle.getLanguageTag());return String.join(", ",labels);});
        for(VideoCatalog.Item item:items){item.metadata.merge(common).size(item.type.equals("HLS")||item.type.equals("DASH")?"Manifestgröße":item.audioUrl.isEmpty()?"Dateigröße":"Video-Dateigröße",sizes.getOrDefault(item.url,-1L));if(!item.audioUrl.isEmpty())item.metadata.size("Audio-Dateigröße",sizes.getOrDefault(item.audioUrl,-1L));}return new Result(id,title,items);
    }
    private static void videoInfo(VideoCatalog.Item item,VideoStream stream){item.metadata.dimensions(stream.getWidth(),stream.getHeight()).number("Bildrate",stream.getFps()," fps").number("Bitrate",stream.getBitrate()/1000.0," kbit/s").put("Video-Codec",stream.getCodec()).put("Qualitätsangabe",stream.getResolution()).put("Tonspur",stream.isVideoOnly()?"Separate Tonspur":"In der Videodatei");if(stream.getFormat()!=null)item.metadata.format(stream.getFormat().getMimeType()).put("MIME-Typ",stream.getFormat().getMimeType());}
    private static void audioInfo(VideoCatalog.Item item,AudioStream stream){if(stream==null)return;item.metadata.put("Audio-Codec",stream.getCodec()).number("Audio-Bitrate",stream.getBitrate()/1000.0," kbit/s").put("Tonspur",stream.getAudioTrackName());if(stream.getAudioLocale()!=null)item.metadata.put("Sprache",stream.getAudioLocale().toLanguageTag());if(stream.getFormat()!=null)item.metadata.put("Audio-MIME-Typ",stream.getFormat().getMimeType());}
    private static String format(VideoStream stream){return stream.getFormat()!=null&&stream.getFormat().getMimeType().contains("webm")?"WebM":"MP4";}
    private static byte[] read(java.io.InputStream input,int limit)throws IOException{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while(out.size()<limit&&(n=input.read(buffer,0,Math.min(buffer.length,limit-out.size())))!=-1)out.write(buffer,0,n);return out.toByteArray();}
    private void check()throws IOException{if(cancelled||Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new IOException("Quellensuche beendet");}
    private boolean usable(String url,boolean manifest)throws IOException{
        check();if(url==null||!url.startsWith("https://"))return false;
        okhttp3.Request request=new okhttp3.Request.Builder().url(url).header("User-Agent",AGENT).header("Referer","https://www.youtube.com/").header("Range","bytes=0-1023").build();
        try(okhttp3.Response response=http.newCall(request).execute()){
            if(!response.isSuccessful()||response.body()==null)return false;
            try{String range=response.header("Content-Range","");long size=range.matches("bytes .*/[0-9]+")?Long.parseLong(range.substring(range.lastIndexOf('/')+1)):response.code()==200?Long.parseLong(response.header("Content-Length","-1")):-1;if(size>0)sizes.put(url,size);}catch(NumberFormatException ignored){}
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
