package de.steffen.musikmathe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

final class BrowserStore {
    static final int MAX_TABS = 20;
    static final class Entry {
        final String url, title;
        final long time;
        final long visits;
        final boolean desktop;
        Entry(String url, String title, long time) { this(url, title, time, false); }
        Entry(String url, String title, long time, boolean desktop) {
            this(url,title,time,desktop,1);
        }
        Entry(String url,String title,long time,boolean desktop,long visits){
            this.url = url; this.title = title == null || title.isEmpty() ? url : title; this.time = time; this.desktop = desktop; this.visits=Math.max(1,visits);
        }
    }
    private final SharedPreferences prefs;
    private final boolean privateMode;
    private final String tabPrefix;
    final PrivateProfiles.Session profile;
    private String privateEngine;
    private boolean privateAdBlocking;
    private final Set<String> privateAdBlockExceptions;
    BrowserStore(Context context) { this(context, false); }
    BrowserStore(Context context, boolean privateMode) { this(context,privateMode,""); }
    BrowserStore(Context context,boolean privateMode,String window) {
        prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE);
        this.privateMode = privateMode;
        tabPrefix=!privateMode&&"second".equals(window)?"second_window_":"";
        profile=privateMode?PrivateProfiles.active:null;
        privateEngine = profile==null?prefs.getString("engine", "google"):profile.string("engine","google");
        privateAdBlocking = profile==null?prefs.getBoolean("ad_blocking", true):profile.bool("ad_blocking",true);
        privateAdBlockExceptions = new HashSet<>();
        if(profile==null)privateAdBlockExceptions.addAll(prefs.getStringSet("ad_block_exceptions",Collections.emptySet()));
        else try{JSONArray list=new JSONArray(profile.string("ad_block_exceptions","[]"));for(int i=0;i<list.length();i++)privateAdBlockExceptions.add(list.getString(i));}catch(Exception ignored){}
    }
    List<Entry> read(String key) {
        ArrayList<Entry> result = new ArrayList<>();
        if (privateMode && profile==null) return result;
        try {
            JSONArray array = new JSONArray(profile==null?prefs.getString("tabs".equals(key)?tabPrefix+key:key, "[]"):profile.string(key,"[]"));
            int limit = "tabs".equals(key) ? MAX_TABS : 500;
            for (int i = 0; i < array.length() && result.size() < limit; i++) {
                JSONObject row = array.optJSONObject(i);
                if (row == null) continue;
                String url = row.optString("url");
                if (!Address.isWeb(url) && !("tabs".equals(key) && Address.HOME.equals(url))) continue;
                result.add(new Entry(url, row.optString("title"), row.optLong("time"), row.optBoolean("desktop"),row.optLong("visits",1)));
            }
        } catch (Exception ignored) { }
        return result;
    }
    void write(String key, List<Entry> rows) {
        if (privateMode&&profile==null) return;
        JSONArray array = new JSONArray();
        for (Entry row : rows) {
            try { JSONObject item=new JSONObject().put("url", row.url).put("title", row.title).put("time", row.time).put("desktop", row.desktop);if("history".equals(key))item.put("visits",row.visits);array.put(item); }
            catch (Exception ignored) { }
        }
        if(profile!=null)profile.put(key,array.toString());else prefs.edit().putString("tabs".equals(key)?tabPrefix+key:key, array.toString()).apply();
    }
    void visit(String url, String title) {
        if ((privateMode&&profile==null) || !Address.isWeb(url)) return;
        List<Entry> history = read("history");
        long visits=1;
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).url.equals(url)){visits=FrequentSites.add(visits,history.get(i).visits);history.remove(i);}
        history.add(0, new Entry(url, title, System.currentTimeMillis(),false,visits));
        while (history.size() > 500) history.remove(history.size() - 1);
        write("history", history);
    }
    List<Entry> frequentSites(){return privateMode&&profile==null?Collections.emptyList():FrequentSites.top(read("history"));}
    boolean bookmarked(String url) { for (Entry row : read("bookmarks")) if (row.url.equals(url)) return true; return false; }
    boolean toggleBookmark(String url, String title) {
        if ((privateMode&&profile==null) || !Address.isWeb(url)) return false;
        List<Entry> rows = read("bookmarks");
        boolean removed = false;
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).url.equals(url)) { rows.remove(i); removed = true; }
        if (!removed) rows.add(0, new Entry(url, title, System.currentTimeMillis()));
        write("bookmarks", rows); return !removed;
    }
    int selected() { return profile!=null?profile.integer("selected",0):privateMode ? 0 : prefs.getInt(tabPrefix+"selected", 0); }
    void selected(int index) { if(profile!=null)profile.put("selected",index);else if (!privateMode) prefs.edit().putInt(tabPrefix+"selected", index).apply(); }
    String engine() { return privateMode ? privateEngine : prefs.getString("engine", "google"); }
    boolean linkPreloadingEnabled(){return !privateMode&&prefs.getBoolean("wifi_link_preloading",true);}
    void linkPreloadingEnabled(boolean enabled){if(!privateMode)prefs.edit().putBoolean("wifi_link_preloading",enabled).apply();}
    void engine(String engine) { if (privateMode){privateEngine = engine;if(profile!=null)profile.put("engine",engine);} else prefs.edit().putString("engine", engine).apply(); }
    boolean adBlockingEnabled() { return privateMode ? privateAdBlocking : prefs.getBoolean("ad_blocking", true); }
    void adBlockingEnabled(boolean enabled) {
        if (privateMode){privateAdBlocking = enabled;if(profile!=null)profile.put("ad_blocking",enabled);}
        else prefs.edit().putBoolean("ad_blocking", enabled).apply();
    }
    Set<String> adBlockExceptions(){
        return new HashSet<>(privateMode?privateAdBlockExceptions:prefs.getStringSet("ad_block_exceptions",Collections.emptySet()));
    }
    void adBlockException(String host,boolean excepted){
        if(host.isEmpty())return;
        Set<String> next=adBlockExceptions();
        if(excepted){
            for(String existing:next)if(host.equals(existing)||host.endsWith("."+existing))return;
            next.removeIf(existing->existing.endsWith("."+host));next.add(host);
        }else next.remove(host);
        if(privateMode){privateAdBlockExceptions.clear();privateAdBlockExceptions.addAll(next);if(profile!=null)profile.put("ad_block_exceptions",new JSONArray(next).toString());}
        else prefs.edit().putStringSet("ad_block_exceptions",next).apply();
    }
}

