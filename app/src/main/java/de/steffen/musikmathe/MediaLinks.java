package de.steffen.musikmathe;

import android.net.Uri;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Untrusted DOM descriptions become HTTP media candidates, never headers or native actions. */
final class MediaLinks {
    static final int MAX_ITEMS=1000;
    private static final Map<String,String> MIMES=new LinkedHashMap<>();
    private static final java.util.regex.Pattern MIME=java.util.regex.Pattern.compile("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+");
    static {
        add("image/jpeg","jpg jpeg jfif");add("image/png","png apng");add("image/gif","gif");add("image/webp","webp");add("image/avif","avif");add("image/bmp","bmp");add("image/svg+xml","svg svgz");add("image/tiff","tif tiff");add("image/x-icon","ico");add("image/heic","heic heif");add("image/jxl","jxl");
        add("audio/mpeg","mp3 mpga");add("audio/mp4","m4a alac");add("audio/aac","aac");add("audio/ogg","ogg oga opus");add("audio/flac","flac");add("audio/wav","wav");add("audio/aiff","aif aiff");add("audio/webm","weba");add("audio/amr","amr");
        add("video/mp4","mp4 m4v");add("video/webm","webm");add("video/quicktime","mov");add("video/x-matroska","mkv");add("video/ogg","ogv");add("video/x-msvideo","avi");add("video/mpeg","mpg mpeg m2v");add("video/mp2t","ts mts m2ts");add("video/3gpp","3gp 3g2");
        add("application/vnd.apple.mpegurl","m3u8");add("application/dash+xml","mpd");
    }
    private static void add(String mime,String extensions){for(String extension:extensions.split(" "))MIMES.put(extension,mime);}
    private static String extension(String name){int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT);}
    static String mime(String value){String result=value==null?"":value.split(";",2)[0].trim().toLowerCase(Locale.ROOT);return result.equals("application/ogg")?"audio/ogg":result;}
    private static boolean supported(String value){return !value.isEmpty()&&value.length()<=128&&(value.startsWith("image/")||value.startsWith("audio/")||value.startsWith("video/")||value.equals("application/vnd.apple.mpegurl")||value.equals("application/x-mpegurl")||value.equals("application/dash+xml"))&&MIME.matcher(value).matches();}
    static final class Item {
        final String url,name,mime,type;
        Item(String url,String name,String mime){this.url=url;this.mime=mime;type=mime.contains("mpegurl")?"HLS":mime.equals("application/dash+xml")?"DASH":"";String stem=name.replaceFirst("(?i)\\.(m3u8|mpd)$","");this.name=type.isEmpty()?name:DownloadFolder.safeName(stem.toLowerCase(Locale.ROOT).endsWith(".mp4")?stem:stem+".mp4");}
        boolean original(){return type.isEmpty();}
        String kind(){return mime.startsWith("image/")?"Bild":mime.startsWith("audio/")||mime.equals("application/ogg")?"Audio":"Video";}
        String label(){return kind()+" · "+name+"\n"+url;}
    }
    static Item item(JSONObject input){
        String raw=input.optString("url");if(raw.length()>8192||!Address.isWeb(raw))return null;
        Uri uri=Uri.parse(raw);String url=uri.buildUpon().fragment(null).build().toString();
        String suggested=input.optString("download");if(suggested.length()>512)suggested=suggested.substring(0,512);
        String path=uri.getLastPathSegment(),name=suggested.isEmpty()?(path==null?"":path):suggested;
        String declared=mime(input.optString("mime")),inferred=MIMES.get(extension(name));
        if(inferred==null)inferred=MIMES.get(extension(path==null?"":path));
        String content=supported(declared)?declared:inferred;if(content==null||!supported(content))return null;
        if(content.equals("application/x-mpegurl")||content.equals("audio/mpegurl")||content.equals("audio/x-mpegurl"))content="application/vnd.apple.mpegurl";
        if(name.isEmpty()||name.equals(".")||name.equals(".."))name="Medium";
        if(!MIMES.containsKey(extension(name))){for(Map.Entry<String,String> row:MIMES.entrySet())if(row.getValue().equals(content)){name+="."+row.getKey();break;}}
        return new Item(url,DownloadFolder.safeName(name),content);
    }
    static void collect(Map<String,Item> items,JSONArray values){for(int i=0;i<values.length()&&items.size()<MAX_ITEMS;i++){JSONObject value=values.optJSONObject(i);if(value==null)continue;Item item=item(value);if(item!=null)items.putIfAbsent(item.url,item);}}
    static Map<String,String> headers(String page,String target,String agent){
        Map<String,String> result=new LinkedHashMap<>();if(agent!=null&&!agent.contains("\r")&&!agent.contains("\n"))result.put("User-Agent",agent);
        if(Address.isWeb(page)&&Address.isWeb(target)&&!("https".equalsIgnoreCase(Uri.parse(page).getScheme())&&"http".equalsIgnoreCase(Uri.parse(target).getScheme()))){
            Uri source=Uri.parse(page);String authority=source.getEncodedAuthority();int at=authority.lastIndexOf('@');Uri clean=source.buildUpon().encodedAuthority(authority.substring(at+1)).fragment(null).build();String referrer=clean.buildUpon().path("/").clearQuery().build().toString();
            if(VideoCatalog.origin(page).equals(VideoCatalog.origin(target)))referrer=clean.toString();
            result.put("Referer",referrer);
        }
        return result;
    }
    static boolean accepts(String expected,String received){String value=mime(received);return value.isEmpty()||value.equals("application/octet-stream")||value.equals("binary/octet-stream")||(supported(value)&&value.substring(0,value.indexOf('/')).equals(expected.substring(0,expected.indexOf('/'))));}
    private MediaLinks(){}
}
