const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const source=fs.readFileSync('app/src/main/assets/video-scan.js','utf8');
const video=(url,poster,title)=>({currentSrc:url,poster,title,getAttribute:()=>'',querySelectorAll:()=>[]});
const videos=[video('https://cdn.test/a.mp4','https://cdn.test/a.jpg','Movie A'),video('https://cdn.test/b.mp4','https://cdn.test/b.jpg','Movie B')];
const window={document:{title:'Page',querySelectorAll:()=>videos},__mmVideos:[{url:videos[0].currentSrc,type:'video/mp4'},{url:'https://cdn.test/manifest.m3u8',type:'application/vnd.apple.mpegurl'}],performance:{getEntriesByType:()=>[]},frames:[]};
let rows=JSON.parse(vm.runInNewContext(source,{window}));assert.equal(rows.length,3);assert.equal(rows[0].title,'Movie A');assert.equal(rows[0].thumbnail,'https://cdn.test/a.jpg');assert.equal(rows[1].title,'Movie B');assert.equal(rows[1].thumbnail,'https://cdn.test/b.jpg');assert.equal(rows[2].thumbnail,'');
videos[0].poster='';videos[0].readyState=2;videos[0].videoWidth=640;window.document.createElement=()=>({getContext:()=>({drawImage(){throw new Error('Cross-origin');}})});
rows=JSON.parse(vm.runInNewContext(source,{window}));assert.equal(rows[0].thumbnail,'');assert.equal(rows[1].thumbnail,'https://cdn.test/b.jpg');
window.document.createElement=()=>({getContext:()=>({drawImage(){}}),toDataURL:()=> 'data:image/jpeg;base64,abcd'});
rows=JSON.parse(vm.runInNewContext(source,{window}));assert.equal(rows[0].thumbnail,'data:image/jpeg;base64,abcd');
console.log('Video preview discovery: source mapping, duplicates, missing posters and cross-origin fallback passed');

Object.assign(videos[0],{videoWidth:1920,videoHeight:1080,duration:125.5,audioTracks:[{language:'de',label:'Deutsch'}],textTracks:[{language:'en',label:'English',kind:'subtitles'}]});
const alternative={src:'https://cdn.test/a-low.mp4',type:'video/mp4',getAttribute:k=>k==='data-quality'?'360p':''};videos[0].querySelectorAll=()=>[alternative];
rows=JSON.parse(vm.runInNewContext(source,{window}));let main=rows.find(r=>r.url==='https://cdn.test/a.mp4'),other=rows.find(r=>r.url===alternative.src);assert.equal(main.width,1920);assert.equal(main.height,1080);assert.equal(main.duration,125.5);assert.equal(main.audio,'Deutsch · de');assert.match(main.subtitles,/English/);assert.equal(other.width,undefined);assert.equal(other.duration,undefined);assert.equal(other.quality,'360p');
videos[0].duration=Infinity;rows=JSON.parse(vm.runInNewContext(source,{window}));main=rows.find(r=>r.url==='https://cdn.test/a.mp4');assert.equal(main.live,true);assert.equal(main.duration,undefined);
console.log('Video metadata discovery: true video dimensions, duration, live, tracks and alternative-source isolation passed');

const java=fs.readFileSync('app/src/main/java/de/steffen/musikmathe/VideoCatalog.java','utf8');
const hook=JSON.parse(java.match(/static final String HOOKS=("(?:\\.|[^"\\])*");/)[1]);
(async()=>{const response={url:'https://cdn.test/movie.mp4',status:206,headers:{get:k=>({'content-type':'video/mp4','content-length':'1024','content-range':'bytes 0-1023/2000000'})[k]||null}};const context={XMLHttpRequest:function(){},fetch:()=>Promise.resolve(response)};context.XMLHttpRequest.prototype.open=function(){};context.window=context;vm.runInNewContext(hook,context);await context.fetch();assert.equal(context.__mmVideos[0].size,2000000);response.headers.get=k=>({'content-type':'video/mp4','content-length':'1024'})[k]||null;await context.fetch();assert.equal(context.__mmVideos[1].size,0);console.log('Video response metadata: full Content-Range size and partial-response guard passed');})().catch(e=>{console.error(e);process.exitCode=1;});
