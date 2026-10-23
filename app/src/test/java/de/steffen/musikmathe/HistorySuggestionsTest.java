package de.steffen.musikmathe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistorySuggestionsTest {
    private BrowserStore.Entry entry(String url,String title,long time){return new BrowserStore.Entry(url,title,time);}
    @Test public void ranksHostAndAddressPrefixesBeforeTitleMatchesThenUsesRecency(){
        List<BrowserStore.Entry> history=Arrays.asList(
            entry("https://other.example/","Über Wikipedia",500),entry("https://reference.example/","Wikipedia Wissen",400),
            entry("https://www.wikipedia.org/wiki/Mathematik","Mathematik",100),entry("https://wikipedia.de/","Enzyklopädie",200));
        List<BrowserStore.Entry> result=HistorySuggestions.match(history,"WIKIPEDIA");
        assertEquals(Arrays.asList("https://wikipedia.de/","https://www.wikipedia.org/wiki/Mathematik","https://reference.example/","https://other.example/"),urls(result));
        assertEquals("https://www.wikipedia.org/wiki/Mathematik",HistorySuggestions.match(history,"https://WWW.WIKIPEDIA.ORG/wiki/Mat").get(0).url);
    }
    @Test public void matchesMultipleWordsAcrossTitleAndAddressWithoutChangingTheUrl(){
        String url="https://example.org/Mathe?course=1&lang=de#Übung";
        List<BrowserStore.Entry> rows=Collections.singletonList(entry(url,"Meine Übungen",10));
        assertEquals(url,HistorySuggestions.match(rows,"  MATHE   ÜBUNGEN  ").get(0).url);
        assertTrue(HistorySuggestions.match(rows,"Mathe unbekannt").isEmpty());assertTrue(HistorySuggestions.match(rows," ").isEmpty());
        assertTrue(HistorySuggestions.match(rows,null).isEmpty());
    }
    @Test public void capsAndDeduplicatesResultsAndExcludesNonWebPages(){
        List<BrowserStore.Entry> rows=new ArrayList<>();
        rows.add(entry("javascript:alert(1)","Treffer",200));rows.add(entry(Address.HOME,"Treffer",200));rows.add(entry("content://secret/file","Treffer",200));
        rows.add(entry("https://first.example/","Treffer neu",100));rows.add(entry("https://first.example/","Treffer alt",1));
        for(int i=0;i<20;i++)rows.add(entry("https://page"+i+".example/","Treffer "+i,90-i));
        List<BrowserStore.Entry> result=HistorySuggestions.match(rows,"Treffer");assertEquals(8,result.size());assertEquals("Treffer neu",result.get(0).title);
        assertEquals(8,new java.util.HashSet<>(urls(result)).size());
    }
    private List<String> urls(List<BrowserStore.Entry> rows){List<String> urls=new ArrayList<>();for(BrowserStore.Entry row:rows)urls.add(row.url);return urls;}
}
