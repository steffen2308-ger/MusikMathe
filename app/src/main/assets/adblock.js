(function () {
    'use strict';
    if (window.top !== window || !/^https?:$/.test(location.protocol)) return;
    var signature = '__CONFIG_KEY__', previous = window.__bestBrowserAdblock;
    if (previous && previous.signature === signature && previous.installed()) return;
    if (previous) previous.stop();
    var host = location.hostname.toLowerCase().replace(/\.$/, '');
    function matches(domain) { return host === domain || host.endsWith('.' + domain); }
    var allowedSites = '__SITE_EXCEPTIONS__';
    if (allowedSites.some(matches)) return;
    var filters = '__FILTER_DATA__';
    var selectors = new Set(filters.generic), exceptions = new Set();
    filters.rules.forEach(function (rule) {
        if ((!rule[0].length || rule[0].some(matches)) && !rule[1].some(matches)) {
            (rule[3] ? exceptions : selectors).add(rule[2]);
        }
    });
    exceptions.forEach(function (selector) { selectors.delete(selector); });
    // A separate CSS rule for each selector isolates invalid/unsupported selector syntax.
    var css = Array.from(selectors).map(function (s) { return s + '{display:none!important;}'; }).join('\n');
    var sheet = null, style = null, observer = null, stopped = false;
    function install() {
        if (stopped || sheet || style) return;
        if (typeof CSSStyleSheet !== 'undefined' && CSSStyleSheet.prototype.replaceSync && 'adoptedStyleSheets' in document) {
            try {
                var candidate = new CSSStyleSheet();
                candidate.replaceSync(css);
                document.adoptedStyleSheets = Array.from(document.adoptedStyleSheets).concat(candidate);
                sheet = candidate;
                return;
            } catch (ignored) { /* Older providers use an ordinary stylesheet. */ }
        }
        var parent = document.head || document.documentElement;
        if (!parent) return;
        style = document.createElement('style');
        style.setAttribute('data-bestbrowser-adblock', '');
        style.textContent = css;
        parent.appendChild(style);
    }
    window.__bestBrowserAdblock = {signature: signature, installed: function () {
        if (stopped) return false;
        if (sheet) return document.adoptedStyleSheets.indexOf(sheet) >= 0;
        if (style) return !!(document.documentElement && document.documentElement.contains(style));
        return !document.head && !document.documentElement;
    }, stop: function () {
        stopped = true;
        if (observer) observer.disconnect();
        document.removeEventListener('DOMContentLoaded', install);
        if (style && style.parentNode) style.parentNode.removeChild(style);
        if (sheet) document.adoptedStyleSheets = Array.from(document.adoptedStyleSheets).filter(function (s) { return s !== sheet; });
        delete window.__bestBrowserAdblock;
    }};
    install();
    if (!style && !sheet) {
        observer = new MutationObserver(function () {
            install();
            if (style || sheet) observer.disconnect();
        });
        observer.observe(document, {childList: true, subtree: true});
        document.addEventListener('DOMContentLoaded', install);
    }
})();
