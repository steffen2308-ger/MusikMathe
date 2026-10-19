package de.steffen.musikmathe;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Small, offline host filter. It deliberately does not implement Chrome extensions. */
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

    private AdBlocker() { }

    static boolean shouldBlock(String url, boolean mainFrame, boolean enabled) {
        // Visiting a link explicitly must still work, including a filter-listed domain.
        if (!enabled || mainFrame || url == null) return false;
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            // Compare whole DNS labels: an ad domain in a path, query or userinfo is harmless.
            while (!host.isEmpty()) {
                if (AD_HOSTS.contains(host)) return true;
                int dot = host.indexOf('.');
                if (dot < 0) break;
                host = host.substring(dot + 1);
            }
        } catch (URISyntaxException ignored) { }
        return false;
    }
}
