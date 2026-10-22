package de.steffen.musikmathe;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class VimeoVideosTest {
    @Test public void opensOnlyTheSelectedVideoAndPreservesUnlistedHash(){
        assertEquals("https://player.vimeo.com/video/76979871",VimeoVideos.playerUrl("https://vimeo.com/76979871"));
        assertEquals("https://player.vimeo.com/video/76979871?h=8272103f6e",VimeoVideos.playerUrl("https://player.vimeo.com/video/76979871?h=8272103f6e&autoplay=0#player"));
        assertEquals("https://player.vimeo.com/video/76979871?h=8272103f6e",VimeoVideos.playerUrl("https://vimeo.com/76979871/8272103f6e"));
        assertEquals("https://player.vimeo.com/video/19231868",VimeoVideos.playerUrl("https://vimeo.com/channels/staffpicks/19231868"));
        assertEquals("https://player.vimeo.com/video/19231868",VimeoVideos.playerUrl("https://vimeo.com/showcase/123/video/19231868"));
        assertEquals("https://player.vimeo.com/video/19231868",VimeoVideos.playerUrl("https://vimeo.com/groups/test/videos/19231868"));
    }
    @Test public void rejectsOtherHostsSchemesCredentialsPortsAndNonVideoPages(){
        for(String url:new String[]{"https://vimeo.com.evil.test/76979871","https://evil.vimeo.com/76979871","http://vimeo.com/76979871","javascript:alert(1)","https://user@player.vimeo.com/video/76979871","https://player.vimeo.com:444/video/76979871","https://vimeo.com/watch","https://vimeo.com/channels/staffpicks","https://player.vimeo.com/video/76979871/other","https://vimeo.com/76979871?h=a%0Ab"})assertEquals(url,"",VimeoVideos.playerUrl(url));
    }
}
