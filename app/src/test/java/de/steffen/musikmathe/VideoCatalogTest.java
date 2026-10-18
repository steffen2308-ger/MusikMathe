package de.steffen.musikmathe;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class VideoCatalogTest {
    @Test public void detectsManifestsAndDirectMediaButNotFragmentsOrBlobUrls(){
        Map<String,String> empty=Collections.emptyMap();
        assertEquals("HLS",VideoCatalog.classify("https://cdn.test/a/master.m3u8?token=abc","",empty));
        assertEquals("DASH",VideoCatalog.classify("https://cdn.test/a","application/dash+xml",empty));
        assertEquals("MP4",VideoCatalog.classify("https://cdn.test/movie.mp4","",empty));
        assertEquals("MP4",VideoCatalog.classify("https://cdn.test/stream?mime=video%2Fmp4","",empty));
        assertNull(VideoCatalog.classify("blob:https://site.test/abc","video/mp4",empty));
        for(String file:new String[]{"seg.ts","chunk.m4s","init.mp4","segment-1.mp4","style.css"})assertNull(file,VideoCatalog.classify("https://cdn.test/"+file,"",empty));
    }
    @Test public void candidatesArePerPageAndDuplicatesMergeHeadersWithoutPersistingCookies(){
        VideoCatalog catalog=new VideoCatalog();Map<String,String> headers=new LinkedHashMap<>();headers.put("Cookie","secret");headers.put("Authorization","Bearer token");headers.put("Referer","https://site.test/");
        assertTrue(catalog.add("https://cdn.test/movie.mp4","",headers));assertFalse(catalog.add("https://cdn.test/movie.mp4","",Collections.emptyMap()));
        assertEquals(1,catalog.list().size());assertFalse(catalog.list().get(0).headers.containsKey("Cookie"));assertEquals("Bearer token",catalog.list().get(0).headers.get("Authorization"));
        catalog.clear();assertTrue(catalog.list().isEmpty());
    }
}
