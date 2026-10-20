(function () {
  'use strict';
  var rows=[],seen={};
  function positive(n){return typeof n==='number'&&isFinite(n)&&n>0?n:0;}
  function add(url,type,thumbnail,title,info) {
    if(!url)return;
    var row=seen[url];
    if(!row){if(rows.length>=180)return;row={url:url,type:type||'',thumbnail:'',title:''};seen[url]=row;rows.push(row);}
    if(type&&(!row.type||row.type==='video/unknown'))row.type=type;
    if(thumbnail)row.thumbnail=thumbnail;if(title)row.title=title;
    if(info)Object.keys(info).forEach(function(key){if(info[key]!==''&&info[key]!==0&&info[key]!==false)row[key]=info[key];});
  }
  function scan(w,depth) {
    try {
      var d=w.document,videos=d.querySelectorAll('video');
      videos.forEach(function(v) {
        var thumbnail=v.poster||'',title=v.title||v.getAttribute('aria-label')||(videos.length===1?d.title:''),current=v.currentSrc||v.src;
        var subtitles=[],audio=[];
        try{Array.prototype.forEach.call(v.textTracks||[],function(t){subtitles.push([t.label,t.language,t.kind].filter(Boolean).join(' · '));});}catch(ignored){}
        try{Array.prototype.forEach.call(v.audioTracks||[],function(t){audio.push([t.label,t.language].filter(Boolean).join(' · ')||'Vorhanden');});}catch(ignored){}
        if(!audio.length&&(v.mozHasAudio===true||v.webkitAudioDecodedByteCount>0))audio.push('Vorhanden');
        var info={width:positive(v.videoWidth),height:positive(v.videoHeight),duration:positive(v.duration),live:v.duration===Infinity,audio:audio.join(', '),subtitles:subtitles.join(', ')};
        if(!thumbnail&&v.readyState>=2&&v.videoWidth>0)try {
          var c=d.createElement('canvas');c.width=160;c.height=90;c.getContext('2d').drawImage(v,0,0,160,90);thumbnail=c.toDataURL('image/jpeg',0.7);
        }catch(ignored){}
        add(current,'video/unknown',thumbnail,title,info);
        v.querySelectorAll('source').forEach(function(s){
          // Other source qualities must never inherit dimensions/duration from the selected source.
          add(s.src,s.type||'video/unknown',thumbnail,title,s.src===current?info:{mime:s.type||'',quality:s.getAttribute('label')||s.getAttribute('data-quality')||''});
        });
      });
      (w.__mmVideos||[]).forEach(function(e){add(e.url,e.type,'','',{mime:e.type||'',size:positive(e.size)});});
      w.performance.getEntriesByType('resource').slice(-400).forEach(function(e){add(e.name,'');});
      if(depth<2)for(var i=0;i<Math.min(w.frames.length,8);i++)scan(w.frames[i],depth+1);
    }catch(ignored){}
  }
  scan(window,0);return JSON.stringify(rows);
})()
