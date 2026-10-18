package de.steffen.musikmathe;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import android.net.Uri;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class YouTubeSourcesTest {
    private static final String ID="aqz-KE-bpKQ";
    private JSONObject data(JSONArray formats)throws Exception{return new JSONObject().put("videoId",ID).put("title","Testfilm").put("formats",formats).put("playerData",true);}
    private JSONObject video(String url)throws Exception{return new JSONObject().put("url",url).put("itag",137).put("mimeType","video/mp4; codecs=\"avc1.640028\"").put("qualityLabel","1080p");}
    private JSONObject audio()throws Exception{return new JSONObject().put("url","https://r1.googlevideo.com/videoplayback?id=A&itag=140").put("itag",140).put("mimeType","audio/mp4").put("bitrate",128000);}
    @Test public void validatesWatchShortsEmbedAndRealOrigins(){
        for(String url:new String[]{"https://www.youtube.com/watch?v="+ID,"https://m.youtube.com/shorts/"+ID,"https://www.youtube-nocookie.com/embed/"+ID,"https://youtu.be/"+ID})assertEquals(ID,YouTubeSources.videoId(url));
        assertEquals("",YouTubeSources.videoId("https://youtube.com.evil.test/watch?v="+ID));assertFalse(YouTubeSources.originAllowed("https://notyoutube.com"));assertFalse(YouTubeSources.originAllowed("http://www.youtube.com"));assertTrue(YouTubeSources.originAllowed("https://m.youtube.com"));
    }
    @Test public void pairsVideoWithAudioAndUsesBrowserResolvedUrl()throws Exception{
        String original="https://r1.googlevideo.com/videoplayback?id=A&itag=137&n=initial",played="https://r1.googlevideo.com/videoplayback?id=A&itag=137&n=resolved&range=0-999";
        YouTubeSources.Result result=YouTubeSources.parse(data(new JSONArray().put(video(original)).put(audio())),Collections.emptyMap(),Collections.singletonList(played));
        assertEquals(1,result.items.size());VideoCatalog.Item item=result.items.get(0);assertTrue(item.url.contains("n=resolved"));assertFalse(item.url.contains("range="));assertTrue(item.audioUrl.contains("itag=140"));assertTrue(item.label().contains("1080p"));
    }
    @Test public void staleVideoUrlsNeverReplaceCurrentVideo()throws Exception{
        String current="https://r1.googlevideo.com/videoplayback?id=A&itag=137",old="https://r1.googlevideo.com/videoplayback?id=B&itag=137";
        YouTubeSources.Result result=YouTubeSources.parse(data(new JSONArray().put(video(current)).put(audio())),Collections.emptyMap(),Collections.singletonList(old));assertEquals(current,result.items.get(0).url);
    }
    @Test public void signatureOnlyFormatsAreNotInventedAndSignedRangeIsNotRemoved()throws Exception{
        JSONObject cipher=video("").put("cipher","url="+Uri.encode("https://r1.googlevideo.com/videoplayback?id=A&itag=137")+"&s=not-deciphered");
        assertTrue(YouTubeSources.parse(data(new JSONArray().put(cipher).put(audio())),Collections.emptyMap(),Collections.emptyList()).items.isEmpty());
        assertEquals("",YouTubeSources.completeObservedUrl("https://r1.googlevideo.com/videoplayback?itag=137&range=0-10&sparams=id%2Crange"));
    }
    @Test public void catalogKeepsPairedMetadataAndResetsAfterVideoChange()throws Exception{
        VideoCatalog catalog=new VideoCatalog();YouTubeSources.Result first=YouTubeSources.parse(data(new JSONArray().put(video("https://r1.googlevideo.com/videoplayback?id=A&itag=137")).put(audio())),Collections.emptyMap(),Collections.emptyList());catalog.youtube(first);
        catalog.add(first.items.get(0).url,"video/mp4",Collections.emptyMap());assertTrue(catalog.list().get(0).audioUrl.contains("itag=140"));
        JSONObject next=data(new JSONArray()).put("videoId","jNQXAC9IVRw");catalog.youtube(YouTubeSources.parse(next,Collections.emptyMap(),Collections.emptyList()));assertTrue(catalog.list().isEmpty());
    }
}
