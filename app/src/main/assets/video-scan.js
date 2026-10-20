(function () {
  'use strict';
  var rows=[],seen={};
  function add(url,type,thumbnail,title) {
    if(!url)return;
    if(seen[url]) { if(thumbnail)seen[url].thumbnail=thumbnail;if(title)seen[url].title=title;return; }
    if(rows.length>=180)return;
    var row={url:url,type:type||'',thumbnail:thumbnail||'',title:title||''};seen[url]=row;rows.push(row);
  }
  function scan(w,depth) {
    try {
      var d=w.document,videos=d.querySelectorAll('video');
      videos.forEach(function(v) {
        var thumbnail=v.poster||'',title=v.title||v.getAttribute('aria-label')||(videos.length===1?d.title:'');
        // A loaded same-origin frame can also preview a player without a poster.
        if(!thumbnail&&v.readyState>=2&&v.videoWidth>0)try {
          var c=d.createElement('canvas');c.width=160;c.height=90;
          c.getContext('2d').drawImage(v,0,0,160,90);thumbnail=c.toDataURL('image/jpeg',0.7);
        }catch(ignored){}
        add(v.currentSrc||v.src,'video/unknown',thumbnail,title);
        v.querySelectorAll('source').forEach(function(s){add(s.src,s.type||'video/unknown',thumbnail,title);});
      });
      (w.__mmVideos||[]).forEach(function(e){add(e.url,e.type);});
      w.performance.getEntriesByType('resource').slice(-400).forEach(function(e){add(e.name,'');});
      if(depth<2)for(var i=0;i<Math.min(w.frames.length,8);i++)scan(w.frames[i],depth+1);
    }catch(ignored){}
  }
  scan(window,0);return JSON.stringify(rows);
})()
