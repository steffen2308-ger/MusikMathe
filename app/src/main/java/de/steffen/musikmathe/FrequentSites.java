package de.steffen.musikmathe;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Frequency within retained history, grouped by website; ties favour recent visits. */
final class FrequentSites {
    private static final class Site {
        BrowserStore.Entry page;
        long count,last;
        Site(BrowserStore.Entry page){this.page=page;}
        void include(BrowserStore.Entry next){
            count=add(count,next.visits);last=Math.max(last,next.time);
            if(next.visits>page.visits||(next.visits==page.visits&&next.time>page.time))page=next;
        }
    }
    static long add(long a,long b){return Long.MAX_VALUE-a<b?Long.MAX_VALUE:a+b;}
    static String site(String url){
        if(!Address.isWeb(url))return "";
        try{
            URI uri=new URI(url);String host=uri.getHost();if(host==null||uri.getUserInfo()!=null)return "";
            host=host.toLowerCase(Locale.ROOT);if(host.startsWith("www."))host=host.substring(4);
            if("musikmathe.invalid".equals(host))return "";
            int port=uri.getPort();boolean standard=port<0||("http".equalsIgnoreCase(uri.getScheme())&&port==80)||("https".equalsIgnoreCase(uri.getScheme())&&port==443);
            return host+(standard?"":":"+port);
        }catch(Exception invalid){return "";}
    }
    static List<BrowserStore.Entry> top(List<BrowserStore.Entry> history){
        LinkedHashMap<String,Site> sites=new LinkedHashMap<>();
        for(BrowserStore.Entry row:history){String key=site(row.url);if(key.isEmpty())continue;Site site=sites.get(key);if(site==null){site=new Site(row);sites.put(key,site);}site.include(row);}
        ArrayList<Site> ranked=new ArrayList<>(sites.values());
        ranked.sort((a,b)->{int byCount=Long.compare(b.count,a.count);return byCount!=0?byCount:Long.compare(b.last,a.last);});
        ArrayList<BrowserStore.Entry> result=new ArrayList<>();
        for(int i=0;i<Math.min(3,ranked.size());i++){Site site=ranked.get(i);BrowserStore.Entry page=site.page;result.add(new BrowserStore.Entry(page.url,page.title,site.last,false,site.count));}
        return result;
    }
}
