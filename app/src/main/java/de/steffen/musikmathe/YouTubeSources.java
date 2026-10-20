package de.steffen.musikmathe;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Player data is used as provided by the current YouTube session; no signature guessing. */
final class YouTubeSources {
    static final class Result {
        final String videoId,title,status,reason;
        final boolean playerData;
        final List<VideoCatalog.Item> items;
        final VideoMetadata metadata;
        Result(String id,String title,String status,String reason,boolean playerData,List<VideoCatalog.Item> items,VideoMetadata metadata){this.videoId=id;this.title=title;this.status=status;this.reason=reason;this.playerData=playerData;this.items=items;this.metadata=metadata;}
    }
    static boolean originAllowed(String url){Uri u=Uri.parse(url);String h=u.getHost();return "https".equalsIgnoreCase(u.getScheme())&&h!=null&&(h.equals("youtube.com")||h.endsWith(".youtube.com")||h.equals("youtube-nocookie.com")||h.endsWith(".youtube-nocookie.com"));}
    static String videoId(String url){try{Uri u=Uri.parse(url);String host=u.getHost();if(host==null)return "";String id="";if(host.equals("youtu.be"))id=u.getLastPathSegment();else if(host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("youtube-nocookie.com")||host.endsWith(".youtube-nocookie.com")){id=u.getQueryParameter("v");if(id==null){List<String> p=u.getPathSegments();if(p.size()>=2&&(p.get(0).equals("shorts")||p.get(0).equals("embed")||p.get(0).equals("live")))id=p.get(1);}}return id!=null&&id.matches("[A-Za-z0-9_-]{11}")?id:"";}catch(Exception e){return "";}}
    static String script(Context context){try(java.io.InputStream in=context.getAssets().open("youtube.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "JSON.stringify({})";}}
    private static boolean mediaUrl(String url){try{Uri u=Uri.parse(url);String h=u.getHost();return Address.isWeb(url)&&h!=null&&(h.equals("googlevideo.com")||h.endsWith(".googlevideo.com")||h.equals("youtube.com")||h.endsWith(".youtube.com"));}catch(Exception e){return false;}}
    static String completeObservedUrl(String value){
        if(!mediaUrl(value))return "";Uri uri=Uri.parse(value);if(uri.getQueryParameter("sq")!=null)return "";
        if(uri.getQueryParameter("range")==null)return value;
        String signed=","+uri.getQueryParameter("sparams")+","+uri.getQueryParameter("lsparams")+",";
        if(signed.contains(",range,"))return "";
        Uri.Builder out=uri.buildUpon().clearQuery();for(String key:uri.getQueryParameterNames())if(!key.equals("range")&&!key.equals("rn")&&!key.equals("rbuf"))for(String item:uri.getQueryParameters(key))out.appendQueryParameter(key,item);return out.build().toString();
    }
    private static String direct(JSONObject format){
        String url=format.optString("url");if(mediaUrl(url))return url;
        String raw=format.optString("cipher");if(raw.isEmpty())return "";
        try{Uri cipher=Uri.parse("https://cipher.invalid/?"+raw);if(cipher.getQueryParameter("s")!=null)return "";url=cipher.getQueryParameter("url");if(!mediaUrl(url))return "";String signature=cipher.getQueryParameter("sig");if(signature==null)signature=cipher.getQueryParameter("signature");String key=cipher.getQueryParameter("sp");if(signature!=null)url=Uri.parse(url).buildUpon().appendQueryParameter(key==null?"signature":key,signature).build().toString();return url;}catch(Exception e){return "";}
    }
    static Result parse(JSONObject data,Map<String,String> headers,List<String> network)throws Exception{
        String id=data.optString("videoId"),title=data.optString("title","YouTube-Video");if(!id.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException("Kein YouTube-Video");
        Map<Integer,String> observed=new LinkedHashMap<>();ArrayList<String> all=new ArrayList<>(network);JSONArray found=data.optJSONArray("observed");if(found!=null)for(int i=0;i<Math.min(found.length(),100);i++)all.add(found.optString(i));
        for(String raw:all){String url=completeObservedUrl(raw);if(url.isEmpty())continue;try{String tag=Uri.parse(url).getQueryParameter("itag");if(tag!=null)observed.put(Integer.parseInt(tag),url);}catch(Exception ignored){}}
        ArrayList<JSONObject> video=new ArrayList<>(),audio=new ArrayList<>(),combined=new ArrayList<>();JSONArray formats=data.optJSONArray("formats");
        if(formats!=null)for(int i=0;i<Math.min(formats.length(),100);i++){JSONObject f=formats.optJSONObject(i);if(f==null||f.optBoolean("drm"))continue;String base=direct(f),identity=f.optString("url"),url=observed.get(f.optInt("itag"));
            if(identity.isEmpty()&&!f.optString("cipher").isEmpty())identity=Uri.parse("https://cipher.invalid/?"+f.optString("cipher")).getQueryParameter("url");
            if(url!=null&&identity!=null&&!identity.isEmpty()){String a=Uri.parse(identity).getQueryParameter("id"),b=Uri.parse(url).getQueryParameter("id");if(a==null||!a.equals(b))url=null;}
            if(url==null)url=base;if(url.isEmpty())continue;JSONObject copy=new JSONObject(f.toString());copy.put("resolved",url);String mime=f.optString("mimeType").toLowerCase(Locale.ROOT);
            if(mime.startsWith("audio/"))audio.add(copy);else if(mime.startsWith("video/")){boolean sound=!f.optString("audioQuality").isEmpty()||!f.optString("audioSampleRate").isEmpty()||mime.contains("mp4a")||mime.contains("vorbis")||mime.contains("opus");if(sound)combined.add(copy);else video.add(copy);}}
        java.util.Set<Integer> known=new java.util.HashSet<>();if(formats!=null)for(int i=0;i<formats.length();i++)if(formats.optJSONObject(i)!=null)known.add(formats.getJSONObject(i).optInt("itag"));
        for(Map.Entry<Integer,String> row:observed.entrySet())if(!known.contains(row.getKey())){
            Uri uri=Uri.parse(row.getValue());String mime=uri.getQueryParameter("mime");if(mime==null)continue;
            JSONObject f=new JSONObject().put("resolved",row.getValue()).put("mimeType",mime).put("qualityLabel","Abgespielte Qualität");
            if(mime.startsWith("audio/"))audio.add(f);else if(mime.startsWith("video/")){if(row.getKey()==18||row.getKey()==22||row.getKey()==37||row.getKey()==38)combined.add(f);else video.add(f);}
        }
        Comparator<JSONObject> quality=(a,b)->{int h=Integer.compare(b.optInt("height"),a.optInt("height"));return h!=0?h:Integer.compare(b.optInt("bitrate"),a.optInt("bitrate"));};Collections.sort(video,quality);Collections.sort(combined,quality);
        Collections.sort(audio,(a,b)->{JSONObject at=a.optJSONObject("audioTrack"),bt=b.optJSONObject("audioTrack");boolean ad=at!=null&&at.optBoolean("audioIsDefault"),bd=bt!=null&&bt.optBoolean("audioIsDefault");if(ad!=bd)return ad?-1:1;boolean am=a.optString("mimeType").startsWith("audio/mp4"),bm=b.optString("mimeType").startsWith("audio/mp4");if(am!=bm)return am?-1:1;return Integer.compare(b.optInt("bitrate"),a.optInt("bitrate"));});
        ArrayList<VideoCatalog.Item> items=new ArrayList<>();for(JSONObject f:combined)items.add(item(f,null,id,title,headers,data));
        if(!audio.isEmpty())for(JSONObject f:video)if(items.size()<16)items.add(item(f,audio.get(0),id,title,headers,data));
        String hls=data.optString("hls"),dash=data.optString("dash");if(mediaUrl(hls))items.add(new VideoCatalog.Item(hls,"HLS",headers,"",id,"YouTube · HLS · "+title));if(mediaUrl(dash))items.add(new VideoCatalog.Item(dash,"DASH",headers,"",id,"YouTube · DASH · "+title));
        for(VideoCatalog.Item candidate:items)candidate.metadata.merge(common(data));
        return new Result(id,title,data.optString("status"),data.optString("reason"),data.optBoolean("playerData"),items,common(data));
    }
    static VideoMetadata common(JSONObject data){
        VideoMetadata info=new VideoMetadata().duration(data.optDouble("duration")).put("Autor / Kanal",data.optString("author")).put("Beschreibung",data.optString("description")).put("Aufrufe",data.optString("views")).put("Veröffentlichung",data.optString("uploadDate")).put("Untertitel",data.optString("subtitles"));if(data.optBoolean("live"))info.put("Übertragung","Livestream");return info;
    }
    private static VideoCatalog.Item item(JSONObject f,JSONObject audio,String id,String title,Map<String,String> headers,JSONObject data)throws Exception{
        String mime=f.optString("mimeType"),quality=f.optString("qualityLabel");if(quality.isEmpty())quality=f.optInt("height")>0?f.optInt("height")+"p":"Video";
        VideoCatalog.Item item=new VideoCatalog.Item(f.getString("resolved"),mime.contains("webm")?"WebM":"MP4",headers,audio==null?"":audio.getString("resolved"),id,"YouTube · "+quality+" · Bild und Ton\n"+title);
        item.metadata.merge(common(data)).format(mime).dimensions(f.optInt("width"),f.optInt("height")).duration(f.optDouble("approxDurationMs")/1000).number("Bildrate",f.optDouble("fps")," fps").number("Bitrate",f.optDouble("bitrate")/1000," kbit/s").put("MIME-Typ",mime).put("Qualitätsangabe",quality).put("Tonspur",audio==null?"In der Videodatei":"Separate Tonspur").size(audio==null?"Dateigröße":"Video-Dateigröße",f.optLong("contentLength")).put("Format-ID",f.optInt("itag")>0?Integer.toString(f.optInt("itag")):"");
        if(audio!=null)item.metadata.put("Audio-MIME-Typ",audio.optString("mimeType")).put("Audio-Codec",codecs(audio.optString("mimeType"))).size("Audio-Dateigröße",audio.optLong("contentLength")).number("Audio-Bitrate",audio.optDouble("bitrate")/1000," kbit/s");
        JSONObject sound=audio==null?f:audio;item.metadata.put(audio==null?"Codecs":"Video-Codec",codecs(mime)).number("Abtastrate",sound.optDouble("audioSampleRate")," Hz").number("Audiokanäle",sound.optInt("audioChannels"),"");JSONObject track=sound.optJSONObject("audioTrack");if(track!=null)item.metadata.put("Sprache / Tonspur",track.optString("displayName")).put("Tonspur-ID",track.optString("id"));
        return item;
    }
    private static String codecs(String mime){int start=mime.indexOf("codecs=");if(start<0)return "";return mime.substring(start+7).replace("\"","").trim();}
}
