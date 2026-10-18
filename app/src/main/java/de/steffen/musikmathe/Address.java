package de.steffen.musikmathe;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;

/** Treat typed text as a search unless it is a usable web address. */
final class Address {
    static final String HOME = "file:///android_asset/start.html";
    static boolean isWeb(String value) {
        if (value == null || value.matches("(?s).*\\s.*")) return false;
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getRawAuthority() != null && !uri.getRawAuthority().isEmpty()
                    && !uri.getRawAuthority().contains("\\");
        } catch (Exception ignored) { return false; }
    }
    static String resolve(String value, String engine) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) return HOME;
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.matches("^(javascript|data|file|content|intent|about):.*")) return null;
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            // Escape ordinary spaces in a pasted URL, while keeping its query and fragment.
            String url = text.replace(" ", "%20");
            return isWeb(url) ? url : null;
        }
        if (!text.matches("(?s).*\\s.*") && !text.contains("@")) {
            String host = text.split("[/?#]", 2)[0];
            if ((host.contains(".") && !host.startsWith(".") && !host.endsWith("."))
                    || host.matches("localhost(:[0-9]+)?") || host.startsWith("[")) {
                String url = "https://" + text;
                if (isWeb(url)) return url;
            }
        }
        String base = "duckduckgo".equals(engine) ? "https://duckduckgo.com/?q="
                : "bing".equals(engine) ? "https://www.bing.com/search?q=" : "https://www.google.com/search?q=";
        try { return base + URLEncoder.encode(text, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    static String display(String url) { return HOME.equals(url) || "about:blank".equals(url) ? "" : url; }
    static String host(String url) {
        try { String host = new URI(url).getHost(); return host == null ? url : host; }
        catch (Exception ignored) { return url; }
    }
}
