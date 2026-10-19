package de.steffen.musikmathe;

import java.net.URI;
import java.net.URISyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Host matching for bundled/updated HaGeZi rules, with a small built-in fallback. */
final class AdBlocker {
    // Curated ad-delivery domains, not a downloaded or third-party filter list.
    private static final Set<String> AD_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adservice.google.de", "amazon-adsystem.com",
            "adnxs.com", "adnxs-simple.com", "adsrvr.org", "advertising.com",
            "adform.net", "adform.com", "adition.com", "adnami.io",
            "adroll.com", "adsafeprotected.com", "adtech.de", "adtechus.com",
            "advertising.amazon.com", "bidswitch.net", "bidr.io", "casalemedia.com",
            "contextweb.com", "criteo.com", "criteo.net", "demdex.net",
            "doubleverify.com", "flashtalking.com", "gumgum.com", "imrworldwide.com",
            "indexww.com", "innovid.com", "intentiq.com", "lijit.com",
            "media.net", "moatads.com", "openx.net", "openx.com",
            "outbrain.com", "outbrainimg.com", "pubmatic.com", "quantserve.com",
            "rubiconproject.com", "scorecardresearch.com", "serving-sys.com",
            "sharethrough.com", "smartadserver.com", "smartclip.net", "spotxchange.com",
            "taboola.com", "taboolasyndication.com", "teads.tv", "the-ozone-project.com",
            "tremorhub.com", "tribalfusion.com", "triplelift.com", "turn.com",
            "undertone.com", "yieldlab.net", "yieldmo.com", "yieldoptimizer.com",
            "ads.yahoo.com", "advertising.yahoo.com", "ads.youtube.com"
    )));
    private static volatile Set<String> hosts = AD_HOSTS;
    private static final Pattern DOMAIN = Pattern.compile("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern AD_SCRIPT = Pattern.compile("(?:^|/)(?:pagead|adsbygoogle)\\.js$|(?:^|/)widget/ads\\.js$",Pattern.CASE_INSENSITIVE);

    private AdBlocker() { }

    static Set<String> readHosts(Reader source) throws IOException {
        Set<String> result = new HashSet<>();
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim().toLowerCase(Locale.ROOT);
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!DOMAIN.matcher(line).matches()) throw new IOException("Ungültige Filterliste.");
            result.add(line);
            if (result.size() > 200000) throw new IOException("Filterliste ist zu groß.");
        }
        return result;
    }
    static void useHosts(Set<String> source) {
        Set<String> combined = new HashSet<>(source);combined.addAll(AD_HOSTS);
        hosts = Collections.unmodifiableSet(combined);
    }
    static int hostCount() { return hosts.size(); }
    static String host(String url) {
        if (url == null) return "";
        try {
            URI uri = new URI(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return "";
            String value = uri.getHost();
            if (value == null) return "";
            value = value.toLowerCase(Locale.ROOT);
            return value.endsWith(".") ? value.substring(0,value.length()-1) : value;
        } catch (URISyntaxException ignored) { return ""; }
    }
    static boolean excepted(String pageUrl, Set<String> exceptions) {
        return !exceptionHost(pageUrl,exceptions).isEmpty();
    }
    static String exceptionHost(String pageUrl, Set<String> exceptions) {
        String pageHost = host(pageUrl);
        String best="";
        for (String allowed : exceptions) if ((pageHost.equals(allowed) || pageHost.endsWith("."+allowed))&&allowed.length()>best.length())best=allowed;
        return best;
    }

    static boolean shouldBlock(String url, boolean mainFrame, boolean enabled) {
        // Visiting a link explicitly must still work, including a filter-listed domain.
        if (!enabled || mainFrame || url == null) return false;
        String host = host(url);
        if(host.isEmpty())return false;
        try{if(AD_SCRIPT.matcher(new URI(url).getPath()).find())return true;}catch(URISyntaxException ignored){}
        Set<String> snapshot = hosts;
        // Compare whole DNS labels: an ad domain in a path, query or userinfo is harmless.
        while (!host.isEmpty()) {
            if (snapshot.contains(host)) return true;
            int dot = host.indexOf('.');
            if (dot < 0) break;
            host = host.substring(dot + 1);
        }
        return false;
    }
}
