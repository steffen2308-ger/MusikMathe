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
        final boolean desktop;
        Entry(String url, String title, long time) { this(url, title, time, false); }
        Entry(String url, String title, long time, boolean desktop) {
            this.url = url; this.title = title == null || title.isEmpty() ? url : title; this.time = time; this.desktop = desktop;
        }
    }
    private final SharedPreferences prefs;
    private final boolean privateMode;
    private String privateEngine;
    private boolean privateAdBlocking;
    private final Set<String> privateAdBlockExceptions;
    BrowserStore(Context context) { this(context, false); }
    BrowserStore(Context context, boolean privateMode) {
        prefs = context.getSharedPreferences("browser", Context.MODE_PRIVATE);
        this.privateMode = privateMode;
        privateEngine = prefs.getString("engine", "google");
        privateAdBlocking = prefs.getBoolean("ad_blocking", true);
        privateAdBlockExceptions = new HashSet<>(prefs.getStringSet("ad_block_exceptions",Collections.emptySet()));
    }
    List<Entry> read(String key) {
        ArrayList<Entry> result = new ArrayList<>();
        if (privateMode && !"bookmarks".equals(key)) return result;
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            int limit = "tabs".equals(key) ? MAX_TABS : 500;
            for (int i = 0; i < array.length() && result.size() < limit; i++) {
                JSONObject row = array.optJSONObject(i);
                if (row == null) continue;
                String url = row.optString("url");
                if (!Address.isWeb(url) && !("tabs".equals(key) && Address.HOME.equals(url))) continue;
                result.add(new Entry(url, row.optString("title"), row.optLong("time"), row.optBoolean("desktop")));
            }
        } catch (Exception ignored) { }
        return result;
    }
    void write(String key, List<Entry> rows) {
        if (privateMode) return;
        JSONArray array = new JSONArray();
        for (Entry row : rows) {
            try { array.put(new JSONObject().put("url", row.url).put("title", row.title).put("time", row.time).put("desktop", row.desktop)); }
            catch (Exception ignored) { }
        }
        prefs.edit().putString(key, array.toString()).apply();
    }
    void visit(String url, String title) {
        if (privateMode || !Address.isWeb(url)) return;
        List<Entry> history = read("history");
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).url.equals(url)) history.remove(i);
        history.add(0, new Entry(url, title, System.currentTimeMillis()));
        while (history.size() > 500) history.remove(history.size() - 1);
        write("history", history);
    }
    boolean bookmarked(String url) { for (Entry row : read("bookmarks")) if (row.url.equals(url)) return true; return false; }
    boolean toggleBookmark(String url, String title) {
        if (privateMode || !Address.isWeb(url)) return false;
        List<Entry> rows = read("bookmarks");
        boolean removed = false;
        for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).url.equals(url)) { rows.remove(i); removed = true; }
        if (!removed) rows.add(0, new Entry(url, title, System.currentTimeMillis()));
        write("bookmarks", rows); return !removed;
    }
    int selected() { return privateMode ? 0 : prefs.getInt("selected", 0); }
    void selected(int index) { if (!privateMode) prefs.edit().putInt("selected", index).apply(); }
    String engine() { return privateMode ? privateEngine : prefs.getString("engine", "google"); }
    void engine(String engine) { if (privateMode) privateEngine = engine; else prefs.edit().putString("engine", engine).apply(); }
    boolean adBlockingEnabled() { return privateMode ? privateAdBlocking : prefs.getBoolean("ad_blocking", true); }
    void adBlockingEnabled(boolean enabled) {
        if (privateMode) privateAdBlocking = enabled;
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
        if(privateMode){privateAdBlockExceptions.clear();privateAdBlockExceptions.addAll(next);}
        else prefs.edit().putStringSet("ad_block_exceptions",next).apply();
    }
}

