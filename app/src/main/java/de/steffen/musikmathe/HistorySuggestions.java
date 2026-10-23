package de.steffen.musikmathe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local history matching; returned entries retain their exact navigation URL. */
final class HistorySuggestions {
    static final int LIMIT=8;
    private static String normalize(String value){
        String text=value.trim().toLowerCase(Locale.ROOT);
        if(text.startsWith("https://"))text=text.substring(8);else if(text.startsWith("http://"))text=text.substring(7);
        return text.startsWith("www.")?text.substring(4):text;
    }
    private static int score(BrowserStore.Entry entry,String query,String[] terms){
        String url=normalize(entry.url),title=entry.title.toLowerCase(Locale.ROOT);
        String searchable=url+" "+title;
        for(String term:terms)if(!searchable.contains(term))return -1;
        String host=normalize(Address.host(entry.url));
        if(host.startsWith(query))return 0;
        if(url.startsWith(query))return 1;
        if(title.startsWith(query))return 2;
        return 3;
    }
    static List<BrowserStore.Entry> match(List<BrowserStore.Entry> history,String input){
        String query=normalize(input==null?"":input).replaceAll("\\s+"," ");
        List<BrowserStore.Entry> result=new ArrayList<>();if(query.isEmpty())return result;
        String[] terms=query.split(" ");
        // Stable sorting preserves the store's newest-first order when timestamps tie.
        List<BrowserStore.Entry> recent=new ArrayList<>(history);recent.sort((a,b)->Long.compare(b.time,a.time));
        Set<String> seen=new HashSet<>();List<List<BrowserStore.Entry>> ranked=new ArrayList<>();for(int i=0;i<4;i++)ranked.add(new ArrayList<>());
        for(BrowserStore.Entry entry:recent){
            if(!Address.isWeb(entry.url)||!seen.add(entry.url))continue;
            int rank=score(entry,query,terms);if(rank>=0)ranked.get(rank).add(entry);
        }
        for(List<BrowserStore.Entry> group:ranked)for(BrowserStore.Entry entry:group){result.add(entry);if(result.size()==LIMIT)return result;}
        return result;
    }
}
