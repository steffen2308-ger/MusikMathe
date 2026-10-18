package de.steffen.musikmathe;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28)
public class WikiTopTest {
    @Test public void ranksGermanyAcrossWikipediaLanguagesAndFiltersUtilityPages()throws Exception{
        String json="{\"items\":[{\"country\":\"DE\",\"articles\":[{\"article\":\"Hauptseite\",\"project\":\"de.wikipedia\",\"rank\":1},{\"article\":\"Test_&_Text\",\"project\":\"de.wikipedia\",\"views_ceil\":2500,\"rank\":3},{\"article\":\"Music\",\"project\":\"en.wikipedia\",\"views_ceil\":4000,\"rank\":2},{\"article\":\"Special:Search\",\"project\":\"en.wikipedia\"},{\"article\":\"Unsafe\",\"project\":\"evil.example\"}]}]}";
        List<WikiTop.Article> rows=WikiTop.parse(json);assertEquals(2,rows.size());assertEquals("Music",rows.get(0).title);assertEquals("Test & Text",rows.get(1).title);assertEquals(2500,rows.get(1).views);assertTrue(rows.get(1).url.startsWith("https://de.wikipedia.org/wiki/"));
        String html=WikiTop.html(rows,"01.01.2026","https://wikimedia.org/api");assertTrue(html.contains("Test &amp; Text"));assertTrue(html.contains("aus Deutschland"));assertTrue(html.contains("keine Suchanfragen"));
    }
    @Test(expected=IllegalArgumentException.class)public void wrongCountryIsNeverLabelledGermany()throws Exception{WikiTop.parse("{\"items\":[{\"country\":\"AT\",\"articles\":[]}]}");}
    @Test public void endpointAlwaysRequestsGermanyWithUtcDate(){Calendar day=Calendar.getInstance(TimeZone.getTimeZone("UTC"));day.clear();day.set(2026,0,2);assertTrue(WikiTop.endpoint(day).endsWith("/DE/all-access/2026/01/02"));}
}
