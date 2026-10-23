(function () {
  var links = [], seen = Object.create(null);
  var nodes = document.querySelectorAll('a[href],area[href]');
  for (var i = 0; i < Math.min(nodes.length, 512); i++) {
    var a = nodes[i], href = a.href;
    if (!href || href.length > 4096 || seen[href]) continue;
    seen[href] = true;
    links.push({url: href, label: String(a.textContent || a.getAttribute('aria-label') || '').slice(0, 200), download: a.hasAttribute('download'), rel: a.rel || ''});
  }
  return JSON.stringify({page: location.href, links: links});
})();
