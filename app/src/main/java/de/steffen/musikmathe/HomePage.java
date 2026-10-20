package de.steffen.musikmathe;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** History is delivered only to the exact local start page, without an Android JS bridge. */
final class HomePage {
    static String update(List<BrowserStore.Entry> sites,boolean privateMode){
        JSONArray rows=new JSONArray();
        if(!privateMode)for(BrowserStore.Entry site:sites){
            String host=FrequentSites.site(site.url);if(host.isEmpty())continue;
            try{rows.put(new JSONObject().put("url",site.url).put("title",site.title).put("host",host));}catch(Exception ignored){}
            if(rows.length()==3)break;
        }
        return ("(function(){if(location.href!=="+JSONObject.quote(Address.HOME)+"||!window.BestBrowserHome)return;window.BestBrowserHome.update("+rows+","+privateMode+");})()")
            .replace("\u2028","\\u2028").replace("\u2029","\\u2029");
    }
}
