(function(token, embedded) {
  'use strict';
  var key = '__bestBrowserMediaLinks', state = window[key];
  if (!state || state.token !== token) {
    state = window[key] = {token: token, roots: [document], seen: [], walker: null, nodes: 0};
  }
  var items = [], examined = 0;
  function put(url, type, name, base) {
    if (typeof url !== 'string' || !url || url.length > 8192) return;
    try {
      var absolute = new URL(url, base || document.baseURI);
      if (/^https?:$/.test(absolute.protocol) && absolute.href.length <= 8192) items.push({url: absolute.href, mime: (type || '').slice(0,128), download: (name || '').slice(0,512)});
    } catch (ignored) {}
  }
  while (examined < 512 && items.length < 100) {
    if (!state.walker) {
      var root = state.roots.shift();
      if (!root) break;
      if (state.seen.indexOf(root) >= 0) continue;
      state.seen.push(root);
      state.walker = root.ownerDocument ? root.ownerDocument.createTreeWalker(root, 1) : root.createTreeWalker(root, 1);
    }
    var element;
    try { element = state.walker.nextNode(); } catch (ignored) { element = null; }
    if (!element) { state.walker = null; continue; }
    examined++; state.nodes++;
    if (element.shadowRoot) state.roots.push(element.shadowRoot);
    var tag = (element.tagName || '').toLowerCase();
    if (tag === 'iframe') {
      try { if (element.contentDocument) state.roots.push(element.contentDocument); } catch (ignored) {}
    }
    if (tag === 'a' && element.getAttribute('href')) put(element.href, element.getAttribute('type'), element.getAttribute('download'), element.baseURI);
    if (embedded) {
      if (tag === 'img') put(element.currentSrc || element.src || element.getAttribute('data-src'), element.getAttribute('type'), '', element.baseURI);
      if (tag === 'video' || tag === 'audio') put(element.currentSrc || element.src, element.getAttribute('type'), '', element.baseURI);
      if (tag === 'source' && element.parentElement && /^(video|audio)$/i.test(element.parentElement.tagName)) put(element.src, element.getAttribute('type'), '', element.baseURI);
    }
  }
  var done = !state.walker && !state.roots.length, limited = state.nodes >= 100000;
  if (done || limited) delete window[key];
  return JSON.stringify({items: items, done: done || limited, limited: limited});
})
