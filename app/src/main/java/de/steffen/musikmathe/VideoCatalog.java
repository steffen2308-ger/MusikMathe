package de.steffen.musikmathe;

import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-page, memory-only candidates; media segments are not offered as complete videos. */
final class VideoCatalog {
    static final class Item {
        final String url, type;
        final Map<String,String> headers;
        Item(String url,String type,Map<String,String> headers){this.url=url;this.type=type;this.headers=new LinkedHashMap<>(headers);}
        String label(){String path=Uri.parse(url).getLastPathSegment();return type+" · "+Address.host(url)+"\n"+(path==null?"Videostream":path);}
    }
    private final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
    synchronized void clear(){items.clear();}
    synchronized List<Item> list(){return new ArrayList<>(items.values());}
    synchronized boolean add(String url,String mime,Map<String,String> headers){
        String type=classify(url,mime,headers);if(type==null)return false;
        if(items.containsKey(url)){Item old=items.get(url);Map<String,String> merged=new LinkedHashMap<>(old.headers);merged.putAll(safeHeaders(headers));items.put(url,new Item(url,type,merged));return false;}
        if(items.size()>=60)return false;items.put(url,new Item(url,type,safeHeaders(headers)));return true;
    }
    static Map<String,String> safeHeaders(Map<String,String> input){
        LinkedHashMap<String,String> safe=new LinkedHashMap<>();
        if(input!=null)for(Map.Entry<String,String> row:input.entrySet()){
            String key=row.getKey(),value=row.getValue();
            if(key!=null && value!=null && !value.contains("\r") && !value.contains("\n") && (key.equalsIgnoreCase("Referer")||key.equalsIgnoreCase("Origin")||key.equalsIgnoreCase("Authorization")||key.equalsIgnoreCase("User-Agent")))safe.put(key,value);
        }return safe;
    }
    static String classify(String url,String mime,Map<String,String> headers){
        if(!Address.isWeb(url))return null;
        Uri uri=Uri.parse(url);String path=uri.getPath()==null?"":uri.getPath().toLowerCase(Locale.ROOT),type=mime==null?"":mime.toLowerCase(Locale.ROOT);
        if(path.endsWith(".m3u8")||type.contains("mpegurl"))return "HLS";
        if(path.endsWith(".mpd")||type.contains("dash+xml"))return "DASH";
        if(path.matches(".*\\.(m4s|ts|aac|vtt|key)$"))return null;
        if(path.matches(".*\\.(mp4|m4v|mov|webm|mkv|ogv|3gp)$")){
            // Initialization/numbered fragments are not standalone movies.
            if(path.matches(".*(?:init|segment|chunk|frag)[-_0-9]*\\.(mp4|m4v)$"))return null;
            return path.endsWith(".webm")?"WebM":path.endsWith(".mp4")||path.endsWith(".m4v")?"MP4":"Video";
        }
        String hint=uri.getQueryParameter("mime");if(hint!=null && hint.toLowerCase(Locale.ROOT).startsWith("video/"))return "Video";
        if(type.startsWith("video/"))return "Video";
        if(headers!=null)for(Map.Entry<String,String> row:headers.entrySet())if("Sec-Fetch-Dest".equalsIgnoreCase(row.getKey()) && "video".equalsIgnoreCase(row.getValue()))return "Video";
        return null;
    }
    static String origin(String url){Uri u=Uri.parse(url);String scheme=u.getScheme();int port=u.getPort();if(port<0)port="https".equalsIgnoreCase(scheme)?443:80;return (scheme+"://"+u.getHost()+":"+port).toLowerCase(Locale.ROOT);}
}
