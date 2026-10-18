(function () {
  'use strict';
  function youtube(u) { try { var h=new URL(u,location.href).hostname;return h==='youtube.com'||h.endsWith('.youtube.com')||h==='youtube-nocookie.com'||h.endsWith('.youtube-nocookie.com'); } catch(e){return false;} }
  function idFromUrl(u) { try { var p=new URL(u,location.href),m=p.pathname.match(/^\/(?:shorts|embed|live)\/([\w-]{11})(?:\/|$)/);return p.searchParams.get('v')||(m&&m[1])||''; }catch(e){return '';} }
  function install(){
    if(window.__mmYoutubeInstalled)return;window.__mmYoutubeInstalled=true;
    window.__mmYoutubeResponse=null;
    function take(p){try{if(typeof p==='string')p=JSON.parse(p);if(p&&p.videoDetails&&p.videoDetails.videoId)window.__mmYoutubeResponse=p;}catch(e){}}
    var originalFetch=window.fetch;
    if(originalFetch)window.fetch=function(){return originalFetch.apply(this,arguments).then(function(r){try{if(youtube(r.url)&&r.url.indexOf('/youtubei/v1/player')>=0)r.clone().json().then(take).catch(function(){});}catch(e){}return r;});};
    var open=XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open=function(){this.addEventListener('load',function(){try{if(youtube(this.responseURL)&&this.responseURL.indexOf('/youtubei/v1/player')>=0)take(this.responseType==='json'?this.response:this.responseText);}catch(e){}},{once:true});return open.apply(this,arguments);};
    function emit(){try{if(window.musikYouTube&&window.musikYouTube.postMessage)window.musikYouTube.postMessage(JSON.stringify(snapshot()));}catch(e){}}
    window.addEventListener('yt-navigate-finish',function(){setTimeout(emit,300);});
    window.addEventListener('yt-page-data-updated',emit);
    setInterval(emit,2000);
    window.__mmYoutubeEmit=emit;
  }
  function snapshot(){
    var current=idFromUrl(location.href),responses=[],player=null;
    if(window.__mmYoutubeCurrentId&&current&&window.__mmYoutubeCurrentId!==current)window.__mmYoutubeCutoff=performance.now();
    if(current)window.__mmYoutubeCurrentId=current;
    try{player=document.getElementById('movie_player');if(player&&player.getPlayerResponse)responses.push(player.getPlayerResponse());}catch(e){}
    try{responses.push(window.__mmYoutubeResponse,window.ytInitialPlayerResponse);if(window.ytplayer&&ytplayer.config&&ytplayer.config.args)responses.push(ytplayer.config.args.player_response);}catch(e){}
    var result=null;
    for(var i=0;i<responses.length;i++)try{var r=responses[i];if(typeof r==='string')r=JSON.parse(r);var id=r&&r.videoDetails&&r.videoDetails.videoId;if(id&&(!current||id===current)){result=r;if(r.streamingData)break;}}catch(e){}
    var details=result&&result.videoDetails||{},stream=result&&result.streamingData||{},state=result&&result.playabilityStatus||{};
    var out={page:location.href,videoId:current||details.videoId||'',title:details.title||document.title||'YouTube-Video',status:state.status||'',reason:state.reason||'',formats:[],observed:[],hls:stream.hlsManifestUrl||'',dash:stream.dashManifestUrl||'',playerData:!!result};
    function format(f){if(!f||out.formats.length>=80)return;out.formats.push({url:f.url||'',cipher:f.signatureCipher||f.cipher||'',itag:f.itag||0,mimeType:f.mimeType||'',height:f.height||0,qualityLabel:f.qualityLabel||'',bitrate:f.bitrate||0,audioQuality:f.audioQuality||'',audioSampleRate:f.audioSampleRate||'',audioTrack:f.audioTrack||{},drm:!!(f.drmFamilies&&f.drmFamilies.length)});}
    (stream.formats||[]).slice(0,15).forEach(format);var adaptive=stream.adaptiveFormats||[];adaptive.filter(function(f){return (f.mimeType||'').indexOf('video/')===0;}).slice(0,40).forEach(format);adaptive.filter(function(f){return (f.mimeType||'').indexOf('audio/')===0;}).slice(0,25).forEach(format);
    var names=[];try{performance.getEntriesByType('resource').slice(-600).forEach(function(e){if(e.startTime>=(window.__mmYoutubeCutoff||0))names.push(e.name);});}catch(e){}
    (window.__mmVideos||[]).forEach(function(e){names.push(e.url);});
    var seen={};names.forEach(function(u){try{var p=new URL(u);if((p.hostname==='googlevideo.com'||p.hostname.endsWith('.googlevideo.com'))&&p.pathname.indexOf('/videoplayback')>=0){var key=(p.searchParams.get('id')||'')+'|'+(p.searchParams.get('itag')||u);seen[key]=u;}}catch(e){}});
    Object.keys(seen).slice(-40).forEach(function(k){out.observed.push(seen[k]);});
    return out;
  }
  install();
  if(window.__mmYoutubeEmit)window.__mmYoutubeEmit();
  return JSON.stringify(snapshot());
})()
