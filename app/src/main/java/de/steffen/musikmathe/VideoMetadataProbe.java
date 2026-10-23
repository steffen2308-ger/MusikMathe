package de.steffen.musikmathe;

import android.net.Uri;
import android.webkit.CookieManager;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.Representation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;

/** Reads HEAD metadata or one bounded manifest, never follows media/segment references. */
@android.annotation.SuppressLint("UnsafeOptInUsageError")
final class VideoMetadataProbe {
    static final int LIMIT=1024*1024;
    static VideoMetadata read(VideoCatalog.Item item,OkHttpClient http)throws IOException{
        boolean manifest="HLS".equals(item.type)||"DASH".equals(item.type);String url=item.url;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        for(int redirects=0;redirects<6;redirects++){
            long remaining=deadline-System.nanoTime();if(remaining<=0||Thread.currentThread().isInterrupted())throw new IOException("Metadata timeout");
            if(!Address.isWeb(url))throw new IOException("Invalid source");
            okhttp3.Request.Builder request=new okhttp3.Request.Builder().url(url).method(manifest?"GET":"HEAD",null).header("Accept-Encoding","identity");
            for(Map.Entry<String,String> header:VideoCatalog.safeHeaders(item.headers).entrySet()){
                if(VideoHttpSource.originBound(header.getKey())&&!VideoCatalog.origin(item.url).equals(VideoCatalog.origin(url)))continue;
                String value=header.getValue();if("Referer".equalsIgnoreCase(header.getKey())&&!VideoCatalog.origin(item.url).equals(VideoCatalog.origin(url))&&Address.isWeb(value))value=Uri.parse(value).buildUpon().path("/").clearQuery().fragment(null).build().toString();request.header(header.getKey(),value);
            }
            String cookie=CookieManager.getInstance().getCookie(url);if(cookie!=null)request.header("Cookie",cookie);
            okhttp3.Call call=http.newCall(request.build());call.timeout().timeout(remaining,TimeUnit.NANOSECONDS);
            try(okhttp3.Response response=call.execute()){
                if(response.isRedirect()){String location=response.header("Location");okhttp3.HttpUrl next=location==null?null:response.request().url().resolve(location);if(next==null||(url.startsWith("https://")&&!next.isHttps()))throw new IOException("Unsafe redirect");url=next.toString();continue;}
                if(!response.isSuccessful())return new VideoMetadata();
                String mime=response.header("Content-Type","");VideoMetadata info=new VideoMetadata();if(!mime.contains("octet-stream"))info.format(mime).put("MIME-Typ",mime);
                long size=-1;String range=response.header("Content-Range","");try{if(range.matches("bytes .*/[0-9]+"))size=Long.parseLong(range.substring(range.lastIndexOf('/')+1));else if(response.code()==200&&response.header("Content-Encoding","identity").equalsIgnoreCase("identity"))size=Long.parseLong(response.header("Content-Length","-1"));}catch(NumberFormatException ignored){}
                info.size(manifest?"Manifestgröße":item.audioUrl.isEmpty()?"Dateigröße":"Video-Dateigröße",size);
                if(manifest&&response.body()!=null){if(response.body().contentLength()>LIMIT)throw new IOException("Manifest too large");byte[] bytes=bounded(response.body().byteStream());info.merge(parse(item.type,url,bytes));}return info;
            }
        }throw new IOException("Too many redirects");
    }
    static byte[] bounded(InputStream input)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||out.size()+n>LIMIT)throw new IOException("Manifest too large");out.write(buffer,0,n);}return out.toByteArray();}
    static VideoMetadata parse(String type,String url,byte[] bytes)throws IOException{
        if(bytes.length>LIMIT)throw new IOException("Manifest too large");VideoMetadata info=new VideoMetadata();Set<String> qualities=new LinkedHashSet<>(),audio=new LinkedHashSet<>(),text=new LinkedHashSet<>();
        if("HLS".equals(type)){
            HlsPlaylist playlist=new HlsPlaylistParser().parse(Uri.parse(url),new ByteArrayInputStream(bytes));
            if(playlist instanceof HlsMediaPlaylist){HlsMediaPlaylist media=(HlsMediaPlaylist)playlist;if(media.hasEndTag)info.duration(media.durationUs/1000000.0);else info.put("Übertragung","Livestream").put("Zeitfenster",VideoMetadata.durationText(media.durationUs/1000000.0));}
            else if(playlist instanceof HlsMultivariantPlaylist){HlsMultivariantPlaylist master=(HlsMultivariantPlaylist)playlist;for(HlsMultivariantPlaylist.Variant variant:master.variants)add(qualities,quality(variant.format));for(HlsMultivariantPlaylist.Rendition rendition:master.audios)add(audio,audio(rendition.format));for(HlsMultivariantPlaylist.Rendition rendition:master.subtitles)add(text,audio(rendition.format));if(master.muxedAudioFormat!=null)add(audio,audio(master.muxedAudioFormat));if(master.variants.size()==1)format(info,master.variants.get(0).format);}
        }else if("DASH".equals(type)){
            DashManifest manifest=new DashManifestParser().parse(Uri.parse(url),new ByteArrayInputStream(bytes));if(manifest.dynamic)info.put("Übertragung","Livestream");else info.duration(manifest.durationMs/1000.0);
            for(int p=0;p<manifest.getPeriodCount();p++)for(AdaptationSet set:manifest.getPeriod(p).adaptationSets)for(Representation representation:set.representations){Format f=representation.format;if(set.type==C.TRACK_TYPE_VIDEO)add(qualities,quality(f));else if(set.type==C.TRACK_TYPE_AUDIO)add(audio,audio(f));else if(set.type==C.TRACK_TYPE_TEXT)add(text,audio(f));if(f.drmInitData!=null)info.put("Kopierschutz","Vom Stream signalisiert");}
        }
        info.put("Qualitätsstufen",String.join("\n",qualities)).put("Tonspuren",String.join("\n",audio)).put("Untertitel",String.join("\n",text));return info;
    }
    private static void add(Set<String> out,String value){if(!value.isEmpty()&&out.size()<60)out.add(value);}
    private static String quality(Format f){StringBuilder out=new StringBuilder();if(f.width>0&&f.height>0)out.append(f.width).append(" × ").append(f.height);if(f.frameRate>0)part(out,VideoMetadata.decimal(f.frameRate)+" fps");if(f.bitrate>0)part(out,VideoMetadata.decimal(f.bitrate/1000.0)+" kbit/s");if(f.codecs!=null)part(out,f.codecs);return out.toString();}
    private static String audio(Format f){StringBuilder out=new StringBuilder();if(f.label!=null)part(out,f.label);if(f.language!=null)part(out,f.language);if(f.codecs!=null)part(out,f.codecs);if(f.channelCount>0)part(out,f.channelCount+" Kanäle");if(f.sampleRate>0)part(out,f.sampleRate+" Hz");if(f.bitrate>0)part(out,VideoMetadata.decimal(f.bitrate/1000.0)+" kbit/s");return out.toString();}
    private static void part(StringBuilder out,String value){if(out.length()>0)out.append(" · ");out.append(value);}
    private static void format(VideoMetadata info,Format f){info.dimensions(f.width,f.height).number("Bildrate",f.frameRate," fps").number("Bitrate",f.bitrate/1000.0," kbit/s").put("Codecs",f.codecs);}
}
