package de.steffen.musikmathe;

import android.net.Uri;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Per-page, memory-only candidates; media segments are not offered as complete videos. */
final class VideoCatalog {
    static String scanScript(android.content.Context context){try(java.io.InputStream in=context.getAssets().open("video-scan.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}catch(java.io.IOException error){return "JSON.stringify([])";}}
    static final class Item {
        final String url, type, audioUrl, youtubeId, description,thumbnail;
        final Map<String,String> headers;
        Item(String url,String type,Map<String,String> headers){this(url,type,headers,"","","");}
        Item(String url,String type,Map<String,String> headers,String audioUrl,String youtubeId,String description){this(url,type,headers,audioUrl,youtubeId,description,youtubeId.matches("[A-Za-z0-9_-]{11}")?"https://i.ytimg.com/vi/"+youtubeId+"/hqdefault.jpg":"");}
        Item(String url,String type,Map<String,String> headers,String audioUrl,String youtubeId,String description,String thumbnail){this.url=url;this.type=type;this.headers=new LinkedHashMap<>(headers);this.audioUrl=audioUrl;this.youtubeId=youtubeId;this.description=description;this.thumbnail=safeThumbnail(thumbnail);}
        String label(){if(!description.isEmpty())return description;String path=Uri.parse(url).getLastPathSegment();return type+" · "+Address.host(url)+"\n"+(path==null?"Videostream":path);}
    }
    static String safeThumbnail(String value){if(value==null)return "";if(Address.isWeb(value)&&value.length()<=8192)return value;if(value.length()<=150000&&value.matches("data:image/(?:jpeg|png|webp);base64,[A-Za-z0-9+/=]+"))return value;return "";}
    synchronized void describe(String url,String title,String thumbnail){Item old=items.get(url);if(old==null||!old.youtubeId.isEmpty())return;String preview=safeThumbnail(thumbnail);String label=title==null?"":title.trim();if(label.length()>300)label=label.substring(0,300);items.put(url,new Item(old.url,old.type,old.headers,old.audioUrl,old.youtubeId,label.isEmpty()?old.description:label+"\n"+old.type,preview.isEmpty()?old.thumbnail:preview));}
    static final String HOOKS="(function(){try{if(window.__mmVideoHook)return;window.__mmVideoHook=true;window.__mmVideos=[];function put(u,t){if(u&&t&&(/video\\/|mpegurl|dash\\+xml/i).test(t)){var a=window.__mmVideos;if(a.length<80)a.push({url:u,type:t});}}if(window.fetch){var f=window.fetch;window.fetch=function(){return f.apply(this,arguments).then(function(r){try{put(r.url,r.headers.get('content-type'));}catch(e){}return r;});};}var x=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(){this.addEventListener('load',function(){try{put(this.responseURL,this.getResponseHeader('Content-Type'));}catch(e){}},{once:true});return x.apply(this,arguments);};}catch(e){}})()";
    private final LinkedHashMap<String,Map<String,String>> requests=new LinkedHashMap<>();
    synchronized void observe(String url,Map<String,String> headers){if(!Address.isWeb(url))return;if(requests.size()>=220)requests.remove(requests.keySet().iterator().next());requests.put(url,safeHeaders(headers));}
    private final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
    private YouTubeSources.Result youtube;
    private String resolvedId="";
    private long resolvedAt;
    private final List<Item> resolvedItems=new ArrayList<>();
    synchronized void resolved(YouTubeDownloadResolver.Result result){resolvedId=result.id;resolvedAt=android.os.SystemClock.elapsedRealtime();resolvedItems.clear();resolvedItems.addAll(result.items);}
    synchronized void clearResolved(){resolvedItems.clear();resolvedId="";}
    synchronized boolean hasResolved(String id){return id.equals(resolvedId)&&!resolvedItems.isEmpty()&&android.os.SystemClock.elapsedRealtime()-resolvedAt<10*60*1000;}
    synchronized YouTubeSources.Result youtube(){return youtube;}
    synchronized List<String> observed(){return new ArrayList<>(requests.keySet());}
    synchronized void youtube(YouTubeSources.Result result){
        if(youtube!=null&&!youtube.videoId.equals(result.videoId)){items.clear();clearResolved();}
        youtube=result;java.util.Iterator<Map.Entry<String,Item>> it=items.entrySet().iterator();while(it.hasNext())if(!it.next().getValue().youtubeId.isEmpty())it.remove();
        for(Item item:result.items){items.put(item.url,item);}
    }
    synchronized void clear(){items.clear();requests.clear();youtube=null;resolvedItems.clear();resolvedId="";}
    synchronized List<Item> list(){ArrayList<Item> result=new ArrayList<>(resolvedItems);boolean paired=youtube!=null||!resolvedItems.isEmpty();
        for(Item item:items.values())if((resolvedItems.isEmpty()||item.youtubeId.isEmpty())&&(!paired||!item.youtubeId.isEmpty()||!isGoogleVideo(item.url)))result.add(item);
        return result;}
    synchronized boolean add(String url,String mime,Map<String,String> headers){
        String type=classify(url,mime,headers);if(type==null)return false;
        Map<String,String> combined=safeHeaders(headers);Map<String,String> actual=requests.get(url);if(actual!=null)combined.putAll(actual);headers=combined;
        if(items.containsKey(url)){Item old=items.get(url);Map<String,String> merged=new LinkedHashMap<>(old.headers);merged.putAll(safeHeaders(headers));items.put(url,new Item(url,old.type,merged,old.audioUrl,old.youtubeId,old.description,old.thumbnail));return false;}
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
        String hint=uri.getQueryParameter("mime");if(hint!=null && hint.toLowerCase(Locale.ROOT).startsWith("video/"))type=hint.toLowerCase(Locale.ROOT);
        if(type.contains("video/webm"))return "WebM";if(type.contains("video/mp4"))return "MP4";
        if(type.startsWith("video/"))return "Video";
        if(headers!=null)for(Map.Entry<String,String> row:headers.entrySet())if("Sec-Fetch-Dest".equalsIgnoreCase(row.getKey()) && "video".equalsIgnoreCase(row.getValue()))return "Video";
        return null;
    }
    private static boolean isGoogleVideo(String url){String h=Uri.parse(url).getHost();return h!=null&&(h.equals("googlevideo.com")||h.endsWith(".googlevideo.com"));}
    static String origin(String url){Uri u=Uri.parse(url);String scheme=u.getScheme();int port=u.getPort();if(port<0)port="https".equalsIgnoreCase(scheme)?443:80;return (scheme+"://"+u.getHost()+":"+port).toLowerCase(Locale.ROOT);}
}
