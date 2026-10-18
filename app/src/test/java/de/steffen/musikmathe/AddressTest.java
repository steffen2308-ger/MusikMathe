package de.steffen.musikmathe;

import org.junit.Test;
import static org.junit.Assert.*;

public class AddressTest {
    @Test public void normalAddressesDefaultToHttps() {
        assertEquals("https://example.org/path?q=1#part",Address.resolve(" example.org/path?q=1#part ","google"));
        assertEquals("http://localhost:8080/",Address.resolve("http://localhost:8080/","google"));
        assertEquals("https://[::1]:443/",Address.resolve("[::1]:443/","google"));
        assertEquals("https://münchen.de/",Address.resolve("münchen.de/","google"));
    }
    @Test public void searchesAreEncodedAndUseChosenEngine() {
        assertEquals("https://duckduckgo.com/?q=Musik+%26+Mathe",Address.resolve("Musik & Mathe","duckduckgo"));
        assertEquals("https://www.bing.com/search?q=site%3Awikipedia.org+Musik",Address.resolve("site:wikipedia.org Musik","bing"));
        assertEquals("https://www.google.com/search?q=test%40example.com",Address.resolve("test@example.com","google"));
    }
    @Test public void executableAndLocalSchemesNeverRunFromAddressBar() {
        for(String address:new String[]{"javascript:alert(1)","data:text/html,hi","FILE:///sdcard/a","content://test/a","intent://evil","about:config"})assertNull(address,Address.resolve(address,"google"));
        assertFalse(Address.isWeb("javascript:alert(1)")); assertFalse(Address.isWeb("https://"));
        assertNull(Address.resolve("https://","google"));
    }
    @Test public void pastedUrlKeepsItsQueryAndEscapesSpaces() {
        assertEquals("https://example.com/a%20b?q=2&x=3",Address.resolve("https://example.com/a b?q=2&x=3","google"));
        assertEquals(Address.HOME,Address.resolve(" ","google"));
        assertEquals("",Address.display(Address.HOME));
        assertEquals("example.com",Address.host("https://example.com/path"));
    }
}
