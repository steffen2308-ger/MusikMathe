package de.steffen.musikmathe;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class VideoMetadataTest {
    @Test public void playerFactsSurviveCatalogCopiesAndStayWithTheirOwnSource()throws Exception{
        VideoCatalog catalog=new VideoCatalog();catalog.add("https://cdn.test/a.mp4","",Map.of());catalog.add("https://cdn.test/b.mp4","",Map.of());
        catalog.metadata("https://cdn.test/a.mp4",new JSONObject().put("width",1920).put("height",1080).put("duration",3723).put("mime","video/mp4").put("size",2000000));catalog.describe("https://cdn.test/a.mp4","Film A","");catalog.add("https://cdn.test/a.mp4","",Map.of("User-Agent","Browser"));
        VideoCatalog.Item first=catalog.list().get(0);assertEquals("1920 × 1080 Pixel",first.metadata.get("Auflösung"));assertEquals("1:02:03",first.metadata.get("Dauer"));assertEquals("2,00 MB",first.metadata.get("Dateigröße"));assertEquals("MP4",first.metadata.get("Format"));assertTrue(catalog.list().get(1).metadata.summary(false).contains("Dauer: nicht angegeben"));
        assertFalse(first.metadata.details(first).contains("User-Agent"));catalog.clear();assertTrue(catalog.list().isEmpty());
    }
    @Test public void invalidAndLiveValuesNeverBecomeInventedFiniteDurations()throws Exception{
        VideoMetadata unknown=VideoMetadata.fromPlayer(new JSONObject().put("width",-1).put("duration",-1));assertTrue(unknown.summary(false).contains("Auflösung: nicht angegeben"));assertTrue(unknown.summary(false).contains("Dauer: nicht angegeben"));
        VideoMetadata live=VideoMetadata.fromPlayer(new JSONObject().put("live",true));assertTrue(live.summary(false).contains("Dauer: Livestream"));
        VideoMetadata manifest=VideoMetadata.fromPlayer(new JSONObject().put("mime","application/vnd.apple.mpegurl").put("size",900));assertEquals("",manifest.get("Dateigröße"));assertEquals("900 B",manifest.get("Manifestgröße"));
    }
    @Test public void hlsVariantsShowAllQualitiesWithoutClaimingOneFixedResolution()throws Exception{
        String text="#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"Deutsch\",LANGUAGE=\"de\",URI=\"audio.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=640x360,FRAME-RATE=25,CODECS=\"avc1.4d401e,mp4a.40.2\",AUDIO=\"audio\"\nsmall.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,FRAME-RATE=50,CODECS=\"avc1.640028,mp4a.40.2\",AUDIO=\"audio\"\nlarge.m3u8\n";
        VideoMetadata metadata=VideoMetadataProbe.parse("HLS","https://cdn.test/master.m3u8",text.getBytes(StandardCharsets.UTF_8));assertEquals("",metadata.get("Auflösung"));assertTrue(metadata.get("Qualitätsstufen").contains("640 × 360"));assertTrue(metadata.get("Qualitätsstufen").contains("1920 × 1080"));assertTrue(metadata.get("Qualitätsstufen").contains("50 fps"));assertTrue(metadata.get("Tonspuren").contains("de"));assertTrue(metadata.summary(false).contains("Auflösung: Adaptiv"));
    }
    @Test public void mediaPlaylistDurationAndLiveWindowAreDifferentFacts()throws Exception{
        String base="#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:9.5,\na.ts\n#EXTINF:8.5,\nb.ts\n";
        VideoMetadata vod=VideoMetadataProbe.parse("HLS","https://cdn.test/vod.m3u8",(base+"#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8));assertEquals("0:18",vod.get("Dauer"));
        VideoMetadata live=VideoMetadataProbe.parse("HLS","https://cdn.test/live.m3u8",base.getBytes(StandardCharsets.UTF_8));assertEquals("",live.get("Dauer"));assertEquals("0:18",live.get("Zeitfenster"));assertTrue(live.summary(false).contains("Dauer: Livestream"));
        assertThrows(java.io.IOException.class,()->VideoMetadataProbe.bounded(new ByteArrayInputStream(new byte[VideoMetadataProbe.LIMIT+1])));
    }
    @Test public void dashRepresentationsIncludeDurationQualitiesAndLanguages()throws Exception{
        String xml="<MPD xmlns='urn:mpeg:dash:schema:mpd:2011' type='static' mediaPresentationDuration='PT125S' minBufferTime='PT1S'><Period><AdaptationSet mimeType='video/mp4' contentType='video'><Representation id='360' bandwidth='900000' width='640' height='360' codecs='avc1.4d401e'><BaseURL>small.mp4</BaseURL></Representation><Representation id='1080' bandwidth='5000000' width='1920' height='1080' frameRate='30000/1001' codecs='avc1.640028'><BaseURL>large.mp4</BaseURL></Representation></AdaptationSet><AdaptationSet mimeType='audio/mp4' lang='de' contentType='audio'><Representation id='audio' bandwidth='128000' codecs='mp4a.40.2' audioSamplingRate='48000'><BaseURL>audio.m4a</BaseURL></Representation></AdaptationSet></Period></MPD>";
        VideoMetadata metadata=VideoMetadataProbe.parse("DASH","https://cdn.test/movie.mpd",xml.getBytes(StandardCharsets.UTF_8));assertEquals("2:05",metadata.get("Dauer"));assertTrue(metadata.get("Qualitätsstufen").contains("1920 × 1080"));assertTrue(metadata.get("Tonspuren").contains("48000 Hz"));assertTrue(metadata.get("Tonspuren").contains("de"));
    }
}
