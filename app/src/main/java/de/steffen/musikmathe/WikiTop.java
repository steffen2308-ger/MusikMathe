package de.steffen.musikmathe;

import android.net.Uri;
import android.webkit.WebResourceResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** On-demand country ranking. No visitor URL, browser cookie or device location is sent. */
final class WikiTop {
    static final String URL = "https://musikmathe.invalid/wikipedia-de";
    static final class Article {
        final String title, project, url;
        final long views;
        final int rank;
        Article(String title,String project,long views,int rank) {
            this.title=title.replace('_',' ');this.project=project;this.views=views;this.rank=rank;
            url="https://"+project+".org/wiki/"+Uri.encode(title);
        }
    }
    static List<Article> parse(String json) throws Exception {
        JSONObject item=new JSONObject(json).getJSONArray("items").getJSONObject(0);
        if (!"DE".equals(item.getString("country"))) throw new IllegalArgumentException("Falsches Land");
        JSONArray rows=item.getJSONArray("articles");ArrayList<Article> articles=new ArrayList<>();
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);String title=row.optString("article"),project=row.optString("project");
            if(!project.matches("[a-z][a-z0-9-]*\\.wikipedia") || title.isEmpty() || title.contains(":") || title.equals("-") || title.equals("Main_Page") || title.equals("Hauptseite"))continue;
            articles.add(new Article(title,project,row.optLong("views_ceil",row.optLong("views")),row.optInt("rank",i+1)));
        }
        Collections.sort(articles,(a,b)->Integer.compare(a.rank,b.rank));
        if(articles.isEmpty())throw new IllegalArgumentException("Keine Artikel verfügbar");
        return articles.subList(0,Math.min(50,articles.size()));
    }
    static String endpoint(Calendar day) {
        SimpleDateFormat format=new SimpleDateFormat("yyyy/MM/dd",Locale.ROOT);format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return "https://wikimedia.org/api/rest_v1/metrics/pageviews/top-per-country/DE/all-access/"+format.format(day.getTime());
    }
    static String escape(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    static String html(List<Article> articles,String date,String source) {
        StringBuilder list=new StringBuilder("<p>Meistgelesene Artikel aus Deutschland, alle Wikipedia-Sprachen. Stand: "+escape(date)+" (UTC).</p><p>Wikimedia misst Artikelaufrufe, keine Suchanfragen. Die Aufrufzahlen sind gerundete Obergrenzen.</p><ol>");
        java.text.NumberFormat numbers=java.text.NumberFormat.getIntegerInstance(Locale.GERMANY);
        for(Article article:articles)list.append("<li><a href=\"").append(escape(article.url)).append("\">").append(escape(article.title)).append("</a><small>").append(escape(article.project)).append(" · bis zu ").append(numbers.format(article.views)).append(" Aufrufe</small></li>");
        return page(list.append("</ol><p><a href=\"").append(escape(source)).append("\">Quelle: Wikimedia Analytics API · Deutschland</a></p>").toString());
    }
    private static String page(String body) {
        return "<!doctype html><html lang=de><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>Wikipedia · Deutschland</title><style>body{font:16px system-ui;background:#f8faf9;color:#173e39;margin:0;padding:24px;line-height:1.6;max-width:760px;margin:auto}h1{font-size:28px;line-height:1.2}a{color:#17685f}p{color:#526d63}li{padding:14px 4px;border-bottom:1px solid #dce6e1}small{display:block;color:#60756f}ol{padding-left:26px}</style><h1>Wikipedia · Deutschland</h1>"+body+"</html>";
    }
    static WebResourceResponse response() {
        String content;
        try {
            Calendar day=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            content=null;
            for(int back=0;back<7;back++) {
                day.add(Calendar.DAY_OF_MONTH,-1);String source=endpoint(day);
                HttpURLConnection connection=(HttpURLConnection)new URL(source).openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent","BestBrowser/0.4.3 (https://github.com/steffen2308-ger/MusikMathe)");
                connection.setRequestProperty("Accept","application/json");
                try {
                    int status=connection.getResponseCode();
                    if(status==404)continue;
                    if(status!=200)throw new java.io.IOException("HTTP "+status);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream input=connection.getInputStream()) {
                        byte[] buffer=new byte[8192];int count;
                        while((count=input.read(buffer))!=-1) { if(bytes.size()+count>2*1024*1024)throw new java.io.IOException("Antwort zu groß");bytes.write(buffer,0,count); }
                    }
                    List<Article> articles=parse(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                    SimpleDateFormat date=new SimpleDateFormat("dd.MM.yyyy",Locale.GERMANY);date.setTimeZone(TimeZone.getTimeZone("UTC"));
                    content=html(articles,date.format(day.getTime()),source);break;
                } finally { connection.disconnect(); }
            }
            if(content==null)throw new java.io.IOException("Keine aktuellen Daten");
        } catch(Exception error) {
            content=page("<p>Die aktuelle Deutschland-Rangliste ist gerade nicht verfügbar. Prüfe deine Internetverbindung und versuche es später erneut.</p><p><a href='"+URL+"'>Erneut laden</a></p><p><a href='https://doc.wikimedia.org/generated-data-platform/aqs/analytics-api/reference/page-views.html'>Datenquelle: Wikimedia Analytics</a></p>");
        }
        WebResourceResponse response=new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        java.util.Map<String,String> headers=new java.util.HashMap<>();headers.put("Cache-Control","no-store");headers.put("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'");response.setResponseHeaders(headers);return response;
    }
}
