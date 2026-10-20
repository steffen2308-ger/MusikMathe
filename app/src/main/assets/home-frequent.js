(function () {
  'use strict';
  var home = 'file:///android_asset/start.html';
  var palette = ['#17685f', '#365d9d', '#784b83', '#875422', '#855062', '#406849'];
  function knownIcon(host) {
    var links = document.querySelectorAll('nav[data-fixed] a');
    for (var i = 0; i < links.length; i++) {
      var linkHost = new URL(links[i].href).hostname.toLowerCase().replace(/^www\./, '');
      if (linkHost === host) return links[i].querySelector('.tile-icon').cloneNode(true);
    }
    return null;
  }
  window.BestBrowserHome = {
    update: function (rows, privateMode) {
      if (location.href !== home) return;
      var section = document.getElementById('frequent-sites');
      var list = document.getElementById('frequent-links');
      var empty = document.getElementById('frequent-empty');
      if (!section || !list || !empty) return;
      while (list.firstChild) list.removeChild(list.firstChild);
      section.hidden = !!privateMode;
      if (privateMode) return;
      (Array.isArray(rows) ? rows : []).slice(0, 3).forEach(function (row) {
        var url;
        try { url = new URL(row.url); } catch (invalid) { return; }
        if (!/^https?:$/.test(url.protocol) || url.username || url.password) return;
        var host = url.hostname.toLowerCase().replace(/^www\./, '');
        if (host === 'musikmathe.invalid') return;
        var label = String(row.title || row.host || host);
        var link = document.createElement('a');
        link.href = url.href;
        link.title = label + ' · ' + String(row.host || host);
        var icon = knownIcon(host);
        if (!icon) {
          icon = document.createElement('span');
          icon.className = 'tile-icon monogram';
          icon.setAttribute('aria-hidden', 'true');
          icon.textContent = host.charAt(0).toUpperCase();
          var hash = 0;
          for (var i = 0; i < host.length; i++) hash = ((hash * 31) + host.charCodeAt(i)) >>> 0;
          icon.style.backgroundColor = palette[hash % palette.length];
        }
        var copy = document.createElement('span');
        copy.className = 'tile-copy';
        var title = document.createElement('span');
        title.className = 'tile-label';
        title.textContent = label;
        var domain = document.createElement('small');
        domain.textContent = String(row.host || host);
        copy.appendChild(title);
        copy.appendChild(domain);
        link.appendChild(icon);
        link.appendChild(copy);
        list.appendChild(link);
      });
      empty.hidden = list.children.length !== 0;
    }
  };
})();
