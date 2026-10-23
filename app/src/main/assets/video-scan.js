(function () {
  'use strict';
  var rows=[],seen={},videoBudget=64,sourceBudget=256,documentBudget=24;
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
      if(documentBudget--<=0)return;
      var d=w.document,videos=d.querySelectorAll('video');
      for(var vi=0;vi<videos.length&&videoBudget>0;vi++,videoBudget--) {
        var v=videos[vi];
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
        var sources=v.querySelectorAll('source');
        for(var si=0;si<sources.length&&sourceBudget>0;si++,sourceBudget--){
          var s=sources[si];
          // Other source qualities must never inherit dimensions/duration from the selected source.
          add(s.src,s.type||'video/unknown',thumbnail,title,s.src===current?info:{mime:s.type||'',quality:s.getAttribute('label')||s.getAttribute('data-quality')||''});
        }
      }
      var observed=w.__mmVideos||[];for(var oi=0;oi<Math.min(observed.length,80);oi++){var e=observed[oi];add(e.url,e.type,'','',{mime:e.type||'',size:positive(e.size)});}
      var resources=w.performance.getEntriesByType('resource');for(var ri=Math.max(0,resources.length-400);ri<resources.length;ri++)add(resources[ri].name,'');
      if(depth<2)for(var i=0;i<Math.min(w.frames.length,8)&&documentBudget>0;i++)scan(w.frames[i],depth+1);
    }catch(ignored){}
  }
  scan(window,0);return JSON.stringify(rows);
})()
