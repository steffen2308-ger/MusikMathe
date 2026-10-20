package de.steffen.musikmathe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Source-specific facts, shared by copies of a candidate; no credentials or guessed dimensions. */
final class VideoMetadata {
    private final LinkedHashMap<String,String> values=new LinkedHashMap<>();
    volatile long probedAt;
    synchronized VideoMetadata put(String key,String value){if(value!=null){value=value.trim();if(!value.isEmpty()&&value.length()<=16000)values.put(key,value);}return this;}
    synchronized String get(String key){return values.getOrDefault(key,"");}
    synchronized Map<String,String> fields(){return Collections.unmodifiableMap(new LinkedHashMap<>(values));}
    VideoMetadata merge(VideoMetadata other){if(other!=null)for(Map.Entry<String,String> row:other.fields().entrySet())put(row.getKey(),row.getValue());return this;}
    VideoMetadata fill(VideoMetadata other){if(other!=null)for(Map.Entry<String,String> row:other.fields().entrySet())synchronized(this){if(get(row.getKey()).isEmpty())put(row.getKey(),row.getValue());}return this;}
    VideoMetadata number(String key,double value,String suffix){if(Double.isFinite(value)&&value>0)put(key,decimal(value)+suffix);return this;}
    VideoMetadata duration(double seconds){if(Double.isFinite(seconds)&&seconds>0)put("Dauer",durationText(seconds));return this;}
    VideoMetadata dimensions(int width,int height){if(width>0&&height>0)put("Auflösung",width+" × "+height+" Pixel");else if(height>0)put("Auflösung",height+"p");return this;}
    VideoMetadata size(String key,long bytes){if(bytes>0)put(key,bytes<1000?bytes+" B":bytes<1000000?String.format(Locale.GERMANY,"%.1f kB",bytes/1000.0):bytes<1000000000?String.format(Locale.GERMANY,"%.2f MB",bytes/1000000.0):String.format(Locale.GERMANY,"%.2f GB",bytes/1000000000.0));return this;}
    VideoMetadata format(String mime){if(mime==null)return this;String value=mime.toLowerCase(Locale.ROOT);return put("Format",value.contains("mpegurl")?"HLS":value.contains("dash+xml")?"DASH":value.contains("mp4")?"MP4":value.contains("webm")?"WebM":value.contains("matroska")?"Matroska":value.contains("quicktime")?"QuickTime":value.contains("ogg")?"Ogg":"");}
    static String decimal(double n){return n==Math.rint(n)?Long.toString((long)n):String.format(Locale.GERMANY,"%.2f",n);}
    static String durationText(double seconds){long n=Math.round(seconds);return n>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",n/3600,(n%3600)/60,n%60):String.format(Locale.ROOT,"%d:%02d",n/60,n%60);}
    static VideoMetadata fromPlayer(JSONObject row){
        VideoMetadata info=new VideoMetadata();if(row==null)return info;
        info.dimensions(row.optInt("width"),row.optInt("height")).duration(row.optDouble("duration"));
        if(row.optBoolean("live"))info.put("Übertragung","Livestream");
        info.format(row.optString("mime")).put("MIME-Typ",row.optString("mime")).put("Qualitätsangabe",row.optString("quality")).put("Tonspur",row.optString("audio")).put("Untertitel",row.optString("subtitles"));
        info.size(row.optString("mime").contains("mpegurl")||row.optString("mime").contains("dash+xml")?"Manifestgröße":"Dateigröße",row.optLong("size")).number("Bildrate",row.optDouble("fps")," fps").number("Bitrate",row.optDouble("bitrate")/1000," kbit/s");return info;
    }
    String summary(boolean compact){
        Map<String,String> fields=fields();StringBuilder out=new StringBuilder();
        line(out,"Auflösung",fields.getOrDefault("Auflösung",fields.containsKey("Qualitätsstufen")?"Adaptiv":"nicht angegeben"));
        line(out,"Dauer","Livestream".equals(fields.get("Übertragung"))?"Livestream":fields.getOrDefault("Dauer","nicht angegeben"));
        if(!compact)for(String key:new String[]{"Qualitätsstufen","Dateigröße","Video-Dateigröße","Audio-Dateigröße","Bildrate","Qualitätsangabe","Format","MIME-Typ","Video-Codec","Audio-Codec","Codecs","Bitrate","Audio-Bitrate","Tonspur","Tonspuren","Sprache","Audiokanäle","Abtastrate","Untertitel","Zeitfenster","Autor / Kanal"})if(fields.containsKey(key))line(out,key,fields.get(key));
        return out.toString();
    }
    static void line(StringBuilder out,String key,String value){if(out.length()>0)out.append('\n');out.append(key).append(": ").append(value);}
    String details(VideoCatalog.Item item){
        Map<String,String> fields=fields();StringBuilder out=new StringBuilder(item.label());out.append("\n\n").append(summary(false));
        for(Map.Entry<String,String> row:fields.entrySet())if(!summaryKeys().contains(row.getKey())){out.append('\n');line(out,row.getKey(),row.getValue());}
        if(!fields.containsKey("Format"))line(out,"Format / Streamtyp",item.type);
        line(out,"Quelle",Address.host(item.url));String filename=android.net.Uri.parse(item.url).getLastPathSegment();if(filename!=null&&!filename.isEmpty())line(out,"Dateiname / Pfadende",filename);
        if(!item.youtubeId.isEmpty())line(out,"YouTube-ID",item.youtubeId);
        if(!item.audioUrl.isEmpty())line(out,"Speicherung","Video und separate Tonspur werden zusammengeführt");
        out.append("\n\nEs werden die Angaben der Seite und der Quelle angezeigt. Nicht bereitgestellte Werte werden nicht geschätzt.");return out.toString();
    }
    private static java.util.Set<String> summaryKeys(){return java.util.Set.of("Auflösung","Dauer","Qualitätsstufen","Dateigröße","Video-Dateigröße","Audio-Dateigröße","Bildrate","Qualitätsangabe","Format","MIME-Typ","Video-Codec","Audio-Codec","Codecs","Bitrate","Audio-Bitrate","Tonspur","Tonspuren","Sprache","Audiokanäle","Abtastrate","Untertitel","Zeitfenster","Autor / Kanal");}
}
