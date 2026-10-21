(function (fractionX, fractionY, captureId) {
  'use strict';
  var root = window, d = root.document, viewport = root.visualViewport;
  var left = viewport ? viewport.offsetLeft : 0, top = viewport ? viewport.offsetTop : 0;
  var width = viewport ? viewport.width : root.innerWidth, height = viewport ? viewport.height : root.innerHeight;
  var rows = [], visits = 0, state = root.__mmVideoMarkerState;
  if (!state || state.document !== d || !(state.ids instanceof WeakMap)) {
    state = {document: d, ids: new WeakMap(), next: 0}; root.__mmVideoMarkerState = state;
  }
  var hitX = width * fractionX, hitY = height * fractionY;
  function text(value, limit) { return typeof value === 'string' ? value.slice(0, limit) : ''; }
  function positive(value) { return typeof value === 'number' && isFinite(value) && value > 0 ? value : 0; }
  function intersect(a, b) {
    return {left: Math.max(a.left, b.left), top: Math.max(a.top, b.top), right: Math.min(a.right, b.right), bottom: Math.min(a.bottom, b.bottom)};
  }
  function bounds(rect, x, y, sx, sy) {
    return {left: x + rect.left * sx, top: y + rect.top * sy, right: x + rect.right * sx, bottom: y + rect.bottom * sy};
  }
  function allowedHit(doc, video, x, y) {
    var hit = doc.elementFromPoint(x, y);
    while (hit && hit.shadowRoot && hit.shadowRoot.elementFromPoint) {
      var inner = hit.shadowRoot.elementFromPoint(x, y); if (!inner || inner === hit) break; hit = inner;
    }
    if (hit === video || (hit && video.contains(hit))) return true;
    if (!hit || (hit.closest && hit.closest('[role="dialog"],[aria-modal="true"]'))) return false;
    // Mobile players often put a transparent touch/control layer beside the video.
    // Accept siblings only in a tight player-sized container, never the whole page.
    var vr = video.getBoundingClientRect();
    for (var parent = video.parentElement, n = 0; parent && n < 8; parent = parent.parentElement, n++) {
      if (/^(BODY|HTML)$/.test(parent.tagName)) break;
      var pr = parent.getBoundingClientRect();
      if (pr.width >= vr.width * .9 && pr.height >= vr.height * .9 && pr.width <= vr.width * 1.4 && pr.height <= vr.height * 1.4 && parent.contains(hit)) return true;
    }
    var player = video.closest('.html5-video-player,.video-js,.plyr,.jwplayer,[data-player],#player,#player-container-id');
    return !!(player && player.contains(hit));
  }
  function videoLink(value, base) {
    try {
      var u = new URL(value, base), h = u.hostname.toLowerCase(), id = '';
      if (u.protocol !== 'https:') return null;
      if (h === 'youtube.com' || /\.youtube(?:-nocookie)?\.com$/.test(h) || h === 'youtube-nocookie.com') {
        var m = u.pathname.match(/^\/(?:shorts|embed|live)\/([\w-]{11})(?:\/|$)/);
        id = u.pathname === '/watch' ? u.searchParams.get('v') : (m && m[1]);
        if (id && /^[\w-]{11}$/.test(id)) return {url: 'https://www.youtube.com/watch?v=' + id, youtubeId: id};
      }
      if ((h === 'n-tv.de' || h === 'www.n-tv.de') && /^\/mediathek\/(?:videos|sendungen)\/.+\.html$/.test(u.pathname)) return {url: u.origin + u.pathname, youtubeId: ''};
    } catch (ignored) {}
    return null;
  }
  function scopes(doc) {
    var out = [doc];
    // Open shadow roots occur in custom mobile players. Bound the walk per sample.
    try {
      var walker = doc.createTreeWalker(doc.documentElement, 1), node, n = 0;
      while ((node = walker.nextNode()) && n++ < 2000 && out.length < 16) if (node.shadowRoot) out.push(node.shadowRoot);
    } catch (ignored) {}
    return out;
  }
  function walk(w, x, y, sx, sy, clip, depth, parents) {
    if (++visits > 12 || rows.length >= 24) return;
    try {
      var doc = w.document, roots = scopes(doc), videos = [], frames = [], links = [];
      roots.forEach(function (scope) {
        videos = videos.concat(Array.from(scope.querySelectorAll('video')).slice(0, 64));
        frames = frames.concat(Array.from(scope.querySelectorAll('iframe,frame')).slice(0, 32));
        links = links.concat(Array.from(scope.querySelectorAll('a[href]')).slice(0, 300));
      });
      for (var i = 0; i < Math.min(videos.length, 64) && rows.length < 24; i++) {
        var video = videos[i], style = w.getComputedStyle(video);
        if (video.closest('.ad-showing,.ad-interrupting')) continue;
        if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0 || !video.getClientRects().length) continue;
        var rect = video.getBoundingClientRect(), visible = intersect(bounds(rect, x, y, sx, sy), clip);
        for (var parent = video.parentElement, n = 0; parent && n < 16; parent = parent.parentElement, n++) {
          var ps = w.getComputedStyle(parent);
          if (ps.display === 'none' || ps.visibility === 'hidden' || Number(ps.opacity) === 0) { visible.right = visible.left; break; }
          var pb = bounds(parent.getBoundingClientRect(), x, y, sx, sy);
          if (/hidden|clip|scroll|auto/.test(ps.overflowX)) { visible.left = Math.max(visible.left, pb.left); visible.right = Math.min(visible.right, pb.right); }
          if (/hidden|clip|scroll|auto/.test(ps.overflowY)) { visible.top = Math.max(visible.top, pb.top); visible.bottom = Math.min(visible.bottom, pb.bottom); }
        }
        if (visible.right - visible.left < hitX * 1.2 || visible.bottom - visible.top < hitY * 1.2) continue;
        var point = null;
        [[visible.right - hitX * .6, visible.top + hitY * .6], [visible.left + hitX * .6, visible.top + hitY * .6], [visible.right - hitX * .6, visible.bottom - hitY * .6]].some(function (p) {
          if (!allowedHit(doc, video, (p[0] - x) / sx, (p[1] - y) / sy)) return false;
          if (!parents.every(function (ancestor) { return ancestor.document.elementFromPoint((p[0] - ancestor.x) / ancestor.sx, (p[1] - ancestor.y) / ancestor.sy) === ancestor.frame; })) return false;
          point = p; return true;
        });
        if (!point) continue;
        var cx = point[0], cy = point[1];
        var id = state.ids.get(video); if (!id) { id = String(++state.next); state.ids.set(video, id); }
        var current = text(video.currentSrc || video.src, 8192), sources = [], seen = {};
        function add(url, type) {
          url = text(url, 8192); if (!url) return;
          if (seen[url]) { if (type && seen[url].type === 'video/unknown') seen[url].type = text(type, 200); return; }
          if (sources.length >= 12) return;
          var source = {url: url, type: text(type, 200) || 'video/unknown', current: url === current}; seen[url] = source; sources.push(source);
        }
        add(current, video.getAttribute('type')); add(video.src, video.getAttribute('type'));
        video.querySelectorAll('source').forEach(function (source) { add(source.src, source.type); });
        try {
          if (w.videojs && typeof w.videojs.getPlayer === 'function') {
            var owner = video.closest('.video-js'), api = owner && w.videojs.getPlayer(owner.id);
            if (api && api.el && api.el().contains(video) && api.currentSources) api.currentSources().slice(0, 12).forEach(function (s) { add(s.src, s.type); });
          }
        } catch (ignored) {}
        var thumbnail = text(video.poster, 8192);
        if (id === captureId && !thumbnail && video.readyState >= 2 && video.videoWidth > 0) try {
          var canvas = doc.createElement('canvas'); canvas.width = 160; canvas.height = 90;
          canvas.getContext('2d').drawImage(video, 0, 0, 160, 90); thumbnail = canvas.toDataURL('image/jpeg', .7);
        } catch (ignored) {}
        var youtubeId = '';
        try { var player = video.closest('.html5-video-player'); if (player && typeof player.getVideoData === 'function') youtubeId = text(player.getVideoData().video_id, 32); } catch (ignored) {}
        rows.push({id: id, x: cx - left, y: cy - top, page: text(w.location.href, 8192), main: w === root, count: videos.length,
          sources: sources, youtubeId: youtubeId, title: text(video.title || video.getAttribute('aria-label') || (videos.length === 1 ? doc.title : ''), 2000),
          thumbnail: thumbnail, width: positive(video.videoWidth), height: positive(video.videoHeight), duration: positive(video.duration), live: video.duration === Infinity});
      }
      // A homepage's thumbnails are links, not <video> elements. Mark their
      // individual previews as well, without mistaking the HTML page for a stream.
      var targets = {};
      for (var k = 0; k < Math.min(links.length, 300) && rows.length < 24; k++) {
        var link = links[k], target = videoLink(link.href, w.location.href), image = link.querySelector('img');
        if (!target || !image || targets[target.url]) continue;
        var imageStyle = w.getComputedStyle(image), linkStyle = w.getComputedStyle(link);
        if (imageStyle.visibility === 'hidden' || Number(imageStyle.opacity) === 0 || linkStyle.visibility === 'hidden' || Number(linkStyle.opacity) === 0) continue;
        if (videos.some(function (v) { return link.contains(v); })) continue;
        var ir = image.getBoundingClientRect(), box = intersect(bounds(ir, x, y, sx, sy), clip);
        if (box.right - box.left < hitX * 1.2 || box.bottom - box.top < hitY * 1.2) continue;
        var px = box.right - hitX * .6, py = box.top + hitY * .6, hit = doc.elementFromPoint((px - x) / sx, (py - y) / sy);
        if (!hit || !link.contains(hit)) continue;
        if (!parents.every(function (p) { return p.document.elementFromPoint((px - p.x) / p.sx, (py - p.y) / p.sy) === p.frame; })) continue;
        var lid = state.ids.get(link); if (!lid) { lid = String(++state.next); state.ids.set(link, lid); }
        targets[target.url] = true;
        rows.push({id: lid, x: px - left, y: py - top, kind: 'link', target: target.url, page: text(w.location.href, 8192), main: w === root,
          youtubeId: target.youtubeId, title: text(link.getAttribute('aria-label') || link.title || image.alt || link.textContent, 2000), thumbnail: text(image.currentSrc || image.src, 8192), sources: []});
      }
      if (depth < 2) {
        for (var j = 0; j < Math.min(frames.length, 32) && rows.length < 24; j++) {
          var frame = frames[j], fr = frame.getBoundingClientRect(), fx = frame.offsetWidth ? fr.width / frame.offsetWidth : 1, fy = frame.offsetHeight ? fr.height / frame.offsetHeight : 1;
          if (fr.width <= 0 || fr.height <= 0) continue;
          var originX = x + (fr.left + frame.clientLeft * fx) * sx, originY = y + (fr.top + frame.clientTop * fy) * sy;
          var childClip = intersect(clip, {left: originX, top: originY, right: originX + frame.clientWidth * fx * sx, bottom: originY + frame.clientHeight * fy * sy});
          var before = rows.length;
          try { if (frame.contentWindow.document && visits < 12) walk(frame.contentWindow, originX, originY, fx * sx, fy * sy, childClip, depth + 1, parents.concat([{document: doc, frame: frame, x: x, y: y, sx: sx, sy: sy}])); } catch (ignored) {}
          if (rows.length !== before) continue;
          // The parent can locate a known embedded player without reading its
          // cross-origin document. Its separately labelled page search stays explicit.
          try {
            var fu = new URL(frame.src, w.location.href), fh = fu.hostname;
            if (fu.protocol !== 'https:' || !/(^|\.)(youtube(?:-nocookie)?\.com|rtl\.de|n-tv\.de)$/.test(fh) || !/\/embed\/|player|video/i.test(fu.pathname)) continue;
            if (childClip.right - childClip.left < hitX * 1.2 || childClip.bottom - childClip.top < hitY * 1.2) continue;
            var ex = childClip.right - hitX * .6, ey = childClip.top + hitY * .6;
            if (doc.elementFromPoint((ex - x) / sx, (ey - y) / sy) !== frame) continue;
            if (!parents.every(function (p) { return p.document.elementFromPoint((ex - p.x) / p.sx, (ey - p.y) / p.sy) === p.frame; })) continue;
            var fid = state.ids.get(frame); if (!fid) { fid = String(++state.next); state.ids.set(frame, fid); }
            rows.push({id: fid, x: ex - left, y: ey - top, kind: 'frame', page: text(fu.href, 8192), title: text(frame.title || 'Eingebetteter Videoplayer', 2000), sources: []});
          } catch (ignored) {}
        }
      }
    } catch (ignored) { /* Cross-origin frames remain isolated; the existing video list is still available. */ }
  }
  if (width > 0 && height > 0 && hitX > 0 && hitY > 0) walk(root, 0, 0, 1, 1, {left: left, top: top, right: left + width, bottom: top + height}, 0, []);
  return JSON.stringify({href: text(root.location.href, 8192), width: width, height: height, rows: rows});
})
