package de.steffen.musikmathe;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreloadLinksTest {
    private String link(String url){return PreloadLinks.eligible("https://site.example/current",url,"Read article",false,"");}
    @Test public void onlyHttpsPageLinksAreEligibleAndEscapesArePreserved(){
        assertEquals("https://site.example/news?q=a%20b",link("https://site.example/news?q=a%20b#chapter"));
        assertEquals("https://other.example/page.php",link("https://other.example/page.php"));
        for(String url:new String[]{"http://site.example/news","javascript:alert(1)","file:///secret","mailto:a@example.org","https://user:secret@site.example/","https://site.example:99999/","https://musikmathe.invalid/wikipedia-de","https://site.example/current#same"})assertEquals(url,"",link(url));
    }
    @Test public void downloadsAndRecognisableActionsNeverBecomeCandidates(){
        for(String url:new String[]{"https://site.example/logout","https://site.example/sign-out","https://site.example/log_out","https://site.example/sign-in","https://site.example/account?do=%64elete","https://site.example/download/123","https://site.example/api/items","https://site.example/movie.mp4","https://site.example/file.pdf","https://site.example/action?csrf=123"})assertEquals(url,"",link(url));
        assertEquals("",PreloadLinks.eligible("","https://site.example/neutral","Abmelden",false,""));
        assertEquals("",PreloadLinks.eligible("","https://site.example/neutral","Read",true,""));
        assertEquals("",PreloadLinks.eligible("","https://site.example/neutral","Read",false,"external sponsored"));
        assertEquals("",PreloadLinks.eligible("","https://site.example/neutral","Read",false,"nofollow"));
        assertEquals("https://site.example/artwork",link("https://site.example/artwork"));
    }
}
