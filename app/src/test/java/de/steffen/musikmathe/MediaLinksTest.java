package de.steffen.musikmathe;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class MediaLinksTest {
    private MediaLinks.Item item(String url,String mime,String name)throws Exception{return MediaLinks.item(new JSONObject().put("url",url).put("mime",mime).put("download",name));}
    @Test public void identifiesImagesAudioVideosAndStreamsWithoutRewritingSignedQueries()throws Exception{
        MediaLinks.Item photo=item("https://cdn.test/image.JPG?token=a%2Fb+%2B#part","","");assertEquals("image/jpeg",photo.mime);assertEquals("https://cdn.test/image.JPG?token=a%2Fb+%2B",photo.url);assertTrue(photo.original());
        assertEquals("audio/mpeg",item("https://files.test/download?id=42","","sound.mp3").mime);
        assertEquals("image/png",item("https://files.test/image?id=42","image/png; charset=binary","").mime);
        assertEquals("HLS",item("https://files.test/live.m3u8","","").type);assertEquals("live.mp4",item("https://files.test/live.m3u8","","").name);
        assertFalse(item("https://files.test/movie.mpd","","").original());
        assertNull(item("https://files.test/article","text/html",""));assertNull(item("https://files.test/file","bad-mpegurl",""));
        for(String url:new String[]{"javascript:alert(1)","blob:https://files.test/id","data:image/png;base64,AA","file:///private/picture.jpg"})assertNull(item(url,"image/png","secret.jpg"));
    }
    @Test public void deduplicatesFragmentsButKeepsDifferentSignedVariantsAndSafeFilenames()throws Exception{
        Map<String,MediaLinks.Item> found=new LinkedHashMap<>();JSONArray rows=new JSONArray();
        for(String url:new String[]{"https://files.test/a.jpg#one","https://files.test/a.jpg#two","https://files.test/a.jpg?size=2"})rows.put(new JSONObject().put("url",url));
        MediaLinks.collect(found,rows);assertEquals(2,found.size());assertFalse(item("https://files.test/a.jpg","","../../bad\nname.jpg").name.contains("/"));
    }
    @Test public void credentialsNeverComeFromTheDomAndCrossOriginReferrersLosePaths()throws Exception{
        Map<String,String> same=MediaLinks.headers("https://user:pass@page.test/private?token=secret#x","https://page.test/a.jpg","Agent");assertEquals("https://page.test/private?token=secret",same.get("Referer"));
        Map<String,String> cross=MediaLinks.headers("https://page.test/private?token=secret","https://cdn.test/a.jpg","Agent");assertEquals("https://page.test/",cross.get("Referer"));assertFalse(cross.containsKey("Cookie"));assertFalse(cross.containsKey("Authorization"));
        assertFalse(MediaLinks.headers("https://page.test/secret","http://files.test/a.jpg","Agent").containsKey("Referer"));
        assertFalse(MediaLinks.accepts("image/png","text/html"));assertTrue(MediaLinks.accepts("image/png","image/png"));assertTrue(MediaLinks.accepts("video/mp4","application/octet-stream"));
    }
}
