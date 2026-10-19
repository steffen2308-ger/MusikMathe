package de.steffen.musikmathe;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdBlockerTest {
    @Test public void parsesHostListAndRejectsNonDomainInput()throws Exception{
        java.util.Set<String> hosts=AdBlocker.readHosts(new java.io.StringReader("# comment\n ads.publisher.example \nADS.PUBLISHER.EXAMPLE\n"));
        assertEquals(1,hosts.size());assertTrue(hosts.contains("ads.publisher.example"));
        try{AdBlocker.readHosts(new java.io.StringReader("<html>error</html>"));fail("Not a domain list");}catch(java.io.IOException expected){}
    }
    @Test public void siteExceptionsRespectHostBoundaries(){
        java.util.Set<String> allowed=java.util.Collections.singleton("example.org");
        assertTrue(AdBlocker.excepted("https://news.example.org/page",allowed));
        assertFalse(AdBlocker.excepted("https://example.org.evil.test/page",allowed));
        assertFalse(AdBlocker.excepted("file:///example.org",allowed));
    }
    @Test public void blocksConservativeSameOriginAdScriptPaths(){
        assertTrue(AdBlocker.shouldBlock("https://publisher.example/widget/ads.js",false,true));
        assertTrue(AdBlocker.shouldBlock("https://publisher.example/pagead.js?cache=1",false,true));
        assertFalse(AdBlocker.shouldBlock("https://publisher.example/pagead.js",true,true));
        assertFalse(AdBlocker.shouldBlock("https://publisher.example/article?file=pagead.js",false,true));
    }
    @Test public void blocksAdHostsAndSubdomainsOverHttpAndHttps() {
        assertTrue(AdBlocker.shouldBlock("https://securepubads.g.doubleclick.net/tag/js/gpt.js", false, true));
        assertTrue(AdBlocker.shouldBlock("http://pagead2.googlesyndication.com/pagead/show_ads.js", false, true));
        assertTrue(AdBlocker.shouldBlock("https://cdn.taboola.com/libtrc/loader.js", false, true));
        assertTrue(AdBlocker.shouldBlock("HTTPS://DOUBLECLICK.NET.:443/banner", false, true));
    }
    @Test public void disabledFilterAndExplicitNavigationAlwaysPassThrough() {
        assertFalse(AdBlocker.shouldBlock("https://doubleclick.net/banner.js", false, false));
        assertFalse(AdBlocker.shouldBlock("https://doubleclick.net/", true, true));
    }
    @Test public void onlyMatchesWholeHostLabels() {
        assertFalse(AdBlocker.shouldBlock("https://notdoubleclick.net/script.js", false, true));
        assertFalse(AdBlocker.shouldBlock("https://doubleclick.net.example.org/script.js", false, true));
        assertFalse(AdBlocker.shouldBlock("https://example.org/doubleclick.net/ad.js?host=taboola.com", false, true));
        assertFalse(AdBlocker.shouldBlock("https://doubleclick.net@example.org/file", false, true));
    }
    @Test public void preservesNormalPagesAndVideoDelivery() {
        for(String url:new String[]{"https://www.google.com/search?q=musik", "https://www.youtube.com/watch?v=123",
                "https://r1.googlevideo.com/videoplayback", "https://upload.wikimedia.org/image.jpg", "https://example.org/style.css"}) {
            assertFalse(url, AdBlocker.shouldBlock(url, false, true));
        }
    }
    @Test public void malformedAndLocalUrlsAreNotFiltered() {
        for(String url:new String[]{null, "", "https://[invalid", "not a URL", "file:///doubleclick.net",
                "data:text/plain,doubleclick.net", "about:blank", "javascript:alert(1)", "ftp://doubleclick.net/a"}) {
            assertFalse(AdBlocker.shouldBlock(url, false, true));
        }
    }
}
