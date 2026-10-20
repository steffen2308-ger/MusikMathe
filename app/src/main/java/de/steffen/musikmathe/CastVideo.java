package de.steffen.musikmathe;

import android.net.Uri;
import java.util.Locale;

/** A receiver gets a media URL and title, never the browser's cookies or request headers. */
final class CastVideo {
    final String url, mime, title;
    private CastVideo(String url,String mime,String title){this.url=url;this.mime=mime;this.title=title;}
    static String problem(VideoCatalog.Item item,String page,boolean protectedMedia,boolean privateTab){
        if(privateTab)return "Übertragen ist nur in normalen Tabs verfügbar.";
        String host=host(page);
        if(protectedMedia||domain(host,"netflix.com"))return "Diese Seite verwendet geschützte Inhalte. Nutze die Cast-Funktion des Anbieters oder dessen TV-App.";
        if(item==null)return "Starte zuerst ein Video auf der Seite und suche erneut.";
        Uri uri=Uri.parse(item.url);String mediaHost=host(item.url);
        if(!("http".equalsIgnoreCase(uri.getScheme())||"https".equalsIgnoreCase(uri.getScheme()))||mediaHost.isEmpty()||uri.getUserInfo()!=null)
            return "Diese Videoquelle lässt sich nicht direkt vom Fernseher laden.";
        if(mediaHost.equals("localhost")||mediaHost.endsWith(".localhost")||mediaHost.equals("0.0.0.0")||mediaHost.startsWith("127.")||mediaHost.equals("::1")||mediaHost.equals("[::1]"))
            return "Diese Videoquelle ist nur auf dem Handy erreichbar.";
        if(!item.youtubeId.isEmpty()||domain(host,"youtube.com")||domain(host,"youtu.be")||domain(mediaHost,"googlevideo.com"))
            return "Nutze für YouTube die Cast-Funktion der YouTube-App.";
        if(!item.audioUrl.isEmpty())return "Bild und Ton liegen getrennt vor. Diese Quelle kann noch nicht übertragen werden.";
        for(String key:item.headers.keySet())if("Authorization".equalsIgnoreCase(key))return "Diese Quelle benötigt eine Anmeldung, die der Fernseher nicht übernehmen kann.";
        if(mime(item.type).isEmpty())return "Dieses Videoformat wird beim Übertragen noch nicht unterstützt.";
        return "";
    }
    static CastVideo from(VideoCatalog.Item item,String title){
        String clean=title==null?"Video":title.replaceAll("[\\p{Cntrl}]"," ").trim();
        if(clean.isEmpty()||clean.startsWith("http:")||clean.startsWith("https:"))clean="Video";
        return new CastVideo(item.url,mime(item.type),clean.substring(0,Math.min(clean.length(),160)));
    }
    static String mime(String type){
        switch(type){case "MP4":return "video/mp4";case "WebM":return "video/webm";case "HLS":return "application/x-mpegURL";case "DASH":return "application/dash+xml";default:return "";}
    }
    private static String host(String url){String host=url==null?null:Uri.parse(url).getHost();return host==null?"":host.toLowerCase(Locale.ROOT);}
    private static boolean domain(String host,String domain){return host.equals(domain)||host.endsWith("."+domain);}
}
