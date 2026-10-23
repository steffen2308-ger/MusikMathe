const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const script=fs.readFileSync('app/src/main/assets/youtube.js','utf8');
function run(path,options={}){
  const state={location:{href:'https://www.youtube.com/'+path},document:{title:'Test - YouTube',getElementById:()=>options.player?{getPlayerResponse:()=>options.player}:null},URL,JSON,performance:{now:()=>10,getEntriesByType:()=>[]},setInterval:()=>0,setTimeout:()=>0,XMLHttpRequest:function(){},addEventListener:()=>{}};
  state.XMLHttpRequest.prototype.open=function(){};state.window=state;Object.assign(state,options.globals||{});vm.createContext(state);
  return {data:JSON.parse(vm.runInContext(script,state)),state};
}
const response={videoDetails:{videoId:'aqz-KE-bpKQ',title:'Big Buck Bunny'},playabilityStatus:{status:'OK'},streamingData:{adaptiveFormats:[{itag:137,mimeType:'video/mp4; codecs="avc1.640028"',height:1080,url:'https://r1.googlevideo.com/videoplayback?id=A&itag=137'},{itag:140,mimeType:'audio/mp4; codecs="mp4a.40.2"',audioQuality:'AUDIO_QUALITY_MEDIUM',url:'https://r1.googlevideo.com/videoplayback?id=A&itag=140'}]}};
for(const path of ['watch?v=aqz-KE-bpKQ','shorts/aqz-KE-bpKQ','embed/aqz-KE-bpKQ','live/aqz-KE-bpKQ']){const result=run(path,{globals:{ytInitialPlayerResponse:response}}).data;assert.equal(result.videoId,'aqz-KE-bpKQ');assert.equal(result.formats.length,2);assert.equal(result.playerData,true);}
const player=run('watch?v=aqz-KE-bpKQ',{player:response}).data;assert.equal(player.title,'Big Buck Bunny');
const config=run('watch?v=aqz-KE-bpKQ',{globals:{ytplayer:{config:{args:{player_response:JSON.stringify(response)}}}}}).data;assert.equal(config.formats.length,2);
const blocked=run('watch?v=aqz-KE-bpKQ').data;assert.equal(blocked.videoId,'aqz-KE-bpKQ');assert.equal(blocked.playerData,false);assert.equal(blocked.formats.length,0);
const other=JSON.parse(JSON.stringify(response));other.videoDetails.videoId='jNQXAC9IVRw';
const stale=run('watch?v=jNQXAC9IVRw',{player:other,globals:{ytInitialPlayerResponse:response}}).data;assert.equal(stale.videoId,'jNQXAC9IVRw');assert.equal(stale.title,'Big Buck Bunny');assert.equal(stale.playerData,true);
const oldOnly=run('watch?v=jNQXAC9IVRw',{globals:{ytInitialPlayerResponse:response}}).data;assert.equal(oldOnly.playerData,false);assert.equal(oldOnly.formats.length,0);
console.log('YouTube extraction: 9 player/URL/navigation fixtures passed');

const rich=JSON.parse(JSON.stringify(response));Object.assign(rich.videoDetails,{lengthSeconds:'125',author:'Testkanal',shortDescription:'Beschreibung',viewCount:'321'});Object.assign(rich.streamingData.adaptiveFormats[0],{width:1920,fps:60,contentLength:'2000000',approxDurationMs:'125000'});Object.assign(rich.streamingData.adaptiveFormats[1],{audioChannels:2,audioSampleRate:'48000'});const metadata=run('watch?v=aqz-KE-bpKQ',{player:rich}).data;assert.equal(metadata.duration,'125');assert.equal(metadata.author,'Testkanal');assert.equal(metadata.formats[0].width,1920);assert.equal(metadata.formats[0].fps,60);assert.equal(metadata.formats[0].contentLength,'2000000');assert.equal(metadata.formats[1].audioChannels,2);console.log('YouTube metadata: duration, author, description, dimensions, frame rate, sizes and channels passed');
