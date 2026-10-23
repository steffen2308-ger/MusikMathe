package de.steffen.musikmathe;

import android.content.Context;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=28)
public class FrequentSitesTest {
    private Context context;
    private BrowserStore store;
    private BrowserStore.Entry page(String url,String title,long time,long count){return new BrowserStore.Entry(url,title,time,false,count);}
    @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("browser",0).edit().clear().commit();store=new BrowserStore(context);}
    @Test public void realVisitCountsPersistAndCanOutrankMoreRecentPages(){
        for(int i=0;i<4;i++)store.visit("https://often.example/watch","Oft besucht");
        store.visit("https://recent.example/","Zuletzt besucht");
        BrowserStore restored=new BrowserStore(context);
        assertEquals(4,restored.frequentSites().get(0).visits);assertEquals("https://often.example/watch",restored.frequentSites().get(0).url);assertEquals("https://recent.example/",restored.read("history").get(0).url);
    }
    @Test public void sitesAreGroupedTopThreeAreRankedAndRepresentativePageIsMostVisited(){
        List<BrowserStore.Entry> top=FrequentSites.top(Arrays.asList(
            page("https://new.example/","New",80,2),page("https://www.example.org/one","One",10,4),page("http://example.org/two","Two",90,3),
            page("https://b.example/","B",40,6),page("https://c.example/","C",50,6),page("https://example.org:8443/","Other service",100,1)));
        assertEquals(3,top.size());assertEquals("https://www.example.org/one",top.get(0).url);assertEquals(7,top.get(0).visits);assertEquals("https://c.example/",top.get(1).url);assertEquals("https://b.example/",top.get(2).url);
    }
    @Test public void oldHistoryStartsAtOneAndClearingHistoryAlsoClearsFrequency(){
        context.getSharedPreferences("browser",0).edit().putString("history","[{\"url\":\"https://old.example/\",\"title\":\"Old\",\"time\":1}]").commit();
        assertEquals(1,store.frequentSites().get(0).visits);store.visit("https://old.example/","Old again");assertEquals(2,new BrowserStore(context).frequentSites().get(0).visits);
        store.write("history",Collections.emptyList());assertTrue(new BrowserStore(context).frequentSites().isEmpty());
    }
    @Test public void privateHistoryNeitherReadsNorChangesNormalFrequency(){
        store.visit("https://normal.example/","Normal");BrowserStore secret=new BrowserStore(context,true);secret.visit("https://secret.example/","Secret");
        assertTrue(secret.frequentSites().isEmpty());assertEquals(1,new BrowserStore(context).frequentSites().size());assertEquals(1,new BrowserStore(context).frequentSites().get(0).visits);
        String privateScript=HomePage.update(store.frequentSites(),true);assertFalse(privateScript.contains("normal.example"));
    }
    @Test public void unusableAndInternalUrlsAreExcludedAndCountsCannotOverflow(){
        List<BrowserStore.Entry> top=FrequentSites.top(Arrays.asList(page("javascript:alert(1)","Bad",10,99),page(Address.HOME,"Home",10,99),page("https://musikmathe.invalid/wikipedia-de","Internal",10,99),page("https://user:secret@example.org/","Credentials",10,99),page("https://a.example/one","A",1,Long.MAX_VALUE),page("https://a.example/two","A2",2,8)));
        assertEquals(1,top.size());assertEquals(Long.MAX_VALUE,top.get(0).visits);
        store.write("history",Arrays.asList(page("https://a.example/one","A",1,Long.MAX_VALUE)));store.visit("https://a.example/one","A");assertEquals(Long.MAX_VALUE,store.read("history").get(0).visits);
    }
}
