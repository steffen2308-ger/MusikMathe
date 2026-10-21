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
    if (hit === video || (hit && video.contains(hit))) return true;
    var player = video.closest('.html5-video-player,.video-js,.plyr,.jwplayer,[data-player]');
    return !!(player && hit && player.contains(hit));
  }
  function walk(w, x, y, sx, sy, clip, depth, parents) {
    if (++visits > 12 || rows.length >= 24) return;
    try {
      var doc = w.document, videos = doc.querySelectorAll('video');
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
        var cx = visible.right - hitX * .6, cy = visible.top + hitY * .6;
        if (!allowedHit(doc, video, (cx - x) / sx, (cy - y) / sy)) continue;
        if (!parents.every(function (p) { return p.document.elementFromPoint((cx - p.x) / p.sx, (cy - p.y) / p.sy) === p.frame; })) continue;
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
      if (depth < 2) {
        var frames = doc.querySelectorAll('iframe,frame');
        for (var j = 0; j < Math.min(frames.length, 8) && visits < 12; j++) {
          var frame = frames[j], fr = frame.getBoundingClientRect(), fx = frame.offsetWidth ? fr.width / frame.offsetWidth : 1, fy = frame.offsetHeight ? fr.height / frame.offsetHeight : 1;
          if (fr.width <= 0 || fr.height <= 0) continue;
          var originX = x + (fr.left + frame.clientLeft * fx) * sx, originY = y + (fr.top + frame.clientTop * fy) * sy;
          var childClip = intersect(clip, {left: originX, top: originY, right: originX + frame.clientWidth * fx * sx, bottom: originY + frame.clientHeight * fy * sy});
          walk(frame.contentWindow, originX, originY, fx * sx, fy * sy, childClip, depth + 1, parents.concat([{document: doc, frame: frame, x: x, y: y, sx: sx, sy: sy}]));
        }
      }
    } catch (ignored) { /* Cross-origin frames remain isolated; the existing video list is still available. */ }
  }
  if (width > 0 && height > 0 && hitX > 0 && hitY > 0) walk(root, 0, 0, 1, 1, {left: left, top: top, right: left + width, bottom: top + height}, 0, []);
  return JSON.stringify({href: text(root.location.href, 8192), width: width, height: height, rows: rows});
})
